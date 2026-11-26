// The app's remote feature switches: GET /api/flags/v1 serves the `flags` value of the FLAGS key-value namespace
// (Cloudflare KV, edited by hand in the dashboard; see README). Public, and nothing about the user is sent or kept.
//
// A value that isn't a valid switch file answers 503, so phones keep the last good values they have: a typo in the
// dashboard can never flip every switch back to its default. With no namespace bound, or no `flags` key yet, the
// Worker serves an empty switch list, which means every switch takes its default in the app.

export const FLAGS_PATH = "/api/flags/v1";
export const FLAGS_KEY = "flags";
const EMPTY = JSON.stringify({ schemaVersion: 1, flags: {} });
const MAX_DOCUMENT_CHARS = 16_384;
const KEY = /^[a-z][a-z0-9_]{0,63}$/;

export function isFlagsRoute(pathname) {
  return pathname === FLAGS_PATH;
}

export async function handleFlagsRequest(request, env) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405, headers: { allow: "GET, HEAD" } });
  }
  let body;
  try {
    const stored = env?.FLAGS ? await env.FLAGS.get(FLAGS_KEY) : null;
    body = stored == null ? EMPTY : JSON.stringify(validFlags(stored));
  } catch {
    return new Response("The switch file isn't valid", {
      status: 503,
      headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store", "retry-after": "300" },
    });
  }
  return new Response(request.method === "HEAD" ? null : body, {
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "public, max-age=60",
      "x-content-type-options": "nosniff",
    },
  });
}

/**
 * The switch file, checked: `{"schemaVersion": 1, "flags": {"<key>": true | false | {"enabled": bool, "payload": {…}}}}`.
 * Throws on anything else, so the caller answers 503.
 */
export function validFlags(text) {
  if (typeof text !== "string" || text.length > MAX_DOCUMENT_CHARS) throw new Error("size");
  const document = JSON.parse(text);
  if (!isObject(document) || document.schemaVersion !== 1 || !isObject(document.flags)) throw new Error("shape");
  for (const [key, value] of Object.entries(document.flags)) {
    if (!KEY.test(key)) throw new Error(`key ${key}`);
    if (typeof value === "boolean") continue;
    if (!isObject(value) || typeof value.enabled !== "boolean") throw new Error(`value ${key}`);
    if (Object.keys(value).some((field) => field !== "enabled" && field !== "payload")) throw new Error(`field ${key}`);
    if ("payload" in value && value.payload !== null && !isObject(value.payload)) throw new Error(`payload ${key}`);
  }
  return { schemaVersion: 1, flags: document.flags };
}

function isObject(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
