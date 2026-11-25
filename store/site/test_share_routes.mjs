import assert from "node:assert/strict";
import { test } from "node:test";
import { handleShareRequest, isShareRoute, summaryLine } from "./share-routes.mjs";
import { ANDROID_CERT_SHA256, assetLinksJson, appleAppSiteAssociationJson } from "./app-links.mjs";

const ID = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5";
const env = { ITAD_API_KEY: "test-key" };
const request = (path, { method = "GET", country } = {}) =>
  ({ method, url: `https://gamedeals.bam.pm${path}`, cf: country ? { country } : undefined });

const info = {
  id: ID,
  title: "Hades <II>",
  assets: { banner600: "https://assets.isthereanydeal.com/banner600.jpg", boxart: "http://insecure.example/box.jpg" },
};
const deal = (shop, amount, cut, flag) => ({
  shop: { id: shop.length, name: shop },
  price: { amount, amountInt: Math.round(amount * 100), currency: "USD" },
  regular: { amount: 29.99, currency: "USD" },
  cut,
  url: `https://itad.link/${shop.toLowerCase()}`,
  flag,
});
const prices = [{ id: ID, historyLow: { all: { amount: 7.49, currency: "USD" } }, deals: [
  deal("GOG", 11.99, 60, null),
  deal("Steam", 7.49, 75, "H"),
  { ...deal("Sketchy", 1, 97, null), url: "http://not-https.example" },
] }];

function fakeItad({ infoStatus = 200, infoBody = info, pricesStatus = 200, pricesBody = prices } = {}) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    calls.push({ url, init });
    if (url.includes("/games/info/v2")) return new Response(JSON.stringify(infoBody), { status: infoStatus });
    if (url.includes("/games/prices/v3")) return new Response(JSON.stringify(pricesBody), { status: pricesStatus });
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

test("a game link renders the game, its best price and the cheapest https stores", async () => {
  const itad = fakeItad();
  const response = await handleShareRequest(request(`/g/${ID}`, { country: "GB" }), env, undefined, { fetch: itad.fetch, cache: null });
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.match(html, /<h1>Hades &lt;II&gt;<\/h1>/);
  assert.match(html, /\$7\.49/);
  assert.match(html, /Lowest price ever/);
  assert.ok(html.indexOf("Steam") < html.indexOf("GOG"), "cheapest store first");
  assert.doesNotMatch(html, /Sketchy/, "non-https store links are dropped");
  assert.doesNotMatch(html, /<II>/, "game titles are escaped");
  assert.match(html, /property="og:image" content="https:\/\/assets\.isthereanydeal\.com\/banner600\.jpg"/);
  assert.match(html, /property="og:description" content="\$7\.49 at Steam \(−75%\) · Lowest price ever"/);
  assert.match(html, /play\.google\.com\/store\/apps\/details\?id=pm\.bam\.gamedeals/);
  assert.match(html, /apps\.apple\.com\/app\/id6818586859/);
  assert.match(response.headers.get("cache-control"), /^private, max-age=\d+$/);
  assert.ok(response.headers.get("content-security-policy").includes("default-src 'none'"));
});

test("prices come from the visitor's country, sent as the ITAD key header, never in the URL", async () => {
  const itad = fakeItad();
  await handleShareRequest(request(`/g/${ID}`, { country: "DE" }), env, undefined, { fetch: itad.fetch, cache: null });
  const pricesCall = itad.calls.find((c) => c.url.includes("/games/prices/v3"));
  assert.match(pricesCall.url, /country=DE/);
  assert.deepEqual(JSON.parse(pricesCall.init.body), [ID]);
  for (const call of itad.calls) {
    assert.equal(call.init.headers["ITAD-API-Key"], "test-key");
    assert.doesNotMatch(call.url, /test-key/);
  }
});

test("an unknown or unusable country falls back to US", async () => {
  for (const country of [undefined, "XX", "T1", "gb", "USA"]) {
    const itad = fakeItad();
    await handleShareRequest(request(`/g/${ID}`, { country }), env, undefined, { fetch: itad.fetch, cache: null });
    assert.match(itad.calls.find((c) => c.url.includes("prices")).url, /country=US/, `country ${country}`);
  }
});

test("other spellings of a game link redirect to the one canonical link", async () => {
  for (const path of [`/g/${ID.toUpperCase()}`, `/g/${ID}/`, `/g/${ID}?fbclid=abc`]) {
    const response = await handleShareRequest(request(path), env, undefined, { fetch: fakeItad().fetch, cache: null });
    assert.equal(response.status, 301, path);
    assert.equal(response.headers.get("location"), `https://gamedeals.bam.pm/g/${ID}`);
  }
});

