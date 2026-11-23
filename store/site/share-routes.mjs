// Shared game links: https://gamedeals.bam.pm/g/<ITAD game id>.
//
// With the app installed, Android App Links / iOS Universal Links open the game in the app and this code never runs.
// Otherwise it serves a small preview page: cover, best current price, the cheapest stores, and get-the-app buttons,
// plus the tags chat apps read for a link preview.
//
// Privacy: nothing about the visitor is stored. The visitor's country picks the price region for this one response;
// the edge cache keys on game id + country only. Request logs stay off (wrangler.toml).
//
// Path rules must match ShareLinks.kt (common/links). Price data comes from IsThereAnyDeal with the app's API key,
// held as the ITAD_API_KEY Worker secret, never in this file.

import { assetLinksJson, appleAppSiteAssociationJson } from "./app-links.mjs";

export const GAME_PATH = "/g/";
// "Get the app" for anything shared that isn't a game (the year card): Apple devices go to the App Store, the rest
// to Google Play. Decided from the User-Agent of this one request; nothing is kept.
export const APP_PATH = "/app";
const GAME_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ITAD = "https://api.isthereanydeal.com";
const PLAY_URL = "https://play.google.com/store/apps/details?id=pm.bam.gamedeals";
const APP_STORE_URL = "https://apps.apple.com/app/id6818586859";
const SITE = "https://gamedeals.bam.pm";
const FALLBACK_COUNTRY = "US";
const MAX_STORES = 5;
// How long a rendered page is reused for the same game and country. Prices move, but not by the minute, and this
// keeps a link that goes viral from spending the app's shared ITAD allowance.
const PAGE_TTL_SECONDS = 60 * 60;
const MISSING_TTL_SECONDS = 10 * 60;

/** True when this Worker should answer [pathname] through [handleShareRequest]. */
export function isShareRoute(pathname) {
  return (
    pathname === APP_PATH ||
    pathname === "/.well-known/assetlinks.json" ||
    pathname === "/.well-known/apple-app-site-association" ||
    pathname.startsWith(GAME_PATH)
  );
}

/**
 * @param {Request} request
 * @param {{ ITAD_API_KEY?: string }} env
 * @param {{ waitUntil?: (p: Promise<unknown>) => void }} [ctx]
 * @param {{ fetch?: typeof fetch, cache?: Cache | null }} [deps] injected in tests
 */
export async function handleShareRequest(request, env, ctx, deps = {}) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405, headers: { allow: "GET, HEAD" } });
  }
  const url = new URL(request.url);
  const head = request.method === "HEAD";

  if (url.pathname === "/.well-known/assetlinks.json") {
    const body = assetLinksJson();
    return body === null ? notFoundText() : json(body, head);
  }
  if (url.pathname === "/.well-known/apple-app-site-association") {
    return json(appleAppSiteAssociationJson(), head);
  }
  if (url.pathname === APP_PATH) {
    const agent = request.headers?.get?.("user-agent") ?? "";
    const store = /iPhone|iPad|iPod|Macintosh/.test(agent) ? APP_STORE_URL : PLAY_URL;
    return new Response(null, { status: 302, headers: { location: store, "cache-control": "private, no-store", vary: "user-agent" } });
  }

  const rawId = url.pathname.slice(GAME_PATH.length).replace(/\/$/, "");
  const gameId = rawId.toLowerCase();
  if (!GAME_ID.test(gameId)) return page(renderMissing(), 404, MISSING_TTL_SECONDS, head);
  // One game, one address: send other spellings (upper case, trailing slash, query) to the canonical link.
  if (rawId !== gameId || url.pathname !== GAME_PATH + gameId || url.search) {
    return Response.redirect(`${SITE}${GAME_PATH}${gameId}`, 301);
  }

  const country = countryOf(request);
  const cache = deps.cache === undefined ? globalThis.caches?.default ?? null : deps.cache;
  const cacheKey = new Request(`${SITE}/__share-cache${GAME_PATH}${gameId}?country=${country}`);
  if (cache) {
    const hit = await cache.match(cacheKey);
    if (hit) return forBrowser(hit, head);
  }

  const fetchImpl = deps.fetch ?? fetch;
  let html;
  let status;
  let ttl;
  try {
    const game = await loadGame(fetchImpl, env.ITAD_API_KEY, gameId, country);
    [html, status, ttl] = game === null
      ? [renderMissing(), 404, MISSING_TTL_SECONDS]
      : [renderGame(game, country), 200, PAGE_TTL_SECONDS];
  } catch {
    // ITAD is down or rejected us: say so plainly, keep the get-the-app buttons, and don't cache the failure.
    return page(renderUnavailable(gameId), 503, 0, head);
  }
  if (cache) {
    // The edge copy carries a public lifetime so the Cache API keeps it; visitors get the private headers below.
    const edgeCopy = new Response(html, { status, headers: { ...PAGE_HEADERS, "cache-control": `public, max-age=${ttl}` } });
    const store = cache.put(cacheKey, edgeCopy);
    if (ctx?.waitUntil) ctx.waitUntil(store); else await store;
  }
  return page(html, status, ttl, head);
}

