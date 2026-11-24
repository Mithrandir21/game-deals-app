// Shared wishlist links: https://gamedeals.bam.pm/w/<ids>.
//
// The link itself is the list: up to ten ITAD game ids, each written as its 16 bytes in URL-safe base64 (22
// characters, no padding) and run together, in the order the sharer picked them. With the app installed, Android App
// Links / iOS Universal Links open it in the app, which shows the games you both want. Otherwise this code serves a
// preview page: each game with its best current price and a buy button, plus get-the-app buttons.
//
// Privacy: the list is never stored. The rendered page is never put in the edge cache and tells browsers not to keep
// it, because the set of games is the sharer's choice. Only public per-game data is cached, each game on its own key:
// its title and art (a day) and its prices for one country (an hour). Request logs stay off (wrangler.toml).
//
// Path rules must match ShareLinks.kt (common/links); the tests use the same golden encodings.

import {
  GAME_PATH, ITAD, ITAD_TIMEOUT_MS, SITE, PAGE_HEADERS, countryOf, escapeHtml, firstHttps, getTheApp, isLowestEver, isUsableDeal,
  money, shell,
} from "./share-routes.mjs";

export const WISHLIST_PATH = "/w/";
export const MAX_GAMES = 10;
const ENCODED_ID_LENGTH = 22;
const BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
const HEX = "0123456789abcdef";
const INFO_TTL_SECONDS = 24 * 60 * 60;
const UNKNOWN_TTL_SECONDS = 10 * 60;
const PRICE_TTL_SECONDS = 60 * 60;

/** True when this Worker should answer [pathname] through [handleWishlistRequest]. */
export function isWishlistRoute(pathname) {
  return pathname.startsWith(WISHLIST_PATH);
}

/** A lower-case UUID's 16 bytes as 22 URL-safe base64 characters. */
export function encodeId(uuid) {
  let out = "";
  let buffer = 0;
  let bits = 0;
  for (const char of uuid.replaceAll("-", "")) {
    buffer = (buffer << 4) | parseInt(char, 16);
    bits += 4;
    if (bits >= 6) {
      bits -= 6;
      out += BASE64URL[(buffer >> bits) & 0x3f];
      buffer &= (1 << bits) - 1;
    }
  }
  // 128 bits leave 2 over: pad them with zeros into a last character.
  if (bits > 0) out += BASE64URL[(buffer << (6 - bits)) & 0x3f];
  return out;
}

/**
 * 22 base64 characters back to a lower-case UUID, or null for a character outside the URL-safe alphabet or a last
 * character with stray low bits (so every id has exactly one spelling).
 */
export function decodeId(token) {
  if (token.length !== ENCODED_ID_LENGTH) return null;
  let hex = "";
  let buffer = 0;
  let bits = 0;
  for (const char of token) {
    const value = BASE64URL.indexOf(char);
    if (value < 0) return null;
    buffer = (buffer << 6) | value;
    bits += 6;
    while (bits >= 4 && hex.length < 32) {
      bits -= 4;
      hex += HEX[(buffer >> bits) & 0xf];
    }
    buffer &= (1 << bits) - 1;
  }
  if (buffer !== 0) return null;
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** The game ids in a wishlist link's path segment, repeats dropped, or null unless it is 1 to 10 whole ids. */
export function decodeWishlist(segment) {
  if (segment.length === 0 || segment.length % ENCODED_ID_LENGTH !== 0) return null;
  if (segment.length / ENCODED_ID_LENGTH > MAX_GAMES) return null;
  const ids = [];
  for (let i = 0; i < segment.length; i += ENCODED_ID_LENGTH) {
    const id = decodeId(segment.slice(i, i + ENCODED_ID_LENGTH));
    if (id === null) return null;
    if (!ids.includes(id)) ids.push(id);
  }
  return ids;
}

/** The canonical path for [ids]: the same encoding ShareLinks.wishlist builds in the app. */
export function wishlistPath(ids) {
  return WISHLIST_PATH + ids.map(encodeId).join("");
}

/**
 * @param {Request} request
 * @param {{ ITAD_API_KEY?: string }} env
 * @param {{ waitUntil?: (p: Promise<unknown>) => void }} [ctx]
 * @param {{ fetch?: typeof fetch, cache?: Cache | null, timeoutMs?: number }} [deps] injected in tests
 */
export async function handleWishlistRequest(request, env, ctx, deps = {}) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405, headers: { allow: "GET, HEAD" } });
  }
  const url = new URL(request.url);
  const head = request.method === "HEAD";
  const segment = url.pathname.slice(WISHLIST_PATH.length).replace(/\/$/, "");
  const ids = decodeWishlist(segment);
  if (ids === null) return listPage(renderBroken(), 404, head);
  // One list, one address: send other spellings (repeats, trailing slash, query) to the canonical link.
  const canonical = wishlistPath(ids);
  if (url.pathname !== canonical || url.search) return Response.redirect(`${SITE}${canonical}`, 301);

  const country = countryOf(request);
  const cache = deps.cache === undefined ? globalThis.caches?.default ?? null : deps.cache;
  const fetchImpl = deps.fetch ?? fetch;
  let list;
  try {
    const signal = AbortSignal.timeout(deps.timeoutMs ?? ITAD_TIMEOUT_MS);
    list = await loadWishlist({ fetch: fetchImpl, cache, ctx, apiKey: env.ITAD_API_KEY, signal }, ids, country);
  } catch {
    return listPage(renderUnavailable(), 503, head);
  }
  if (list.games.length === 0) return listPage(renderBroken(), 404, head);
  return listPage(renderList(list, canonical, country), 200, head);
}