test("a malformed id gets the not-found page without calling ITAD", async () => {
  const itad = fakeItad();
  for (const path of ["/g/", "/g/half-life", `/g/${ID}/more`, "/g/<script>"]) {
    const response = await handleShareRequest(request(path), env, undefined, { fetch: itad.fetch, cache: null });
    assert.equal(response.status, 404, path);
    assert.match(await response.text(), /couldn't find that game/);
  }
  assert.equal(itad.calls.length, 0);
});

test("a game ITAD doesn't know gets a cached not-found page", async () => {
  const cache = fakeCache();
  const itad = fakeItad({ infoStatus: 404, infoBody: { status: 404 } });
  const response = await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch: itad.fetch, cache });
  assert.equal(response.status, 404);
  assert.equal(cache.store.size, 1);
});

test("when ITAD fails the page says so, keeps the app buttons and is never cached", async () => {
  const cache = fakeCache();
  const itad = fakeItad({ infoStatus: 500 });
  const response = await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch: itad.fetch, cache });
  assert.equal(response.status, 503);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(await response.text(), /Get Game Deals/);
  assert.equal(cache.store.size, 0);
});

test("a missing API key fails safe instead of calling ITAD without one", async () => {
  const itad = fakeItad();
  const response = await handleShareRequest(request(`/g/${ID}`), {}, undefined, { fetch: itad.fetch, cache: null });
  assert.equal(response.status, 503);
  assert.equal(itad.calls.length, 0);
});

test("if only the prices call fails, the game page still renders without prices", async () => {
  const itad = fakeItad({ pricesStatus: 500, pricesBody: {} });
  const response = await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch: itad.fetch, cache: null });
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.match(html, /No store is selling it right now/);
  assert.doesNotMatch(html, /class="stores"/);
});

test("a second visit from the same country is served from the edge cache", async () => {
  const cache = fakeCache();
  const itad = fakeItad();
  const waits = [];
  const ctx = { waitUntil: (p) => waits.push(p) };
  await handleShareRequest(request(`/g/${ID}`, { country: "GB" }), env, ctx, { fetch: itad.fetch, cache });
  await Promise.all(waits);
  const callsAfterFirst = itad.calls.length;
  const second = await handleShareRequest(request(`/g/${ID}`, { country: "GB" }), env, ctx, { fetch: itad.fetch, cache });
  assert.equal(itad.calls.length, callsAfterFirst, "no new ITAD calls");
  assert.equal(second.status, 200);
  assert.match(second.headers.get("cache-control"), /^private/);
  assert.match(await second.text(), /Hades/);
  // A different country is a different page.
  await handleShareRequest(request(`/g/${ID}`, { country: "DE" }), env, ctx, { fetch: itad.fetch, cache });
  assert.ok(itad.calls.length > callsAfterFirst);
  // The edge copy holds nothing but the game id and country in its key.
  for (const key of cache.store.keys()) assert.match(key, /^https:\/\/gamedeals\.bam\.pm\/__share-cache\/g\/[0-9a-f-]{36}\?country=[A-Z]{2}$/);
});

test("HEAD returns headers without a body, and other methods are refused", async () => {
  const head = await handleShareRequest(request(`/g/${ID}`, { method: "HEAD" }), env, undefined, { fetch: fakeItad().fetch, cache: null });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), "");
  const post = await handleShareRequest(request(`/g/${ID}`, { method: "POST" }), env, undefined, { fetch: fakeItad().fetch, cache: null });
  assert.equal(post.status, 405);
});

test("Apple's association file claims game links for the iOS app", async () => {
  const response = await handleShareRequest(request("/.well-known/apple-app-site-association"), env, undefined, {});
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "application/json");
  assert.deepEqual(JSON.parse(await response.text()), JSON.parse(appleAppSiteAssociationJson()));
  const details = JSON.parse(appleAppSiteAssociationJson()).applinks.details[0];
  assert.deepEqual(details.appIDs, ["36N7FDX928.pm.bam.gamedeals.ios"]);
  assert.deepEqual(details.components, [{ "/": "/g/*" }, { "/": "/w/*" }]);
});

test("assetlinks.json is only served once a valid signing fingerprint is configured", async () => {
  const response = await handleShareRequest(request("/.well-known/assetlinks.json"), env, undefined, {});
  assert.equal(response.status, 200, "the Play App Signing key is configured");
  const served = JSON.parse(await response.text());
  assert.deepEqual(served[0].relation, ["delegate_permission/common.handle_all_urls"]);
  assert.deepEqual(served[0].target.sha256_cert_fingerprints, ANDROID_CERT_SHA256);
  assert.equal(assetLinksJson([]), null, "no fingerprint, no file");
  const fp = Array.from({ length: 32 }, (_, i) => i.toString(16).padStart(2, "0")).join(":");
  const statements = JSON.parse(assetLinksJson([fp, "not-a-fingerprint"]));
  assert.equal(statements[0].target.package_name, "pm.bam.gamedeals");
  assert.deepEqual(statements[0].target.sha256_cert_fingerprints, [fp.toUpperCase()]);
  assert.equal(assetLinksJson(["nope"]), null);
});

