# Game Deals support Worker and Winter Sale event document

`worker.js` serves the support page and the Winter Sale control document at `/events/winter-sale-2026.json`. The event route returns `Cache-Control: no-store` and a server `Date` header. It also serves a developer test document at `/events/winter-sale-2026-test.json` (event ID `steam-winter-2026-test`), which only debug builds with the developer screen's test switch read. The checked-in documents start with `enabled: false`; change it only for the intended soft launch and deploy this Worker to make the change live.

Before a deployment, run:

```bash
python3 store/site/validate_event.py
python3 store/site/validate_sales.py
python3 -m unittest discover -s store/site -p 'test_*.py'
node --test store/site/test_site_routes.mjs store/site/test_share_routes.mjs store/site/test_wishlist_routes.mjs store/site/test_signals_routes.mjs
cd store/site && npx wrangler@4 deploy --dry-run
```

## Big-sale dates

`sales.json` lists the sales on Steam, Epic and GOG, served at `/events/sales.json` (public, cached for an hour). Each sale has a `kind`: `big` for a store-wide seasonal sale, `fest` for a smaller themed one such as a Steam fest (a missing kind means `big`). A running big sale always shows; a fest gives way to a big sale starting within a week. Keep at least three months of sales listed ahead. The apps carry a copy of this file as a fallback, so an app release picks up whatever it holds then. The countdown widgets on Android and iOS, and the iOS Live Activity for a sale's last hours, read it. The dates are typed by hand and everyone sees them, so check each one against the store before deploying, and run `python3 store/site/validate_sales.py`. Times are UTC: Steam sales start and end at 10:00 Pacific (18:00 UTC in winter, 17:00 UTC in summer time), Epic's at 11:00 Eastern (16:00 UTC in winter). Remove past sales now and then. The file holds at most 40.

Where the first dates came from (23 Nov 2026):
- Steam Winter Sale 2026: Valve's Steamworks page (partner.steamgames.com/doc/marketing/upcoming_events/2026_winter_sale).
- Steam Spring and Summer Sale 2027: Valve's published 2027 schedule, as reported by several sale calendars (pcgamer.com/steam-sale-dates, gamedevproducer.com/steam-sale-dates). Start times are assumed to be Valve's usual 10:00 Pacific.
- Epic Holiday Sale 2026: Epic's developer promotion schedule, as reported by SteamDB (steamdb.com/en/promo-codes/epic-games-store). Epic can move it.
- GOG hasn't announced its winter sale yet; add it when it does.

When publishing through the Cloudflare connector, upload `sales.json` like the event documents: as `export default {…};` with type `application/javascript+module`.

## Shared game links

`share-routes.mjs` serves `https://gamedeals.bam.pm/g/<ITAD game id>`, the link the app shares for a game. With the app installed, Android App Links and iOS Universal Links open the game in the app. Otherwise the visitor gets a preview page: the cover, the best current price, the five cheapest stores, Google Play and App Store buttons, and the tags chat apps read for a link preview. The path rules match `ShareLinks.kt` in `common/links`; change both together.

- **ITAD key:** the page reads prices from IsThereAnyDeal with the app's API key, set as the Worker secret `ITAD_API_KEY` (never in a file). Without it, game links show a "prices aren't loading" page.
- **Shared allowance:** ITAD's call limit is shared with every app user. Each rendered page is kept in the edge cache for an hour per game and country, so a link shared widely costs two ITAD calls per hour per country, not two per visit.
- **Privacy:** the visitor's country picks the price region for that response only. The cache key holds the game id and country, nothing else, and request logs stay off.
- **App-link files:** `app-links.mjs` holds the Android package and signing fingerprints for `/.well-known/assetlinks.json` and the iOS app id for `/.well-known/apple-app-site-association`. Android needs **Play App Signing's** SHA-256 fingerprint (Play Console → App integrity), not the upload key's. Until one is added, `assetlinks.json` returns 404 and Android shows links in the browser.
- **Wishlist links:** `wishlist-routes.mjs` serves `https://gamedeals.bam.pm/w/<ids>`, the link a signed-in user shares for up to ten games from their waitlist. The link is the list: each ITAD game id is written as 22 URL-safe base64 characters, in the order picked, and nothing is stored. The preview page shows each game's best price with a buy button. The page itself is never cached (`private, no-store`), because the set of games is the sharer's choice. Only public per-game data is cached, each game on its own key: title and art for a day, prices per country for an hour. The encoding matches `ShareLinks.wishlist` in the app, and both test files use the same sample links.
- **Game signals:** `signals-routes.mjs` answers `POST /api/signals/v1` with `{"ids": [...]}` (up to 50 ITAD game ids). For each game it returns the deepest discount in each of the last 104 weeks (US price history), the review score, player counts, and ITAD's waitlist and owner counts. Each game is one row in the D1 database `gamedeals-signals` (binding `SIGNALS_DB`), refreshed from ITAD at most once a day, so a game costs two ITAD calls a day however many phones show it. The answer never waits on ITAD: games without a row come back as `pending` and are fetched in the background, at most ten per request. The database holds public game data only; the ids a phone sends are used for that answer and not kept. Without the database or the ITAD key the endpoint returns 503, and the app shows rows without signals.
- **Setting up the signals database (once):** create it with `npx wrangler@4 d1 create gamedeals-signals` (or in the dashboard under Storage & Databases → D1), put its id in `wrangler.toml`, then create the table with `npx wrangler@4 d1 migrations apply gamedeals-signals --remote`. When publishing through the Cloudflare connector instead of wrangler, every upload's metadata must list `{ "type": "d1", "name": "SIGNALS_DB", "id": "<database id>" }` in `bindings` (an upload without it drops the binding) and carry `"keep_bindings": ["secret_text"]` so the `ITAD_API_KEY` secret survives.
- **Testing on a debug build:** a debug build isn't signed with the Play key, so verification fails. Approve the domain by hand instead: `adb shell pm set-app-links-user-selection --user cur --package pm.bam.gamedeals true gamedeals.bam.pm`.

The `gamedeals-site` Worker serves `gamedeals.bam.pm`. The separate `bam.pm` Pages site belongs to DuoPoint; deploying this Worker does not update the privacy policy there. To turn the guide off, set `enabled` to `false`, validate, and deploy this Worker. The app treats a freshly fetched disabled document as authoritative and limits use of a cached enabled document to 12 hours when offline.
