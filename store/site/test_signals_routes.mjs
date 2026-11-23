import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { test } from "node:test";
import {
  CLAIM_TIMEOUT_MS, MAX_IDS, MAX_REFRESH_PER_REQUEST, REFRESH_AFTER_MS, WEEKS, handleSignalsRequest, isSignalsRoute,
  parseIds, toSignals, weeklyMaxCuts,
} from "./signals-routes.mjs";

const DAY = 24 * 60 * 60 * 1000;
const WEEK = 7 * DAY;
const NOW = Date.parse("2026-11-23T12:00:00Z");
const ID = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5";
const ID2 = "018d937f-1ae9-734c-ba47-bd357cf07edd";
const many = (count) => Array.from({ length: count }, (_, n) => `018d937f-0000-7000-8000-${String(n).padStart(12, "0")}`);

/** A D1Database stand-in over Node's SQLite, with the real schema from migrations/, so the SQL itself is tested. */
function d1() {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec(readFileSync(new URL("./migrations/0001_game_signals.sql", import.meta.url), "utf8"));
  const statement = (sql, params = []) => ({
    bind: (...values) => statement(sql, values),
    all: async () => ({ results: sqlite.prepare(sql).all(...params) }),
    run: async () => ({ meta: { changes: Number(sqlite.prepare(sql).run(...params).changes) } }),
  });
  return {
    sqlite,
    prepare: (sql) => statement(sql),
    batch: async (statements) => Promise.all(statements.map((s) => s.run())),
    row: (id) => sqlite.prepare("SELECT * FROM game_signals WHERE id = ?").get(id),
  };
}

const info = (id) => ({
  id,
  title: "Hades",
  reviews: [
    { score: 88, source: "Metascore", count: 50 },
    { score: 98, source: "Steam", count: 250000, url: "https://store.steampowered.com/app/1145360" },
  ],
  stats: { rank: 12, waitlisted: 45000, collected: 120000 },
  players: { recent: 4000, day: 9000, week: 15000, peak: 37000 },
});
const change = (daysAgo, shop, cut) =>
  ({ timestamp: new Date(NOW - daysAgo * DAY).toISOString(), shop: { id: shop, name: `Shop ${shop}` }, deal: cut === null ? null : { price: { amount: 1 }, cut } });

function fakeItad({ infoStatus = () => 200, historyStatus = 200, history = [] } = {}) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    calls.push({ url, init });
    const id = new URL(url).searchParams.get("id");
    if (url.includes("/games/info/v2")) {
      const status = infoStatus(id);
      return new Response(JSON.stringify(status === 200 ? info(id) : { status }), { status });
    }
    if (url.includes("/games/history/v2")) return new Response(JSON.stringify(history), { status: historyStatus });
    throw new Error(`unexpected ${url}`);
  };
  return { fetch, calls };
}

const post = (body, method = "POST") => ({
  method,
  url: "https://gamedeals.bam.pm/api/signals/v1",
  json: async () => (typeof body === "string" ? JSON.parse(body) : body),
});

async function ask(db, ids, { itad = fakeItad(), now = NOW, env = {} } = {}) {
  const response = await handleSignalsRequest(post({ ids }), { ITAD_API_KEY: "test-key", SIGNALS_DB: db, ...env }, undefined, { fetch: itad.fetch, now: () => now });
  return { response, body: await response.json(), itad };
}

// ---------------------------------------------------------------- the weekly discount series

test("each week holds the deepest discount any store had during it, oldest week first", () => {
  const history = [
    change(30, 1, 50), change(23, 1, 0), // Steam: −50% from 30 to 23 days ago
    change(26, 2, 75), change(25, 2, 0), // another store: −75% for one day inside that sale
    change(3, 1, 20), // −20% since 3 days ago, still on
  ];
  const cuts = weeklyMaxCuts(history, NOW);
  assert.equal(cuts.length, WEEKS);
  // Weeks end 0, 7, 14, 21, 28 and 35 days ago: the −50% sale spans the weeks ending 21 and 28 days ago.
  assert.deepEqual(cuts.slice(-6), [0, 50, 75, 0, 0, 20]);
  assert.ok(cuts.slice(0, -6).every((c) => c === 0));
});

