// Per-game public signals for the app's rows: POST https://gamedeals.bam.pm/api/signals/v1 with {"ids": [...]}.
//
// For each game the Worker keeps one row in the D1 database (binding SIGNALS_DB), refreshed from IsThereAnyDeal at
// most once a day: the deepest discount in each of the last 104 weeks (from the US price history; discounts are
// almost always the same percentage in every region), the review score, player counts, how many ITAD users
// waitlist and own the game, and what the game is (its Steam tags, developer, release year and Steam app id, which
// the app uses to look up a description). Every app user's rows read from this one copy, so a game costs ITAD two calls a day no
// matter how many phones show it. That is what keeps the shared ITAD allowance from running out as installs grow.
//
// Privacy: the database holds public game data only, keyed by game id. The ids a phone asks about (which can be its
// waitlist) are used for this one response and never stored or logged; request logs stay off (wrangler.toml).
//
// The response never waits on ITAD. Games without a row yet come back in "pending" and are fetched in the background
// (at most MAX_REFRESH_PER_REQUEST per request); the app asks again later. Stale rows are served while they refresh.

import { ITAD } from "./share-routes.mjs";

export const SIGNALS_PATH = "/api/signals/v1";
export const MAX_IDS = 50;
export const WEEKS = 104;
export const REFRESH_AFTER_MS = 24 * 60 * 60 * 1000;
// A refresh that hasn't finished in this time (the Worker died, or ITAD failed) may be claimed again. A failed refresh
// keeps its claim until then, which spaces out retries while ITAD is down or limiting us.
export const CLAIM_TIMEOUT_MS = 10 * 60 * 1000;
export const MAX_REFRESH_PER_REQUEST = 10;
// ITAD's tags are Steam's player tags, most votes first. The app skips the vague ones and shows three, so keep a few.
export const MAX_TAGS = 10;
const MAX_TEXT = 60;
const HISTORY_COUNTRY = "US";
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const GAME_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** True when this Worker should answer [pathname] through [handleSignalsRequest]. */
export function isSignalsRoute(pathname) {
  return pathname === SIGNALS_PATH;
}

/**
 * @param {Request} request
 * @param {{ ITAD_API_KEY?: string, SIGNALS_DB?: D1Database }} env
 * @param {{ waitUntil?: (p: Promise<unknown>) => void }} [ctx]
 * @param {{ fetch?: typeof fetch, now?: () => number }} [deps] injected in tests
 */
export async function handleSignalsRequest(request, env, ctx, deps = {}) {
  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: { allow: "POST", ...NO_STORE } });
  }
  if (!env.SIGNALS_DB || !env.ITAD_API_KEY) return jsonResponse({ error: "not configured" }, 503);
  let ids;
  try {
    ids = parseIds(await request.json());
  } catch {
    ids = null;
  }
  if (ids === null) return jsonResponse({ error: `send {"ids": [...]} with 1 to ${MAX_IDS} ITAD game ids` }, 400);

  const now = (deps.now ?? Date.now)();
  const store = d1Store(env.SIGNALS_DB);
  let rows;
  try {
    rows = await store.read(ids);
  } catch {
    return jsonResponse({ error: "unavailable" }, 503);
  }

  const games = {};
  const pending = [];
  const missing = [];
  const stale = [];
  for (const id of ids) {
    const row = rows.get(id);
    const data = row?.data ? JSON.parse(row.data) : null;
    if (data === null) {
      pending.push(id);
      missing.push(id);
      continue;
    }
    // A game ITAD doesn't know is remembered as such for a day, and simply left out.
    if (!data.unknown) games[id] = data;
    if (now - row.updated_at >= REFRESH_AFTER_MS) stale.push(id);
  }

  // Missing games first: a stale row still has something to show.
  const wanted = [...missing, ...stale].slice(0, MAX_REFRESH_PER_REQUEST);
  if (wanted.length > 0) {
    const refresh = refreshGames(store, deps.fetch ?? fetch, env.ITAD_API_KEY, wanted, now).catch(() => {});
    if (ctx?.waitUntil) ctx.waitUntil(refresh); else await refresh;
  }
  return jsonResponse({ games, pending }, 200);
}

/** The request's ids, lower-cased and de-duplicated, or null unless it is {"ids": [1..MAX_IDS ITAD ids]}. */
export function parseIds(body) {
  if (!body || !Array.isArray(body.ids) || body.ids.length === 0 || body.ids.length > MAX_IDS) return null;
  const ids = [];
  for (const raw of body.ids) {
    if (typeof raw !== "string") return null;
    const id = raw.toLowerCase();
    if (!GAME_ID.test(id)) return null;
    if (!ids.includes(id)) ids.push(id);
  }
  return ids;
}

/** Claims each of [ids] for refresh (skipping ones another request is already refreshing), then refreshes them. */
async function refreshGames(store, fetchImpl, apiKey, ids, now) {
  const claimed = await store.claim(ids, now);
  await Promise.all(claimed.map(async (id) => {
    let data;
    try {
      data = await loadSignals(fetchImpl, apiKey, id, now);
    } catch {
      return; // Keep the claim: the game is retried once it times out.
    }
    await store.save(id, data, now);
  }));
}

/** One game's signals from ITAD (two calls), `{ unknown: true }` when ITAD doesn't know it. Throws on failures. */
export async function loadSignals(fetchImpl, apiKey, id, now) {
  const headers = { "ITAD-API-Key": apiKey, accept: "application/json" };
  const since = new Date(now - (WEEKS + 1) * WEEK_MS).toISOString();
  const [infoResponse, historyResponse] = await Promise.all([
    fetchImpl(`${ITAD}/games/info/v2?id=${id}`, { headers }),
    fetchImpl(`${ITAD}/games/history/v2?id=${id}&country=${HISTORY_COUNTRY}&since=${encodeURIComponent(since)}`, { headers }),
  ]);
  if (infoResponse.status === 400 || infoResponse.status === 404) return { unknown: true };
  if (!infoResponse.ok) throw new Error(`ITAD info ${infoResponse.status}`);
  if (!historyResponse.ok) throw new Error(`ITAD history ${historyResponse.status}`);
  const info = await infoResponse.json();
  const history = await historyResponse.json();
  return toSignals(info, Array.isArray(history) ? history : [], now);
}

