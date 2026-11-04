import html from "./index.html";
import winterSaleEvent from "./winter-sale-2026.json";
import winterSaleTestEvent from "./winter-sale-2026-test.json";
import { handleSiteRequest } from "./site-routes.mjs";

export default {
  async fetch(request) {
    return handleSiteRequest(request, html, JSON.stringify(winterSaleEvent), JSON.stringify(winterSaleTestEvent));
  },
};
