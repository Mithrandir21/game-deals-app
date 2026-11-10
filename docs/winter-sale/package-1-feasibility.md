# Winter Sale guide — Package 1 feasibility record

**Date:** 3 November 2026
**Branch:** `feat/winter-sale-guide`, created from freshly fetched `origin/dev` at `4af6c62c`
**Worktree:** `/home/bam/REPOS/PRIVATE/GameDeals-winter-sale-guide`
**Status:** Package 1 complete; pause before Package 2
**Requirements:** `/home/bam/Documents/GameDeals-Winter-Sale-Handover-Revised-V6.md`
**Execution tracker:** `/home/bam/Documents/GameDeals-Winter-Sale-Execution-Plan.md`

This is a feasibility and implementation-choice record, not a change to ITAD data or to the app's existing screens. The ITAD and IGDB probes below were read-only and used credentials already configured for this app. No credential or raw API response is stored in the branch.

## Decisions for the build

| Question | Package 1 choice | Reason and later check |
|---|---|---|
| Price endpoint (V6 R18) | Use `POST /games/overview/v2` with the selected country and explicit voucher inclusion, in batches of at most 200 IDs. Read the `prices` array, use `current` as the one selected offer and `lowest.price` for the historical-low test. Ignore `bundles`. | In the live 200-game UK sample, every `current.price.amount` matched the minimum voucher-inclusive `/games/prices/v3` offer, and every `lowest.price.amount` matched `historyLow.all.amount`. Overview was about half the response size. Confirm in endpoint contract tests in Package 3. |
| Fallback | Use `/games/prices/v3` with `vouchers=true`, `deals` omitted, and `capacity` omitted. Select the minimum amount among same-currency offers and keep all fields from that offer. | Omitting `vouchers=true` changed the cheapest amount for 20 of the 200 sampled games. The fallback must include voucher prices to remain equivalent to Overview's default behaviour. |
| Batch size | 200 IDs for new guide repricing calls. Keep the existing waitlist's 100-ID refresh chunks unchanged. | Both live price endpoints accepted 200 IDs. The existing waitlist snapshot helper's 100-ID chunks are separate from the new guide lookup. |
| Popular candidate size (V6 R21) | Start with 100 Game-only `/deals/v2` candidates, then apply local collection/waitlist/ignored exclusions. A shorter final section is acceptable and must have an honest state. | This avoids adding a signed-in variant of the existing API-key Deals client for the first release. Measure the surviving section size during the soft launch; change this technical default only with evidence. |
| One-call Popular idea (V6 R19) | Keep the documented Deals List candidate call plus the Overview repricing call. Do not adopt the `nondeals=true`/`cut>=1` shortcut now. | Its filter order is undocumented, and the candidate feed alone cannot establish the lowest-offer rule. The shared price batches are needed for Waitlist and Series in any case. This can be re-tested later without blocking delivery. |
| Equal-price ties (V6 R20) | Use a stable game-card sort key after historical-low status and discount, ending in game ID. Use the single offer selected by ITAD Overview; keep the screen state fixed between refreshes. | The sample contained 16 games with multiple equally cheap full-list offers. Overview exposes only one chosen offer, so it cannot enforce a separate app-owned shop tie-break. If changing tied shops is observed during refreshes and is unacceptable, switch the guide to full prices or add a targeted solution. This is a stated limitation of the lighter endpoint. |
| Launcher mapping | Start from observed ITAD DRM IDs/names, and map only recognized values. | A blank DRM array is unknown, not proof of DRM-free activation. Package 3 should preserve unknowns instead of guessing. |

The owner decisions in V6 remain intact: every displayed game's **cheapest current offer must itself have a positive cut**; a candidate with a discounted offer elsewhere but a cheaper non-discounted `current` offer is omitted. No hand-picks, DLC suggestions, or new analytics were added by these choices.

## Live ITAD evidence

On 3 November, a read-only probe requested 200 UK (`GB`) Game-only, non-adult candidates ranked through `/deals/v2`, then requested those same IDs through both price endpoints. Timings below are one local observation, not a latency guarantee.

| Response | Items | Bytes | Elapsed |
|---|---:|---:|---:|
| Deals List candidate feed | 200 | 279,665 | ~0.7–0.9 s |
| Games Overview | 200 | 627,094 | ~0.5 s |
| Full prices with vouchers | 200 | 1,207,423 | ~0.7 s |

Overview's JSON is `{ "prices": [...], "bundles": [...] }`. Each `prices` item has an ID, `current`, `lowest`, and other fields. `current` included `shop`, `price`, `regular`, `cut`, `voucher`, `flag`, `drm`, `platforms`, `timestamp`, `expiry`, and `url`; `lowest` included its own price. The voucher field was a string where present. No sampled game lacked `current` or `lowest`, but the implementation must still handle absence.

With `/games/prices/v3?vouchers=true`, **200/200** Overview current amounts matched the minimum full-list amount and **200/200** Overview historical lows matched the full-list all-time low. Without voucher inclusion on the full-price call, only **180/200** minimum amounts matched. Twenty Overview current offers had a voucher. This difference is endpoint parameter behaviour, not an assertion that either provider response is erroneous.

