import assert from "node:assert/strict";
import { test } from "node:test";
import {
  decodeId, decodeWishlist, encodeId, handleWishlistRequest, isWishlistRoute, titlesLine, wishlistPath,
} from "./wishlist-routes.mjs";

// Golden values shared with ShareLinksTest.kt, checked against Python's base64.urlsafe_b64encode(uuid.bytes).
const ID = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5";
const ID2 = "018d937f-1ae9-734c-ba47-bd357cf07edd";
const ENC = "AY2TfwfCcVW24vX5s8W3pQ";
const ENC2 = "AY2Tfxrpc0y6R701fPB-3Q";
const many = (count) => Array.from({ length: count }, (_, n) => `018d937f-0000-7000-8000-${String(n).padStart(12, "0")}`);

const env = { ITAD_API_KEY: "test-key" };
const request = (path, { method = "GET", country } = {}) =>
  ({ method, url: `https://gamedeals.bam.pm${path}`, cf: country ? { country } : undefined });

const infos = {
  [ID]: { id: ID, title: "Hades <II>", assets: { banner300: "https://assets.example/hades300.jpg", banner600: "https://assets.example/hades600.jpg" } },
  [ID2]: { id: ID2, title: "Hollow Knight", assets: { boxart: "http://insecure.example/box.jpg" } },
};
const deal = (shop, amount, cut, flag) => ({
  shop: { id: shop.length, name: shop },
  price: { amount, currency: "USD" },
  regular: { amount: 29.99, currency: "USD" },
  cut,
  url: `https://itad.link/${shop.toLowerCase()}`,
  flag,
});
const prices = [
  { id: ID, historyLow: { all: { amount: 7.49, currency: "USD" } }, deals: [deal("GOG", 11.99, 60, null), deal("Steam", 7.49, 75, "H")] },
  { id: ID2, historyLow: null, deals: [] },
];

function fakeItad({ infoStatus = () => 200, pricesStatus = 200 } = {}) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    calls.push({ url, init });
    if (url.includes("/games/info/v2")) {
      const id = new URL(url).searchParams.get("id");
      const status = infoStatus(id);
      return new Response(JSON.stringify(status === 200 ? infos[id] : { status }), { status });
    }
    if (url.includes("/games/prices/v3")) {
      const asked = JSON.parse(init.body);
      return new Response(JSON.stringify(prices.filter((p) => asked.includes(p.id))), { status: pricesStatus });
    }
    throw new Error(`unexpected ${url}`);
  };
  return { fetch, calls };
}

function fakeCache() {
  const store = new Map();
  return {
    store,
    async match(req) { const hit = store.get(req.url); return hit ? hit.clone() : undefined; },
    async put(req, res) { store.set(req.url, res); },
  };
}

const visit = (path, deps, opts) => handleWishlistRequest(request(path, opts), env, undefined, deps);

// ---------------------------------------------------------------- link rules (same as ShareLinks.kt)

test("ids encode to the same 22 characters the app builds, and decode back", () => {
  assert.equal(encodeId(ID), ENC);
  assert.equal(encodeId(ID2), ENC2);
  assert.equal(encodeId("00000000-0000-0000-0000-000000000000"), "AAAAAAAAAAAAAAAAAAAAAA");
  assert.equal(encodeId("ffffffff-ffff-ffff-ffff-ffffffffffff"), "_____________________w");
  assert.equal(decodeId(ENC), ID);
  assert.equal(decodeId(ENC2), ID2);
  for (const id of many(10)) assert.equal(decodeId(encodeId(id)), id);
});

test("a wishlist path keeps the order picked and drops repeats", () => {
  assert.equal(wishlistPath([ID, ID2]), `/w/${ENC}${ENC2}`);
  assert.deepEqual(decodeWishlist(`${ENC2}${ENC}`), [ID2, ID]);
  assert.deepEqual(decodeWishlist(`${ENC}${ENC2}${ENC}`), [ID, ID2]);
  assert.equal(wishlistPath(many(10)).length, 3 + 220);
});

