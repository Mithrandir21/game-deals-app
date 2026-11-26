import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { FLAGS_KEY, handleFlagsRequest, isFlagsRoute, validFlags } from "./flags-routes.mjs";

const template = readFileSync(new URL("./flags.json", import.meta.url), "utf8");
const request = (method = "GET") => new Request("https://gamedeals.bam.pm/api/flags/v1", { method });
const kv = (values) => ({
  reads: [],
  async get(key) {
    this.reads.push(key);
    if (values instanceof Error) throw values;
    return values[key] ?? null;
  },
});

test("only the flags path is a flags route", () => {
  assert.ok(isFlagsRoute("/api/flags/v1"));
  assert.ok(!isFlagsRoute("/api/flags/v2"));
  assert.ok(!isFlagsRoute("/api/flags/v1/"));
});

test("the stored switch file is served as public JSON for a minute", async () => {
  const flags = kv({ [FLAGS_KEY]: template });
  const response = await handleFlagsRequest(request(), { FLAGS: flags });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "application/json; charset=utf-8");
  assert.equal(response.headers.get("cache-control"), "public, max-age=60");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  assert.deepEqual(JSON.parse(await response.text()), JSON.parse(template));
  assert.deepEqual(flags.reads, ["flags"]);
});

test("the template in the repo is a valid switch file with every switch on but the update prompt", () => {
  const { flags } = validFlags(template);
  const off = Object.entries(flags).filter(([, value]) => value === false || value?.enabled === false).map(([key]) => key);
  assert.deepEqual(off, ["force_update"]);
  assert.deepEqual(
    Object.keys(flags).sort(),
    ["bundle_watch", "deal_links", "deal_swipe", "discover_by_tag", "force_update", "game_signals", "sale_countdown", "wishlist_links", "year_in_deals"],
  );
});

test("no namespace bound, or no switch file yet, serves an empty list so every switch takes its default", async () => {
  for (const env of [{}, undefined, { FLAGS: kv({}) }]) {
    const response = await handleFlagsRequest(request(), env);
    assert.equal(response.status, 200);
    assert.deepEqual(JSON.parse(await response.text()), { schemaVersion: 1, flags: {} });
  }
});

test("a switch file that isn't valid answers 503 so phones keep their last good values", async () => {
  const invalid = [
    "{not json",
    "[]",
    "null",
    '"text"',
    '{"flags":{}}',
    '{"schemaVersion":2,"flags":{}}',
    '{"schemaVersion":"1","flags":{}}',
    '{"schemaVersion":1}',
    '{"schemaVersion":1,"flags":[]}',
    '{"schemaVersion":1,"flags":{"deal_swipe":"false"}}',
    '{"schemaVersion":1,"flags":{"deal_swipe":0}}',
    '{"schemaVersion":1,"flags":{"deal_swipe":null}}',
    '{"schemaVersion":1,"flags":{"Deal-Swipe":true}}',
    '{"schemaVersion":1,"flags":{"force_update":{"payload":{}}}}',
    '{"schemaVersion":1,"flags":{"force_update":{"enabled":"yes"}}}',
    '{"schemaVersion":1,"flags":{"force_update":{"enabled":true,"payload":"1.7.0"}}}',
    '{"schemaVersion":1,"flags":{"force_update":{"enabled":true,"payload":[1]}}}',
    '{"schemaVersion":1,"flags":{"force_update":{"enabled":true,"extra":1}}}',
    `{"schemaVersion":1,"flags":{"a":true},"padding":"${"x".repeat(16_384)}"}`,
  ];
  for (const text of invalid) {
    const response = await handleFlagsRequest(request(), { FLAGS: kv({ [FLAGS_KEY]: text }) });
    assert.equal(response.status, 503, text.slice(0, 80));
    assert.equal(response.headers.get("cache-control"), "no-store");
  }
});

test("a key-value read failure answers 503 too", async () => {
  const response = await handleFlagsRequest(request(), { FLAGS: kv(new Error("KV down")) });
  assert.equal(response.status, 503);
});

test("a switch may carry a payload, or a null one, and other keys pass through", async () => {
  const text = '{"schemaVersion":1,"flags":{"force_update":{"enabled":true,"payload":null},"some_new_switch":false}}';
  const response = await handleFlagsRequest(request(), { FLAGS: kv({ [FLAGS_KEY]: text }) });
  assert.equal(response.status, 200);
  assert.deepEqual(JSON.parse(await response.text()).flags, { force_update: { enabled: true, payload: null }, some_new_switch: false });
});

test("only the document's schema and flags are served, not other fields", async () => {
  const text = '{"schemaVersion":1,"flags":{"deal_swipe":false},"note":"turned off for the outage"}';
  const response = await handleFlagsRequest(request(), { FLAGS: kv({ [FLAGS_KEY]: text }) });
  assert.deepEqual(JSON.parse(await response.text()), { schemaVersion: 1, flags: { deal_swipe: false } });
});

test("HEAD answers the headers without a body", async () => {
  const response = await handleFlagsRequest(request("HEAD"), { FLAGS: kv({ [FLAGS_KEY]: template }) });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "public, max-age=60");
  assert.equal(await response.text(), "");
});

test("other methods are refused and never read the namespace", async () => {
  for (const method of ["POST", "PUT", "DELETE", "PATCH"]) {
    const flags = kv({ [FLAGS_KEY]: template });
    const response = await handleFlagsRequest(request(method), { FLAGS: flags });
    assert.equal(response.status, 405);
    assert.equal(response.headers.get("allow"), "GET, HEAD");
    assert.deepEqual(flags.reads, []);
  }
});
