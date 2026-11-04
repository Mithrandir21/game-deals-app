const EVENT_PATH = "/events/winter-sale-2026.json";
// Read only by debug builds with the developer screen's test switch on; never by a release build.
const TEST_EVENT_PATH = "/events/winter-sale-2026-test.json";

export function handleSiteRequest(request, html, eventJson, testEventJson) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405, headers: { allow: "GET, HEAD" } });
  }

  const { pathname } = new URL(request.url);
  if (pathname === EVENT_PATH || (pathname === TEST_EVENT_PATH && testEventJson !== undefined)) {
    const body = pathname === EVENT_PATH ? eventJson : testEventJson;
    return new Response(request.method === "HEAD" ? null : body, {
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
        "date": new Date().toUTCString(),
        "x-content-type-options": "nosniff",
      },
    });
  }

  if (pathname !== "/" && pathname !== "/index.html") {
    return Response.redirect(new URL("/", request.url).toString(), 302);
  }

  return new Response(request.method === "HEAD" ? null : html, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "public, max-age=300",
      "x-content-type-options": "nosniff",
    },
  });
}
