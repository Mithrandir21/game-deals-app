import html from "./index.html";
import winterSaleEvent from "./winter-sale-2026.json";
import winterSaleTestEvent from "./winter-sale-2026-test.json";
import { handleSiteRequest } from "./site-routes.mjs";
import { handleShareRequest, isShareRoute } from "./share-routes.mjs";

export default {
  async fetch(request, env, ctx) {
    // Shared game links and the app-link files first; everything else is the support page and the event documents.
    if (isShareRoute(new URL(request.url).pathname)) {
      return handleShareRequest(request, env, ctx);
    }
    return handleSiteRequest(request, html, JSON.stringify(winterSaleEvent), JSON.stringify(winterSaleTestEvent));
  },
};