/** The stored and served shape of one game's signals. */
export function toSignals(info, history, now) {
  const review = pickReview(info?.reviews);
  const players = info?.players;
  const stats = info?.stats;
  return {
    updated: now,
    weeklyMaxCut: weeklyMaxCuts(history, now),
    review,
    players: players && typeof players === "object"
      ? { recent: int(players.recent), day: int(players.day), week: int(players.week), peak: int(players.peak) }
      : null,
    waitlisted: int(stats?.waitlisted),
    collected: int(stats?.collected),
    rank: int(stats?.rank),
    tags: Array.isArray(info?.tags) ? info.tags.map(text).filter((tag) => tag !== null).slice(0, MAX_TAGS) : [],
    developer: Array.isArray(info?.developers) ? info.developers.map((d) => text(d?.name)).find((n) => n !== null) ?? null : null,
    releaseYear: releaseYear(info?.releaseDate),
    steamAppId: int(info?.appid),
  };
}

/** A short trimmed string, or null for anything else. */
function text(value) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.length <= MAX_TEXT ? trimmed : null;
}

/** The year of ITAD's "2020-09-17" release date, or null. */
function releaseYear(date) {
  const match = typeof date === "string" ? /^(\d{4})-\d{2}-\d{2}/.exec(date) : null;
  return match ? Number(match[1]) : null;
}

/** Steam's review when ITAD has one (most players read that one), else the first with a score. */
function pickReview(reviews) {
  if (!Array.isArray(reviews)) return null;
  const scored = reviews.filter((r) => r && typeof r.score === "number" && typeof r.source === "string");
  const review = scored.find((r) => r.source === "Steam") ?? scored[0];
  return review ? { score: review.score, count: int(review.count), source: review.source } : null;
}

function int(value) {
  return typeof value === "number" && Number.isFinite(value) ? Math.round(value) : null;
}

/**
 * The deepest discount (0–100) on offer at any store during each of the last [weeks] weeks, oldest first; the last
 * week ends at [now]. ITAD's history is a log of price changes per store, so each store's discount holds from one of
 * its entries until its next one (an entry without a deal means the store stopped selling the game).
 */
export function weeklyMaxCuts(history, now, weeks = WEEKS) {
  const cuts = new Array(weeks).fill(0);
  const start = now - weeks * WEEK_MS;
  const byShop = new Map();
  for (const entry of history) {
    const at = Date.parse(entry?.timestamp);
    const shop = entry?.shop?.id;
    if (!Number.isFinite(at) || shop === undefined) continue;
    const cut = typeof entry.deal?.cut === "number" ? Math.max(0, Math.min(100, Math.round(entry.deal.cut))) : 0;
    if (!byShop.has(shop)) byShop.set(shop, []);
    byShop.get(shop).push({ at, cut });
  }
  for (const changes of byShop.values()) {
    changes.sort((a, b) => a.at - b.at);
    changes.forEach((change, i) => {
      if (change.cut === 0) return;
      const from = Math.max(change.at, start);
      const until = Math.min(i + 1 < changes.length ? changes[i + 1].at : now, now);
      if (until <= from) return;
      const first = Math.floor((from - start) / WEEK_MS);
      // A sale ending exactly on a week boundary doesn't reach into the next week.
      const last = Math.min(weeks - 1, Math.ceil((until - start) / WEEK_MS) - 1);
      for (let w = Math.max(0, first); w <= last; w++) cuts[w] = Math.max(cuts[w], change.cut);
    });
  }
  return cuts;
}

// ---------------------------------------------------------------- storage

/** The D1 queries, kept together so the tests can run them against SQLite. */
export function d1Store(db) {
  return {
    /** id → { data, updated_at, claimed_at } for the rows that exist. */
    async read(ids) {
      const placeholders = ids.map(() => "?").join(", ");
      const { results } = await db.prepare(`SELECT id, data, updated_at, claimed_at FROM game_signals WHERE id IN (${placeholders})`)
        .bind(...ids).all();
      return new Map(results.map((row) => [row.id, row]));
    },
    /** Marks [ids] as being refreshed; returns the ones this request now owns (not already claimed by another). */
    async claim(ids, now) {
      const statements = ids.map((id) => db.prepare(
        `INSERT INTO game_signals (id, claimed_at) VALUES (?1, ?2)
         ON CONFLICT(id) DO UPDATE SET claimed_at = excluded.claimed_at
         WHERE game_signals.claimed_at IS NULL OR game_signals.claimed_at <= ?3`,
      ).bind(id, now, now - CLAIM_TIMEOUT_MS));
      const results = await db.batch(statements);
      return ids.filter((_, i) => (results[i]?.meta?.changes ?? 0) > 0);
    },
    /** Stores a refreshed row and releases its claim. */
    async save(id, data, now) {
      await db.prepare("UPDATE game_signals SET data = ?1, updated_at = ?2, claimed_at = NULL WHERE id = ?3")
        .bind(JSON.stringify(data), now, id).run();
    },
  };
}

// ---------------------------------------------------------------- responses

const NO_STORE = { "cache-control": "private, no-store" };

function jsonResponse(body, status) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "x-content-type-options": "nosniff", ...NO_STORE },
  });
}
