# Winter Sale guide — rollout runbook

How the guide goes from this branch to users, and how to turn it off. The execution plan in the owner's Documents folder holds the progress log; this file holds the steps.

## Dates

| When | Step |
|---|---|
| By 1 December | Code freeze. Merge `feat/winter-sale-guide` into `dev`, then follow the normal release branch flow. |
| 1 December | Publish the updated privacy policy (below). It must be live before any build that fetches the event document reaches users. |
| 1–3 December | Tag the release (`v1.6.0` suggested). Bitrise builds both platforms from the tag. Rehearse on the Play internal track and TestFlight (below), then submit both stores. |
| About 3 December | Play: promote to production at **100%** (the `promote-production` workflow defaults to a 10% staged rollout; use the Play Console button or change the percentage). iOS: release without phased rollout. |
| 10 December | Set `enabled: true` in the event document and deploy the Worker. This is the soft launch. Check the guide on both phones. |
| 17 December, 18:00 UTC | Sale starts. Check the guide switches to sale copy and that reminders arrive 1–3 hours later. |
| 4 January 2027, 18:00 UTC | Sale ends. Check the Home entry and the guide disappear. Then set `enabled: false` and deploy, so a later clock or cache problem can't bring it back. |

## Event document

The document is `store/site/winter-sale-2026.json`, served by the `gamedeals-site` Worker at `https://gamedeals.bam.pm/events/winter-sale-2026.json` with `Cache-Control: no-store`. The checked-in copy is disabled.

Deploy (from the repository root):

```bash
python3 store/site/validate_event.py
python3 -m unittest discover -s store/site -p 'test_*.py'
node store/site/test_site_routes.mjs
cd store/site && npx wrangler@4 deploy
```

Then confirm the live route:

```bash
curl -sSi https://gamedeals.bam.pm/events/winter-sale-2026.json | grep -iE '^(HTTP|cache-control|date)|"enabled"'
```

Expect `200`, `cache-control: no-store`, a `date` header, and the intended `enabled` value. Wrangler needs a login on this machine (`npx wrangler@4 login`). The owner prefers not to log in with Wrangler; use the Cloudflare connector instead (below).

### Deploying with the Cloudflare connector (no Wrangler login)

This was the method used for the first deploy on 4 November 2026. Claude Code's Cloudflare connector (`mcp__cloudflare-api__execute`) uploads the Worker with a multipart `PUT /accounts/{account}/workers/scripts/gamedeals-site`. The metadata is `main_module: "worker.js"`, `compatibility_date: "2025-09-01"`, no bindings and `observability: { enabled: false }`. It uploads four modules:

| Module | Content type | Content |
|---|---|---|
| `worker.js` | `application/javascript+module` | as in the repo |
| `site-routes.mjs` | `application/javascript+module` | as in the repo |
| `index.html` | `text/plain` | as in the repo |
| `winter-sale-2026.json` | `application/javascript+module` | `export default { …the document… };` |
| `winter-sale-2026-test.json` | `application/javascript+module` | `export default { …the test document… };` |

Cloudflare refuses a raw JSON module (`unsupported Content-Type application/json`), so the document is wrapped as a script module under the same name. `worker.js` imports it unchanged. Wrangler instead bundles everything into one file. Either way the live route serves the same JSON.

**Toggle only the switch.** Read the live modules (`GET …/workers/scripts/gamedeals-site/content/v2`, which returns multipart). Replace the single `"enabled": true|false` in `winter-sale-2026.json`. Upload every module again with the same metadata. Abort if the module is missing or has more than one `enabled`; that happens after a Wrangler deploy, because the bundle has no separate module. In that case do a full upload as in the table. Ask Claude to "switch the Winter Sale guide off with the Cloudflare connector"; this file has the steps.

After any change, run the `curl` check above and update `store/site/winter-sale-2026.json` in the repo to match.

### Turning it off from a phone (Cloudflare dashboard)

Not yet tried from a phone. In the Cloudflare dashboard:
1. Open Workers & Pages, then `gamedeals-site`, then Edit code.
2. Open `winter-sale-2026.json`, change `"enabled": true` to `"enabled": false`, then Deploy.
3. Check `https://gamedeals.bam.pm/events/winter-sale-2026.json` in the phone's browser: it should show `"enabled":false`.

Try this once before 10 December, so the phone route is known to work.

### Turning the guide off

1. Set `"enabled": false`: in `store/site/winter-sale-2026.json` with Wrangler, with the connector toggle, or in the dashboard editor.
2. Deploy (Wrangler: run the validation and deploy commands above), then run the `curl` check.
3. Effect: any phone that fetches the document hides the Home entry and the guide at once, and Android cancels its pending reminder. A phone that is offline keeps a cached enabled document for at most 12 hours.
4. Known limit: an iOS reminder that is already scheduled can still fire after a disable until the app is opened or the background check runs. Tapping it then opens Home, not the guide.

No app release is needed to turn the guide off.

**Practised 4 November 2026** with the connector. The route was deployed switched off (production had redirected it to the home page until then), then switched on, then off. Each change was live within about 5 seconds of the upload: the upload took about 2–5 seconds, and the next `curl` showed the new value. The support page was unchanged (same SHA-256), as were the redirect for other paths and the 405 for other methods. Worker logs stay off (`[observability] enabled = false`), as the privacy policy promises. Released apps don't fetch the route yet, so no user saw any of this.

