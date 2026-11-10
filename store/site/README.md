# Game Deals support Worker and Winter Sale event document

`worker.js` serves the support page and the Winter Sale control document at `/events/winter-sale-2026.json`. The event route returns `Cache-Control: no-store` and a server `Date` header. It also serves a developer test document at `/events/winter-sale-2026-test.json` (event ID `steam-winter-2026-test`), which only debug builds with the developer screen's test switch read. The checked-in documents start with `enabled: false`; change it only for the intended soft launch and deploy this Worker to make the change live.

Before a deployment, run:

```bash
python3 store/site/validate_event.py
python3 -m unittest discover -s store/site -p 'test_*.py'
node store/site/test_site_routes.mjs
cd store/site && npx wrangler@4 deploy --dry-run
```

The `gamedeals-site` Worker serves `gamedeals.bam.pm`. The separate `bam.pm` Pages site belongs to DuoPoint; deploying this Worker does not update the privacy policy there. To turn the guide off, set `enabled` to `false`, validate, and deploy this Worker. The app treats a freshly fetched disabled document as authoritative and limits use of a cached enabled document to 12 hours when offline.