/**
 * Each game's title, art and prices, in the link's order. Games ITAD doesn't know are left out; games whose info
 * call failed (or ran past `io.signal`, the page's 5-second limit) are counted in `failed`. Throws when there's no API
 * key or when every game failed.
 */
export async function loadWishlist(io, ids, country) {
  if (!io.apiKey) throw new Error("ITAD_API_KEY is not set");
  const headers = { "ITAD-API-Key": io.apiKey, accept: "application/json" };
  const [infos, prices] = await Promise.all([
    Promise.all(ids.map((id) => loadInfo(io, headers, id).catch(() => FAILED))),
    loadPrices(io, headers, ids, country).catch(() => new Map()),
  ]);
  const games = [];
  let failed = 0;
  ids.forEach((id, i) => {
    const info = infos[i];
    if (info === FAILED) failed += 1;
    else if (info !== null) games.push({ id, ...info, ...(prices.get(id) ?? { deals: [], historyLow: null }) });
  });
  if (games.length === 0 && failed > 0) throw new Error("every game failed to load");
  return { games, failed };
}

const FAILED = Symbol("failed");

/** { title, thumb, image } for one game, or null when ITAD doesn't know it. Cached per game id. */
async function loadInfo(io, headers, id) {
  const key = cacheKey(`info/${id}`);
  const hit = io.cache ? await io.cache.match(key) : undefined;
  if (hit) return (await hit.json()).info;
  const response = await io.fetch(`${ITAD}/games/info/v2?id=${id}`, { headers, signal: io.signal });
  let info;
  if (response.status === 400 || response.status === 404) {
    info = null;
  } else if (!response.ok) {
    throw new Error(`ITAD info ${response.status}`);
  } else {
    const body = await response.json();
    info = body && typeof body.title === "string"
      ? {
        title: body.title,
        thumb: firstHttps(body.assets?.banner300, body.assets?.banner400, body.assets?.banner145, body.assets?.boxart),
        image: firstHttps(body.assets?.banner600, body.assets?.banner400, body.assets?.boxart, body.assets?.banner300),
      }
      : null;
  }
  await remember(io, key, { info }, info === null ? UNKNOWN_TTL_SECONDS : INFO_TTL_SECONDS);
  return info;
}

/** id → { deals (cheapest first), historyLow } for one country, from the cache or one batched ITAD call. */
async function loadPrices(io, headers, ids, country) {
  const result = new Map();
  const missing = [];
  for (const id of ids) {
    const hit = io.cache ? await io.cache.match(cacheKey(`price/${id}`, country)) : undefined;
    if (hit) result.set(id, await hit.json());
    else missing.push(id);
  }
  if (missing.length === 0) return result;
  const response = await io.fetch(`${ITAD}/games/prices/v3?country=${country}`, {
    method: "POST",
    headers: { ...headers, "content-type": "application/json" },
    body: JSON.stringify(missing),
    signal: io.signal,
  });
  if (!response.ok) return result;
  const body = await response.json();
  const entries = Array.isArray(body) ? body : [];
  for (const id of missing) {
    const entry = entries.find((e) => e && e.id === id);
    const deals = Array.isArray(entry?.deals) ? entry.deals.filter(isUsableDeal) : [];
    deals.sort((a, b) => a.price.amount - b.price.amount);
    const price = { deals, historyLow: entry?.historyLow?.all ?? null };
    result.set(id, price);
    await remember(io, cacheKey(`price/${id}`, country), price, PRICE_TTL_SECONDS);
  }
  return result;
}