## Privacy policy

The Game Deals policy is `https://bam.pm/privacy_policy.txt`, served by DuoPoint's `bam-pm-links` Pages site. Every deploy of that site replaces all of it.

The updated text is `docs/winter-sale/privacy_policy.txt` in this repository. It adds the event file host (section 4), the on-device event data (section 3), and the sale reminder (section 6). On 3 November 2026 the live policy matched DuoPoint's `tools/site/static/privacy_policy.txt` byte for byte, and that is the base the draft was made from.

1. Fetch the live policy again and diff it against the October 2 text; if it changed, merge those changes into the draft first.
2. Set the "Last updated" date to the publish date.
3. Copy the draft to DuoPoint `tools/site/static/privacy_policy.txt`, re-pin its digest in `tools/site/test_build.py`, and build the site as that README describes.
4. Upload the site from the Cloudflare dashboard (the usual zip upload), then check `curl -s https://bam.pm/privacy_policy.txt | grep gamedeals.bam.pm`.
5. Commit the DuoPoint change.

Store disclosures: the event request carries no user data and nothing new is collected, so the App Store privacy label, `PrivacyInfo.xcprivacy` and the Play Data safety form need no change.

## Rehearsal

### Testing on a phone after the app is public (test event file)

Once a release with the guide is public, switching the real document on shows the guide to everyone. To test without that:

1. Install a **debug build** on the phone (store builds never read the test file; the switch is ignored in release builds).
2. In the app: Account → Developer tools → Winter Sale guide → **Use the test event file**. Reopen the app.
3. Ask Claude to switch the test file on with the Cloudflare connector, with test dates (for example `visibleFrom` now, `saleStart` a few minutes ahead, `end` a day later). Only `winter-sale-2026-test.json` changes; the real module is uploaded unchanged.
4. Test the Home card, the guide, links, alerts on/off, the reminder and its tap. The test event has its own ID (`steam-winter-2026-test`) and its own cache, so it uses up nothing of the real event's one reminder.
5. Switch the test file off again, and turn the developer switch off. Reading the real document again replaces the test reminder record and cancels any pending test reminder.

Check: `curl -sSI https://gamedeals.bam.pm/events/winter-sale-2026-test.json` returns `200` and `no-store`.

### Android with a local event file

A debug build can read the event document from a local Worker and install beside the store app, so the store app's data is untouched:

```bash
# Terminal 1: a copy of store/site with test dates in its winter-sale-2026.json, then
npx wrangler@4 dev --port 8787 --ip 127.0.0.1

# Terminal 2
./gradlew :app:assembleDebug -Pgamedeals.winterSaleEventBaseUrl=http://127.0.0.1:8787 -Pgamedeals.debugApplicationIdSuffix=.rehearsal
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8787 tcp:8787
```

The debug build allows plain HTTP to `127.0.0.1` only. The worktree needs a `local.properties` with the ITAD and IGDB keys.

To make the reminder fire within minutes: open the app with `saleStart` a few minutes ahead, read the chosen offset with
`adb shell run-as pm.bam.gamedeals.rehearsal cat shared_prefs/gamedeals_common_storage.xml | grep winter_sale_reminder_v1`,
set `saleStart` to now + 4 minutes − that offset, and open the app again. Do not force-stop the app afterwards: Android cancels a force-stopped app's scheduled work.

Remove it afterwards with `adb uninstall pm.bam.gamedeals.rehearsal`.

### Release candidates (internal track and TestFlight)

Release builds always read the production route. Before the store release goes live, the released app does not request the document, so the production document can briefly carry test dates:

1. Deploy a document with `enabled: true`, `visibleFrom` in the past and `saleStart` about 10 minutes ahead.
2. Check on both phones: Home entry and pre-sale copy; open the guide; offer links open the store; launcher labels; leave and return within 15 minutes (no reload) and after 15 minutes (reload); pull to refresh.
3. At `saleStart`, the open guide switches to sale copy. With alerts on, a reminder arrives 1–3 hours later.
4. Deploy `enabled: false`: the Home entry and guide go away after the next check.
5. Restore the real dates with `enabled: false` and deploy.
6. Clear the app's data on the rehearsal phones (or reinstall). The reminder is shown once per event, and the rehearsal used the real event ID.

## Monitoring through the sale

- Sentry: new issues in the guide, the event check or the reminder job. A bad event document (invalid, or no `Date` header) raises one issue per app run with the message "Winter Sale event document is invalid" or "…has no usable Date header"; that means the Worker is serving the wrong thing. Offline and timeout failures only appear as breadcrumbs.
- Cloudflare: the Worker's request graph for `gamedeals-site`. Expect a jump at release and at soft launch; errors should stay near zero.
- ITAD: API usage around 17 December. Each guide refresh makes one batched price pass; series lookups are capped per opening. An open guide waits a random 0–10 minutes after 18:00 UTC before reloading prices, and a pull within 30 seconds of the last load is ignored.

## Accepted limitations

- Delivery of the reminder is best-effort: Android may delay it under battery saving, and an iOS reminder fires without re-checking the event.
- ITAD's overview endpoint picks one offer when two shops tie on price, so the shop shown for a tie can change between refreshes.
- Series results depend on IGDB classification; games with an unknown type or theme are left out when adult titles are off.
