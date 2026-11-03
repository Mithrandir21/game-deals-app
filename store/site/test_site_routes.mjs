import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { handleSiteRequest } from "./site-routes.mjs";

const eventJson = readFileSync(new URL("./winter-sale-2026.json", import.meta.url), "utf8");
const request = (path, method = "GET") => new Request(`https://gamedeals.bam.pm${path}`, { method });

test("the event route serves JSON with a server date and no-store", async () => {
  const response = handleSiteRequest(request("/events/winter-sale-2026.json"), "<html></html>", eventJson);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(response.headers.get("content-type"), /application\/json/);
  assert.ok(Number.isFinite(Date.parse(response.headers.get("date"))));
  assert.equal(JSON.parse(await response.text()).enabled, false);
});

test("HEAD returns the event headers without a body", async () => {
  const response = handleSiteRequest(request("/events/winter-sale-2026.json", "HEAD"), "<html></html>", eventJson);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(await response.text(), "");
});

test("the support page and legacy redirect keep their behaviour", async () => {
  const home = handleSiteRequest(request("/"), "<html>support</html>", eventJson);
  const unknown = handleSiteRequest(request("/other"), "<html>support</html>", eventJson);
  assert.equal(await home.text(), "<html>support</html>");
  assert.equal(home.headers.get("cache-control"), "public, max-age=300");
  assert.equal(unknown.status, 302);
  assert.equal(unknown.headers.get("location"), "https://gamedeals.bam.pm/");
});

test("unsupported methods cannot change the document", () => {
  const response = handleSiteRequest(request("/events/winter-sale-2026.json", "POST"), "", eventJson);
  assert.equal(response.status, 405);
});