test("a sale that started before the window counts from the window's start; one ending on a boundary stays in its week", () => {
  const history = [change(WEEKS * 7 + 20, 1, 40), change(WEEKS * 7 - 7, 1, 0)];
  const cuts = weeklyMaxCuts(history, NOW);
  assert.deepEqual(cuts.slice(0, 2), [40, 0]);
});

test("a store that stops selling ends its discount; odd entries are ignored", () => {
  const history = [change(10, 1, 60), change(8, 1, null), { timestamp: "nope", shop: { id: 3 }, deal: { cut: 90 } }, { shop: { id: 4 } }];
  const cuts = weeklyMaxCuts(history, NOW);
  assert.deepEqual(cuts.slice(-2), [60, 0]);
});

test("signals carry Steam's review, players and ITAD counts, with missing parts as null", () => {
  const signals = toSignals(info(ID), [], NOW);
  assert.equal(signals.updated, NOW);
  assert.deepEqual(signals.review, { score: 98, count: 250000, source: "Steam" });
  assert.deepEqual(signals.players, { recent: 4000, day: 9000, week: 15000, peak: 37000 });
  assert.equal(signals.waitlisted, 45000);
  assert.equal(signals.collected, 120000);
  assert.equal(signals.rank, 12);
  const bare = toSignals({ reviews: [{ source: "Metascore", score: 70 }] }, [], NOW);
  assert.deepEqual(bare.review, { score: 70, count: null, source: "Metascore" });
  assert.equal(bare.players, null);
  assert.equal(bare.waitlisted, null);
  assert.equal(toSignals({}, [], NOW).review, null);
});

// ---------------------------------------------------------------- the request

test("only POST is accepted, and only a list of 1 to 50 ITAD ids", async () => {
  assert.ok(isSignalsRoute("/api/signals/v1"));
  assert.ok(!isSignalsRoute("/api/signals/v1/x"));
  const get = await handleSignalsRequest(post({}, "GET"), { ITAD_API_KEY: "k", SIGNALS_DB: d1() }, undefined, {});
  assert.equal(get.status, 405);
  for (const body of [{}, { ids: [] }, { ids: "x" }, { ids: [ID, 7] }, { ids: ["half-life"] }, { ids: many(MAX_IDS + 1) }]) {
    const response = await handleSignalsRequest(post(body), { ITAD_API_KEY: "k", SIGNALS_DB: d1() }, undefined, {});
    assert.equal(response.status, 400, JSON.stringify(body));
  }
  const broken = await handleSignalsRequest({ method: "POST", json: async () => { throw new SyntaxError("bad"); } }, { ITAD_API_KEY: "k", SIGNALS_DB: d1() }, undefined, {});
  assert.equal(broken.status, 400);
  assert.deepEqual(parseIds({ ids: [ID.toUpperCase(), ID, ID2] }), [ID, ID2]);
});

test("without the database or the ITAD key the endpoint says so instead of failing", async () => {
  for (const env of [{ ITAD_API_KEY: "k" }, { SIGNALS_DB: d1() }]) {
    const response = await handleSignalsRequest(post({ ids: [ID] }), env, undefined, {});
    assert.equal(response.status, 503);
  }
});

test("a first ask answers at once with the games pending, and fetches them in the background", async () => {
  const db = d1();
  const first = await ask(db, [ID, ID2]);
  assert.equal(first.response.status, 200);
  assert.equal(first.response.headers.get("cache-control"), "private, no-store");
  assert.deepEqual(first.body, { games: {}, pending: [ID, ID2] });
  // Two ITAD calls per game: info and the US history for the last 105 weeks, with the key in a header.
  assert.equal(first.itad.calls.length, 4);
  const historyCall = first.itad.calls.find((c) => c.url.includes("history"));
  assert.match(historyCall.url, /country=US/);
  assert.match(historyCall.url, /since=2024-11-1/);
  for (const call of first.itad.calls) {
    assert.equal(call.init.headers["ITAD-API-Key"], "test-key");
    assert.doesNotMatch(call.url, /test-key/);
  }

  const second = await ask(db, [ID, ID2], { now: NOW + 60_000 });
  assert.deepEqual(second.body.pending, []);
  assert.deepEqual(Object.keys(second.body.games).sort(), [ID, ID2]);
  assert.equal(second.body.games[ID].review.score, 98);
  assert.equal(second.itad.calls.length, 0, "fresh rows cost ITAD nothing");
});

