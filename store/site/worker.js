import html from "./index.html";
import winterSaleEvent from "./winter-sale-2026.json";
import winterSaleTestEvent from "./winter-sale-2026-test.json";
import { handleSiteRequest } from "./site-routes.mjs";
import { handleShareRequest, isShareRoute } from "./share-routes.mjs";
import { handleWishlistRequest, isWishlistRoute } from "./wishlist-routes.mjs";
import { handleSignalsRequest, isSignalsRoute } from "./signals-routes.mjs";

export default {
  async fetch(request, env, ctx) {
    // Shared game and wishlist links and the app-link files first; everything else is the support page and the
    // event documents.
    const { pathname } = new URL(request.url);
    if (isShareRoute(pathname)) return handleShareRequest(request, env, ctx);
    if (isWishlistRoute(pathname)) return handleWishlistRequest(request, env, ctx);
    if (isSignalsRoute(pathname)) return handleSignalsRequest(request, env, ctx);
    return handleSiteRequest(request, html, JSON.stringify(winterSaleEvent), JSON.stringify(winterSaleTestEvent));
  },
};