function countryOf(request) {
  const country = request.cf?.country;
  return typeof country === "string" && /^[A-Z]{2}$/.test(country) && country !== "XX" && country !== "T1"
    ? country
    : FALLBACK_COUNTRY;
}

/** Game info + current prices from ITAD, or null when ITAD doesn't know the game. Throws on any other failure. */
export async function loadGame(fetchImpl, apiKey, gameId, country) {
  if (!apiKey) throw new Error("ITAD_API_KEY is not set");
  const headers = { "ITAD-API-Key": apiKey, accept: "application/json" };
  const [infoResponse, pricesResponse] = await Promise.all([
    fetchImpl(`${ITAD}/games/info/v2?id=${gameId}`, { headers }),
    fetchImpl(`${ITAD}/games/prices/v3?country=${country}`, {
      method: "POST",
      headers: { ...headers, "content-type": "application/json" },
      body: JSON.stringify([gameId]),
    }),
  ]);
  if (infoResponse.status === 400 || infoResponse.status === 404) return null;
  if (!infoResponse.ok) throw new Error(`ITAD info ${infoResponse.status}`);
  const info = await infoResponse.json();
  if (!info || typeof info.title !== "string") return null;
  // Prices are best-effort: a game page without prices still beats an error.
  let deals = [];
  let historyLow = null;
  if (pricesResponse.ok) {
    const prices = await pricesResponse.json();
    const entry = Array.isArray(prices) ? prices.find((p) => p && p.id === gameId) : null;
    if (entry) {
      deals = Array.isArray(entry.deals) ? entry.deals.filter(isUsableDeal) : [];
      historyLow = entry.historyLow?.all ?? null;
    }
  }
  deals.sort((a, b) => a.price.amount - b.price.amount);
  return {
    id: gameId,
    title: info.title,
    image: firstHttps(info.assets?.banner600, info.assets?.banner400, info.assets?.boxart, info.assets?.banner300),
    deals,
    historyLow,
  };
}

function isUsableDeal(d) {
  return d && d.shop && typeof d.shop.name === "string" && d.price && typeof d.price.amount === "number" &&
    typeof d.price.currency === "string" && typeof d.url === "string" && d.url.startsWith("https://");
}

function firstHttps(...urls) {
  return urls.find((u) => typeof u === "string" && u.startsWith("https://")) ?? null;
}

// ---------------------------------------------------------------- rendering