Two of the Deals List candidates had a positive sale cut in the candidate feed but a zero-cut cheapest Overview offer. They are concrete fixtures for V6's strict inclusion rule: the guide must omit them. Sixteen of the 200 games had equal-minimum full-price offers from more than one shop; the tie choice above records what Overview can and cannot enforce.

Observed nonempty DRM values were Epic (16), GOG (35), Microsoft Store (48), Steam (61), Ubisoft Store (62), Rockstar (501), and DRM-free (1000). These are sample values, not a complete universal catalogue. The implementation should use known mappings and leave others unlabelled or marked unknown.

The official [ITAD API documentation](https://docs.isthereanydeal.com/) describes the endpoint contracts; the live probe above established the app's actual response shape and voucher comparison for this date. The API key remains only in the existing local configuration.

### Request budget chosen for Package 3

Per successful guide refresh, one Deals List request brings up to 100 Popular candidates. The deduplicated repricing input is at most 30 displayed Waitlist games + 300 completed Series games + 100 Popular candidates = **430 IDs**, hence at most **three Overview requests** at 200 IDs each. With the original 60-candidate limit, the total would be at most 390 IDs and two price requests. Actual overlap reduces the count. A 15-minute return to the same guide state makes no guide-price request under owner decision D15.

The separate existing Waitlist snapshot refresh can add `ceil(waitlist size / 100)` full-price calls when its region, age, or sale-start rules require it. Cold Series mapping can add the V6-bounded ITAD lookups and IGDB calls. This budget is per refresh, not per game card.

## IGDB, event timing, and infrastructure

- A live IGDB query for the Dark Souls franchise returned 26 games, all carrying a `themes` field when `themes.id` was requested. A live theme lookup returned ID **42 = Erotic**. The existing franchise query does not yet request `themes.id`; that addition belongs in Package 5. [IGDB API documentation](https://api-docs.igdb.com/).
- [Steamworks' Winter Sale 2026 page](https://partner.steamgames.com/doc/marketing/upcoming_events/2026_winter_sale) says the event runs **17 December 2026 to 4 January 2027 at 10:00 Pacific**. In December and January that is **18:00 UTC**. The proposed `saleStart` and `end` instants in V6 are therefore confirmed by the organizer's published schedule as of this check.
- Cloudflare access was confirmed read-only through the connected account: the `gamedeals-site` Worker exists. Its source is in the main checkout's untracked `store/site/`; that folder is absent from the clean development branch. Package 2 must inspect and bring across the relevant site files without copying unrelated untracked App Store work. The current Worker source redirects unknown paths, so the event JSON route is new work.
- The privacy policy source is in the separate DuoPoint repository at `tools/site/static/privacy_policy.txt`. The event-host policy update belongs in Package 8. Do not deploy the whole DuoPoint site as a side effect of GameDeals work without checking its independent state.
- This machine is Linux and has no local `xcodebuild`. The Bitrise account's GameDeals Android and iOS release workflows both succeeded on **28 October 2026** for v1.5.6, so a recent macOS release route exists. Direct Mac device-session availability and **December Bitrise credit balance** were not visible in the available read-only checks; reconfirm before the release rehearsal and store submission. This is a calendar risk, not a blocker to Package 2.

## Repository integration points

- ITAD candidate request and Game-only filter: `remote/itad/.../api/ItadDealsApi.kt`, `remote/itad/.../models/RemoteItadDealsRequest.kt`, and `remote/itad/.../ItadSourceImpl.kt`. The existing client uses an API key and maps game-like types 1/2/3 by default; Package 4 needs type 1 for Popular.
- Existing full-price endpoint and response models: `remote/itad/.../api/ItadGamesApi.kt` and `remote/itad/.../models/RemoteItadModels.kt`. A new Overview call and models belong in Package 3.
- Waitlist snapshot and its 100-ID chunks: `domain/.../repositories/waitlist/WaitlistRepository.kt`.
- Followed-series check and Steam-ID bridge: `domain/.../repositories/franchise/FollowedFranchiseChecker.kt`, `domain/.../repositories/games/GamesRepository.kt`, and `remote/igdb/.../api/IgdbGamesApi.kt`.
- Home entry/navigation: `feature/home/.../ui/` and `feature/home/.../navigation/HomeNavigation.kt`.
- Existing alert lifecycle and platform schedulers: `domain/.../scheduling/NotificationLifecycle.kt`, `AndroidNotificationScheduler.kt`, and `IosNotificationScheduler.kt`.

## Completion and next package

Package 1's endpoint, batch, event-date, IGDB-field, branch-baseline, and infrastructure checks are recorded above. No production code was changed. The only remaining external check is current December release capacity; it has a safe operational follow-up before Package 8 and does not require guessing Package 2's event contract.

**Next, only when asked to continue:** Package 2 creates the hosted event document route and app-side off-switch service. Begin by reviewing the untracked `store/site/` source, then add schema validation, `no-store`, the 12-hour fail-closed local fallback, and event-state tests. Pause again when Package 2's acceptance checks pass.