test("/app sends Apple devices to the App Store and everyone else to Google Play", async () => {
  const visit = (agent) => handleShareRequest(
    { method: "GET", url: "https://gamedeals.bam.pm/app", headers: new Headers(agent ? { "user-agent": agent } : {}) }, env, undefined, {});
  const iphone = await visit("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)");
  assert.equal(iphone.status, 302);
  assert.equal(iphone.headers.get("location"), "https://apps.apple.com/app/id6818586859");
  const android = await visit("Mozilla/5.0 (Linux; Android 15; Pixel 5)");
  assert.equal(android.headers.get("location"), "https://play.google.com/store/apps/details?id=pm.bam.gamedeals");
  const none = await visit(null);
  assert.equal(none.headers.get("location"), "https://play.google.com/store/apps/details?id=pm.bam.gamedeals");
  assert.equal(iphone.headers.get("cache-control"), "private, no-store");
});

test("only share paths are routed here", () => {
  assert.ok(isShareRoute("/app"));
  assert.ok(isShareRoute(`/g/${ID}`));
  assert.ok(isShareRoute("/.well-known/apple-app-site-association"));
  assert.ok(isShareRoute("/.well-known/assetlinks.json"));
  assert.ok(!isShareRoute("/"));
  assert.ok(!isShareRoute("/events/winter-sale-2026.json"));
  assert.ok(!isShareRoute("/game/x"));
});

test("the summary line reads naturally with and without a discount", () => {
  const free = { deals: [{ ...deal("Epic", 0, 100, null), price: { amount: 0, currency: "USD" } }] };
  assert.equal(summaryLine(free), "$0.00 at Epic (−100%)");
  const full = { deals: [deal("Steam", 29.99, 0, null)] };
  assert.equal(summaryLine(full), "$29.99 at Steam");
});

// ---------------------------------------------------------------- ITAD trouble (hardening, 2026-11-24)

/** A fetch that never answers until its signal gives up, like a stalled ITAD. */
const stalled = (url, init = {}) => new Promise((_, reject) => {
  if (!init.signal) return reject(new Error("no time limit on this call"));
  // AbortSignal.timeout's timer doesn't hold the event loop open, and Node 22's test runner then cancels the test
  // mid-wait. This timer holds it open until the signal gives up.
  const keepAlive = setTimeout(() => {}, 10_000);
  init.signal.addEventListener("abort", () => {
    clearTimeout(keepAlive);
    reject(init.signal.reason);
  });
});

test("both ITAD calls carry the page's time limit", async () => {
  const itad = fakeItad();
  await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch: itad.fetch, cache: null });
  assert.equal(itad.calls.length, 2);
  for (const call of itad.calls) assert.ok(call.init.signal instanceof AbortSignal, call.url);
});

test("a stalled ITAD gives up after the time limit and shows the unavailable page, uncached", async () => {
  const cache = fakeCache();
  const started = Date.now();
  const response = await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch: stalled, cache, timeoutMs: 30 });
  assert.equal(response.status, 503);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(await response.text(), /Get Game Deals/);
  assert.ok(Date.now() - started < 2_000, "gave up quickly");
  assert.equal(cache.store.size, 0);
});

test("prices that stall take the page down with them rather than wait", async () => {
  const itad = fakeItad();
  const fetch = (url, init) => (url.includes("/games/prices/v3") ? stalled(url, init) : itad.fetch(url, init));
  const response = await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch, cache: fakeCache(), timeoutMs: 30 });
  assert.equal(response.status, 503);
});

test("ITAD rate-limiting the game (429) shows the unavailable page and is never cached", async () => {
  const cache = fakeCache();
  const itad = fakeItad({ infoStatus: 429, infoBody: { status: 429 } });
  const response = await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch: itad.fetch, cache });
  assert.equal(response.status, 503);
  assert.equal(cache.store.size, 0);
});

test("a page whose prices were rate-limited shows the game but is never kept", async () => {
  const cache = fakeCache();
  const itad = fakeItad({ pricesStatus: 429, pricesBody: { status: 429 } });
  const response = await handleShareRequest(request(`/g/${ID}`), env, undefined, { fetch: itad.fetch, cache });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(await response.text(), /No store is selling it right now/);
  assert.equal(cache.store.size, 0);
});