function money(price) {
  try {
    return new Intl.NumberFormat("en", { style: "currency", currency: price.currency }).format(price.amount);
  } catch {
    return `${price.amount.toFixed(2)} ${price.currency}`;
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function isLowestEver(deal) {
  return deal.flag === "N" || deal.flag === "H";
}

/** The one-line summary used in the page and in chat previews, e.g. "$4.99 at Steam (−75%) · Lowest price ever". */
export function summaryLine(game) {
  const best = game.deals[0];
  if (!best) return "No store is selling it right now. Get Game Deals to be told when it's back.";
  const parts = [`${money(best.price)} at ${best.shop.name}`];
  if (best.cut > 0) parts[0] += ` (−${best.cut}%)`;
  if (isLowestEver(best)) parts.push("Lowest price ever");
  return parts.join(" · ");
}

function renderGame(game, country) {
  const best = game.deals[0];
  const canonical = `${SITE}${GAME_PATH}${game.id}`;
  const summary = summaryLine(game);
  const rows = game.deals.slice(0, MAX_STORES).map((d) => `
      <li>
        <a class="store" href="${escapeHtml(d.url)}" rel="nofollow noopener">
          <span class="shop">${escapeHtml(d.shop.name)}</span>
          <span class="price">${escapeHtml(money(d.price))}${d.cut > 0 ? ` <span class="cut">−${d.cut}%</span>` : ""}</span>
        </a>
      </li>`).join("");
  const low = game.historyLow && typeof game.historyLow.amount === "number"
    ? `<p class="note">Lowest price ever: ${escapeHtml(money(game.historyLow))}</p>` : "";
  const hero = game.image
    ? `<img class="cover" src="${escapeHtml(game.image)}" alt="" width="600" height="282">` : "";
  return shell({
    title: game.title,
    description: summary,
    canonical,
    image: game.image,
    body: `
    ${hero}
    <h1>${escapeHtml(game.title)}</h1>
    ${best ? `<p class="best"><span class="big">${escapeHtml(money(best.price))}</span>${best.cut > 0 ? ` <span class="cut">−${best.cut}%</span>` : ""}${isLowestEver(best) ? ' <span class="flag">Lowest price ever</span>' : ""}</p>` : ""}
    <p class="summary">${escapeHtml(best ? `Best price right now, at ${best.shop.name}.` : summary)}</p>
    ${rows ? `<ul class="stores">${rows}</ul>` : ""}
    ${low}
    ${getTheApp("Follow this game, compare every store and get told when it drops.")}
    <p class="fine">Prices for ${escapeHtml(country)} from <a href="https://isthereanydeal.com/" rel="noopener">IsThereAnyDeal</a>. Prices can change at any time, so check the final price at the store. Game Deals is not affiliated with any store.</p>`,
  });
}

function renderMissing() {
  return shell({
    title: "Game not found",
    description: "This link doesn't point to a game Game Deals knows.",
    canonical: SITE,
    image: null,
    body: `
    <h1>We couldn't find that game</h1>
    <p class="summary">The link may be cut short, or the game may have been removed.</p>
    ${getTheApp("Compare prices across dozens of PC stores and grab free games before they expire.")}`,
  });
}

function renderUnavailable(gameId) {
  return shell({
    title: "Game Deals",
    description: "Prices for this game couldn't be loaded right now.",
    canonical: `${SITE}${GAME_PATH}${gameId}`,
    image: null,
    body: `
    <h1>Prices aren't loading right now</h1>
    <p class="summary">Try the link again in a few minutes, or open it in the app.</p>
    ${getTheApp("Compare prices across dozens of PC stores and grab free games before they expire.")}`,
  });
}

function getTheApp(pitch) {
  return `
    <section class="app">
      <h2>Get Game Deals</h2>
      <p>${escapeHtml(pitch)} Free, no ads.</p>
      <div class="buttons">
        <a class="button" href="${PLAY_URL}" rel="noopener">Google Play</a>
        <a class="button" href="${APP_STORE_URL}" rel="noopener">App Store</a>
      </div>
    </section>`;
}

function shell({ title, description, canonical, image, body }) {
  const t = escapeHtml(title);
  const d = escapeHtml(description);
  const img = image ? escapeHtml(image) : null;
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${t} · Game Deals</title>
<meta name="description" content="${d}">
<link rel="canonical" href="${escapeHtml(canonical)}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="Game Deals and Giveaways">
<meta property="og:title" content="${t}">
<meta property="og:description" content="${d}">
<meta property="og:url" content="${escapeHtml(canonical)}">
${img ? `<meta property="og:image" content="${img}">\n<meta name="twitter:card" content="summary_large_image">` : '<meta name="twitter:card" content="summary">'}
<meta name="robots" content="noindex">
<style>
:root { --bg: #f4f6f8; --card: #ffffff; --fg: #151b25; --muted: #586274; --line: #dbe0e7; --accent: #0b7a63; --on-accent: #ffffff; --flag: #c0283b; color-scheme: light; }
@media (prefers-color-scheme: dark) { :root { --bg: #0f131a; --card: #171c25; --fg: #e7ebf1; --muted: #9aa4b4; --line: #2a313d; --accent: #3cc6a3; --on-accent: #0f131a; --flag: #f06b7c; color-scheme: dark; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
main { max-width: 34rem; margin: 0 auto; padding: 24px 16px 40px; display: grid; gap: 14px; }
.cover { width: 100%; height: auto; border-radius: 10px; background: var(--line); }
h1 { margin: 0; font-size: 1.6rem; line-height: 1.2; }
h2 { margin: 0; font-size: 1.1rem; }
p { margin: 0; }
.best { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; }
.big { font-size: 2rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.cut { font-weight: 700; color: var(--accent); }
.flag { font-size: 0.8rem; font-weight: 700; color: var(--flag); border: 1px solid currentColor; border-radius: 999px; padding: 2px 8px; }
.summary, .note, .fine { color: var(--muted); }
.fine { font-size: 0.8rem; }
.stores { list-style: none; margin: 0; padding: 0; background: var(--card); border: 1px solid var(--line); border-radius: 10px; }
.stores li + li { border-top: 1px solid var(--line); }
.store { display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; color: inherit; text-decoration: none; }
.store:hover .shop { text-decoration: underline; }
.price { font-variant-numeric: tabular-nums; white-space: nowrap; }
.app { background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 16px; display: grid; gap: 10px; }
.buttons { display: flex; flex-wrap: wrap; gap: 8px; }
.button { flex: 1 1 10rem; text-align: center; background: var(--accent); color: var(--on-accent); font-weight: 600; text-decoration: none; padding: 12px 16px; border-radius: 8px; }
a { color: var(--accent); }
a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
</style>
</head>
<body>
<main>${body}
</main>
</body>
</html>`;
}

// ---------------------------------------------------------------- responses

const PAGE_HEADERS = {
  "content-type": "text/html; charset=utf-8",
  "x-content-type-options": "nosniff",
  "referrer-policy": "no-referrer",
  "content-security-policy":
    "default-src 'none'; img-src https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
};

// Browsers may reuse a page briefly. Shared caches may not, because the prices depend on the visitor's country.
const BROWSER_MAX_AGE = 10 * 60;

function page(html, status, ttlSeconds, head) {
  return new Response(head ? null : html, {
    status,
    headers: {
      ...PAGE_HEADERS,
      "cache-control": ttlSeconds > 0 ? `private, max-age=${Math.min(ttlSeconds, BROWSER_MAX_AGE)}` : "no-store",
    },
  });
}

/** An edge-cache hit, re-labelled with the visitor-facing cache headers. */
function forBrowser(hit, head) {
  const headers = new Headers(hit.headers);
  headers.set("cache-control", `private, max-age=${BROWSER_MAX_AGE}`);
  return new Response(head ? null : hit.body, { status: hit.status, headers });
}

function json(body, head) {
  return new Response(head ? null : body, {
    headers: {
      "content-type": "application/json",
      "cache-control": "public, max-age=3600",
      "x-content-type-options": "nosniff",
    },
  });
}

function notFoundText() {
  return new Response("Not found", { status: 404, headers: { "content-type": "text/plain; charset=utf-8" } });
}