test("the database holds public game data keyed by game id, and nothing else", async () => {
  const db = d1();
  await ask(db, [ID]);
  const columns = db.sqlite.prepare("PRAGMA table_info(game_signals)").all().map((c) => c.name);
  assert.deepEqual(columns, ["id", "data", "updated_at", "claimed_at"]);
  const row = db.row(ID);
  assert.equal(row.updated_at, NOW);
  assert.equal(row.claimed_at, null);
  assert.deepEqual(Object.keys(JSON.parse(row.data)).sort(), ["collected", "players", "rank", "review", "updated", "waitlisted", "weeklyMaxCut"]);
});

test("rows older than a day are served while they refresh", async () => {
  const db = d1();
  await ask(db, [ID]);
  const later = NOW + REFRESH_AFTER_MS + 1000;
  const stale = await ask(db, [ID], { now: later });
  assert.equal(stale.body.games[ID].updated, NOW, "the old row is served");
  assert.equal(stale.itad.calls.length, 2, "and refreshed");
  assert.equal(db.row(ID).updated_at, later);
});

test("a refresh another request is running isn't started again until it times out", async () => {
  const db = d1();
  db.sqlite.prepare("INSERT INTO game_signals (id, claimed_at) VALUES (?, ?)").run(ID, NOW - 1000);
  const busy = await ask(db, [ID]);
  assert.deepEqual(busy.body.pending, [ID]);
  assert.equal(busy.itad.calls.length, 0);
  const timedOut = await ask(db, [ID], { now: NOW - 1000 + CLAIM_TIMEOUT_MS });
  assert.equal(timedOut.itad.calls.length, 2);
});

test("a failed refresh keeps its claim, so ITAD isn't asked again until the claim times out", async () => {
  const db = d1();
  const failing = fakeItad({ historyStatus: 429 });
  await ask(db, [ID], { itad: failing });
  assert.equal(db.row(ID).data, null);
  assert.equal(db.row(ID).claimed_at, NOW);
  const retry = await ask(db, [ID], { now: NOW + 60_000 });
  assert.equal(retry.itad.calls.length, 0);
  assert.deepEqual(retry.body.pending, [ID]);
});

test("a game ITAD doesn't know is left out, not pending, and not asked about again for a day", async () => {
  const db = d1();
  await ask(db, [ID], { itad: fakeItad({ infoStatus: () => 404 }) });
  const next = await ask(db, [ID], { now: NOW + 60_000 });
  assert.deepEqual(next.body, { games: {}, pending: [] });
  assert.equal(next.itad.calls.length, 0);
});

test("one request refreshes at most ten games, missing ones before stale ones", async () => {
  const db = d1();
  const ids = many(MAX_IDS);
  const first = await ask(db, ids);
  assert.equal(first.body.pending.length, MAX_IDS);
  assert.equal(first.itad.calls.length, MAX_REFRESH_PER_REQUEST * 2);
  const fetched = new Set(first.itad.calls.map((c) => new URL(c.url).searchParams.get("id")));
  assert.deepEqual([...fetched].sort(), ids.slice(0, MAX_REFRESH_PER_REQUEST).sort());

  // A day later the first ten are stale and forty are still missing: the missing ones go first.
  const later = await ask(db, ids, { now: NOW + REFRESH_AFTER_MS + CLAIM_TIMEOUT_MS });
  const next = new Set(later.itad.calls.map((c) => new URL(c.url).searchParams.get("id")));
  assert.deepEqual([...next].sort(), ids.slice(MAX_REFRESH_PER_REQUEST, 2 * MAX_REFRESH_PER_REQUEST).sort());
});

test("a database failure gets a clear error, not a crash", async () => {
  const db = { prepare: () => ({ bind: () => ({ all: async () => { throw new Error("D1 down"); } }) }) };
  const response = await handleSignalsRequest(post({ ids: [ID] }), { ITAD_API_KEY: "k", SIGNALS_DB: db }, undefined, {});
  assert.equal(response.status, 503);
});