function cacheKey(path, country) {
  return new Request(`${SITE}/__share-cache/${path}${country ? `?country=${country}` : ""}`);
}

async function remember(io, key, value, ttlSeconds) {
  if (!io.cache) return;
  const copy = new Response(JSON.stringify(value), {
    headers: { "content-type": "application/json", "cache-control": `public, max-age=${ttlSeconds}` },
  });
  const store = io.cache.put(key, copy);
  if (io.ctx?.waitUntil) io.ctx.waitUntil(store); else await store;
}

// ---------------------------------------------------------------- rendering

/** "Hades, Celeste, Hollow Knight and 4 more" — the chat-preview line. */
export function titlesLine(games) {
  const shown = games.slice(0, 3).map((g) => g.title);
  const more = games.length - shown.length;
  if (more > 0) return `${shown.join(", ")} and ${more} more`;
  if (shown.length <= 1) return shown.join("");
  return `${shown.slice(0, -1).join(", ")} and ${shown.at(-1)}`;
}

function dealLine(game) {
  const best = game.deals[0];
  if (!best) return "Not on sale anywhere right now";
  let line = `${money(best.price)}${best.cut > 0 ? ` (−${best.cut}%)` : ""} at ${best.shop.name}`;
  if (isLowestEver(best)) line += " · Lowest price ever";
  return line;
}

function renderList({ games, failed }, canonical, country) {
  const count = games.length === 1 ? "1 game" : `${games.length} games`;
  const rows = games.map((game) => {
    const best = game.deals[0];
    const thumb = game.thumb
      ? `<img class="thumb" src="${escapeHtml(game.thumb)}" alt="" width="96" height="45" loading="lazy">`
      : '<span class="thumb"></span>';
    const buy = best
      ? `<a class="buy" href="${escapeHtml(best.url)}" rel="nofollow noopener" aria-label="Buy ${escapeHtml(game.title)} at ${escapeHtml(best.shop.name)}">Buy</a>`
      : "<span></span>";
    return `
      <li class="game">
        ${thumb}
        <div>
          <a class="title" href="${GAME_PATH}${game.id}">${escapeHtml(game.title)}</a>
          <p class="deal">${escapeHtml(dealLine(game))}</p>
        </div>
        ${buy}
      </li>`;
  }).join("");
  const missingNote = failed > 0
    ? `<p class="note">${failed === 1 ? "1 game" : `${failed} games`} couldn't be loaded right now. Try again in a few minutes.</p>`
    : "";
  return shell({
    title: `A wishlist with ${count}`,
    description: `${titlesLine(games)}, with live prices.`,
    canonical: `${SITE}${canonical}`,
    image: games.find((g) => g.image)?.image ?? null,
    body: `
    <h1>A wishlist with ${escapeHtml(count)}</h1>
    <p class="summary">Someone shared the games they'd love to get, with the best price for each right now. Many stores let you buy a game as a gift at checkout.</p>
    <ul class="games">${rows}</ul>
    ${missingNote}
    ${getTheApp("Open this list in the app to see which games you both want, and get told when they drop.")}
    <p class="fine">Prices for ${escapeHtml(country)} from <a href="https://isthereanydeal.com/" rel="noopener">IsThereAnyDeal</a>. Prices can change at any time, so check the final price at the store. Game Deals is not affiliated with any store.</p>`,
  });
}

function renderBroken() {
  return shell({
    title: "Wishlist not found",
    description: "This link doesn't point to a wishlist Game Deals can read.",
    canonical: SITE,
    image: null,
    body: `
    <h1>We couldn't read that wishlist</h1>
    <p class="summary">The link may be cut short. Ask for it again, or copy the whole link.</p>
    ${getTheApp("Compare prices across dozens of PC stores and grab free games before they expire.")}`,
  });
}

function renderUnavailable() {
  return shell({
    title: "Game Deals",
    description: "Prices for this wishlist couldn't be loaded right now.",
    canonical: SITE,
    image: null,
    body: `
    <h1>Prices aren't loading right now</h1>
    <p class="summary">Try the link again in a few minutes, or open it in the app.</p>
    ${getTheApp("Compare prices across dozens of PC stores and grab free games before they expire.")}`,
  });
}

/** Every wishlist response: never kept by the edge or the browser, since the list is the sharer's choice. */
function listPage(html, status, head) {
  return new Response(head ? null : html, { status, headers: { ...PAGE_HEADERS, "cache-control": "private, no-store" } });
}