test("broken wishlist segments are rejected", () => {
  for (const segment of [
    "", ENC.slice(0, -1), `${ENC}A`, "AY2TfwfCcVW24vX5s8W3p+", "AY2TfwfCcVW24vX5s8W3p=",
    "AY2TfwfCcVW24vX5s8W3pR", // stray low bits in the last character
    many(10).map(encodeId).join("") + ENC, // eleven games
  ]) {
    assert.equal(decodeWishlist(segment), null, segment);
  }
  assert.ok(isWishlistRoute(`/w/${ENC}`));
  assert.ok(!isWishlistRoute(`/g/${ID}`));
});

// ---------------------------------------------------------------- the page

test("a wishlist link renders each game in order with its best price and a buy button", async () => {
  const itad = fakeItad();
  const response = await visit(`/w/${ENC}${ENC2}`, { fetch: itad.fetch, cache: null }, { country: "GB" });
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.match(html, /<h1>A wishlist with 2 games<\/h1>/);
  assert.ok(html.indexOf("Hades") < html.indexOf("Hollow Knight"), "the sharer's order");
  assert.match(html, /\$7\.49 \(−75%\) at Steam · Lowest price ever/);
  assert.match(html, /href="https:\/\/itad\.link\/steam"/, "buy goes to the cheapest store");
  assert.match(html, /Hollow Knight<\/a>\s*<p class="deal">Not on sale anywhere right now/);
  assert.match(html, new RegExp(`href="/g/${ID}"`), "each title opens the game's own page");
  assert.doesNotMatch(html, /<II>/, "titles are escaped");
  assert.doesNotMatch(html, /insecure\.example/, "non-https art is dropped");
  assert.match(html, /property="og:title" content="A wishlist with 2 games"/);
  assert.match(html, /property="og:description" content="Hades &lt;II&gt; and Hollow Knight, with live prices\."/);
  assert.match(html, /property="og:image" content="https:\/\/assets\.example\/hades600\.jpg"/);
  assert.match(html, /buy a game as a gift/);
  assert.match(html, /play\.google\.com/);
});

test("the list page is never cached by the edge or the browser", async () => {
  const response = await visit(`/w/${ENC}`, { fetch: fakeItad().fetch, cache: fakeCache() });
  assert.equal(response.headers.get("cache-control"), "private, no-store");
  for (const status of [404, 503]) {
    const path = status === 404 ? "/w/nope" : `/w/${ENC}`;
    const deps = status === 404 ? {} : { fetch: fakeItad({ infoStatus: () => 500 }).fetch, cache: null };
    const failed = await visit(path, deps);
    assert.equal(failed.status, status);
    assert.equal(failed.headers.get("cache-control"), "private, no-store");
  }
});

test("only public per-game data is cached, each game on its own key, never the list", async () => {
  const cache = fakeCache();
  const itad = fakeItad();
  await visit(`/w/${ENC}${ENC2}`, { fetch: itad.fetch, cache }, { country: "DE" });
  const keys = [...cache.store.keys()].sort();
  assert.deepEqual(keys, [
    `https://gamedeals.bam.pm/__share-cache/info/${ID}`,
    `https://gamedeals.bam.pm/__share-cache/info/${ID2}`,
    `https://gamedeals.bam.pm/__share-cache/price/${ID}?country=DE`,
    `https://gamedeals.bam.pm/__share-cache/price/${ID2}?country=DE`,
  ]);
  // A second list sharing one game reuses it and asks ITAD only for what it hasn't seen.
  const calls = itad.calls.length;
  const response = await visit(`/w/${ENC2}${ENC}`, { fetch: itad.fetch, cache }, { country: "DE" });
  assert.equal(response.status, 200);
  assert.equal(itad.calls.length, calls, "everything came from the per-game cache");
  // A new country needs prices again, but not titles.
  await visit(`/w/${ENC}`, { fetch: itad.fetch, cache }, { country: "FR" });
  const fresh = itad.calls.slice(calls);
  assert.equal(fresh.length, 1);
  assert.match(fresh[0].url, /prices\/v3\?country=FR/);
  assert.deepEqual(JSON.parse(fresh[0].init.body), [ID]);
});

test("prices come in one batched call with the key in a header, never in the URL", async () => {
  const itad = fakeItad();
  await visit(`/w/${ENC}${ENC2}`, { fetch: itad.fetch, cache: null }, { country: "SE" });
  const priceCalls = itad.calls.filter((c) => c.url.includes("/games/prices/v3"));
  assert.equal(priceCalls.length, 1);
  assert.deepEqual(JSON.parse(priceCalls[0].init.body), [ID, ID2]);
  for (const call of itad.calls) {
    assert.equal(call.init.headers["ITAD-API-Key"], "test-key");
    assert.doesNotMatch(call.url, /test-key/);
  }
});

test("other spellings of a wishlist link redirect to the canonical link", async () => {
  for (const path of [`/w/${ENC}${ENC2}/`, `/w/${ENC}${ENC2}?fbclid=x`, `/w/${ENC}${ENC2}${ENC}`]) {
    const response = await visit(path, { fetch: fakeItad().fetch, cache: null });
    assert.equal(response.status, 301, path);
    assert.equal(response.headers.get("location"), `https://gamedeals.bam.pm/w/${ENC}${ENC2}`);
  }
});

test("a broken link gets the not-found page without calling ITAD", async () => {
  const itad = fakeItad();
  for (const path of ["/w/", "/w/nope", `/w/${ENC.toLowerCase()}`, `/w/${ENC}/more`, "/w/<script>"]) {
    const response = await visit(path, { fetch: itad.fetch, cache: null });
    assert.equal(response.status, 404, path);
    assert.match(await response.text(), /couldn't read that wishlist/);
  }
  assert.equal(itad.calls.length, 0);
});

test("games ITAD doesn't know are left out; a list with none left is not found", async () => {
  const one = await visit(`/w/${ENC}${ENC2}`, { fetch: fakeItad({ infoStatus: (id) => (id === ID2 ? 404 : 200) }).fetch, cache: null });
  assert.equal(one.status, 200);
  const html = await one.text();
  assert.match(html, /A wishlist with 1 game</);
  assert.doesNotMatch(html, /Hollow Knight/);
  assert.doesNotMatch(html, /couldn't be loaded/);
  const none = await visit(`/w/${ENC}`, { fetch: fakeItad({ infoStatus: () => 404 }).fetch, cache: null });
  assert.equal(none.status, 404);
});

test("a game whose info fails is counted; when every game fails the page says prices aren't loading", async () => {
  const some = await visit(`/w/${ENC}${ENC2}`, { fetch: fakeItad({ infoStatus: (id) => (id === ID2 ? 500 : 200) }).fetch, cache: fakeCache() });
  assert.equal(some.status, 200);
  assert.match(await some.text(), /1 game couldn't be loaded right now/);
  const cache = fakeCache();
  const all = await visit(`/w/${ENC}`, { fetch: fakeItad({ infoStatus: () => 500 }).fetch, cache });
  assert.equal(all.status, 503);
  assert.match(await all.text(), /Prices aren't loading/);
  assert.ok(![...cache.store.keys()].some((k) => k.includes("/info/")), "failures are not cached");
});

test("if only the prices call fails, the games still show without prices", async () => {
  const response = await visit(`/w/${ENC}`, { fetch: fakeItad({ pricesStatus: 500 }).fetch, cache: null });
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.match(html, /Not on sale anywhere right now/);
  assert.doesNotMatch(html, /class="buy"/);
});

test("a missing API key fails safe instead of calling ITAD without one", async () => {
  const itad = fakeItad();
  const response = await handleWishlistRequest(request(`/w/${ENC}`), {}, undefined, { fetch: itad.fetch, cache: null });
  assert.equal(response.status, 503);
  assert.equal(itad.calls.length, 0);
});

test("HEAD returns headers without a body, and other methods are refused", async () => {
  const head = await visit(`/w/${ENC}`, { fetch: fakeItad().fetch, cache: null }, { method: "HEAD" });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), "");
  const post = await visit(`/w/${ENC}`, { fetch: fakeItad().fetch, cache: null }, { method: "POST" });
  assert.equal(post.status, 405);
});

test("the preview line names up to three games", () => {
  const g = (title) => ({ title });
  assert.equal(titlesLine([g("A")]), "A");
  assert.equal(titlesLine([g("A"), g("B")]), "A and B");
  assert.equal(titlesLine([g("A"), g("B"), g("C")]), "A, B and C");
  assert.equal(titlesLine([g("A"), g("B"), g("C"), g("D"), g("E")]), "A, B, C and 2 more");
});
