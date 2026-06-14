# ADR 0002 — Unified Game Page: sourcing the three "stretch" blocks

- **Status:** Accepted
- **Date:** 2026-06-14
- **Deciders:** project owner
- **Epic:** [#291](https://github.com/Mithrandir21/game-deals-android-app/issues/291)
- **Spike:** [#292](https://github.com/Mithrandir21/game-deals-android-app/issues/292)

## Context

The Unified Game Page epic (#291) faithfully mirrors ITAD's web Game Page. Three blocks in that layout
are **not** available from the app's existing data sources (ITAD public API + IGDB) and were flagged to
"attempt only after a sourcing spike":

1. **Player-count history graph** (the trend chart under *Players*).
2. **"Included in subscription / Game Pass"** banner.
3. **"Found in packages"** (Steam store packages, distinct from ITAD bundles).

This ADR records what each would actually require and the go/no-go for Phase 9 (#301). Twitch streaming
was already ruled out of scope.

> Note on ADR 0001's "Bundles / subscriptions: yes" row: that referred to ITAD *knowing about* subscription
> shops/sources at the platform level, not a queryable **per-game subscription flag** on the public
> `/games/prices|overview|info` endpoints. Confirmed absent from the public API (twice, against
> `docs.isthereanydeal.com`).

## Findings

### 1. Player-count history graph — **NO-GO (official)**

- ITAD's historical-players endpoint is **internal-only** (not in the public API).
- Steam's official `ISteamUserStats/GetNumberOfCurrentPlayers` returns the **current** count only — Steam
  keeps **no** historical concurrent-player series.
- Historical series exist only on **SteamDB / SteamCharts**, which expose **no public API** and prohibit
  scraping. Community trackers all **self-record** snapshots over time.
- We already get current counts (`recent / day / week / peak`) from ITAD `/games/info/v2.players` — wired
  in Phase 6 as a small stat card.

**Conclusion:** no maintainable source for the *graph*. The only honest path to a real trend chart is to
**self-record** player snapshots over time (we already run a WorkManager poll for notifications — it could
stamp counts into a small Room table), which is a feature in its own right, not a Phase-9 add-on.

### 2. Included in subscription / Game Pass — **NO-GO**

- No official Microsoft Game Pass catalog API. `displaycatalog.mp.microsoft.com` /
  `catalog.gamepass.com` are undocumented; community lists (e.g. `NikkelM/Game-Pass-API`) self-describe as
  unofficial, region-fragmented, and "not guaranteed accurate".
- EA Play / PS Plus / Ubisoft+ have no official per-game catalog APIs either; a faithful banner would mean
  maintaining several fragile unofficial feeds across regions.
- ITAD public API exposes no per-game subscription flag.

**Conclusion:** no maintainable, non-fragile source. Not worth the multi-service, multi-region maintenance
burden for one banner.

### 3. Found in packages — **CONDITIONAL / LOW ROI → defer**

- Steam Store `appdetails` (`store.steampowered.com/api/appdetails?appids=`) returns a real
  `package_groups` field (undocumented but stable, no key, ~rate-limited). Technically feasible as a new
  best-effort Steam-store source.
- But: only meaningful for games **with a Steam appid**, adds a brand-new Steam-store dependency, and
  largely **overlaps the ITAD "Found in bundles" section** already being added in Phase 6. Steam "packages"
  are mostly edition/standard-vs-deluxe groupings — modest user value.

**Conclusion:** feasible but low ROI and redundant with bundles; **defer** unless it proves valuable after
Phase 6 ships.

## Decision

| Block                         | Verdict      | Phase 9 action                                              |
| ----------------------------- | ------------ | ----------------------------------------------------------- |
| Player-count **history graph**| **No-go**    | Ship counts-only (Phase 6). Backlog a separate "self-recorded player history" feature if wanted. |
| Subscription / **Game Pass**  | **No-go**    | Drop from the redesign. Revisit only if an official API appears. |
| **Found in packages**         | **Defer**    | Not built now; reconsider after Phase 6 bundles ship. |

Net effect: **Phase 9 (#301) carries no committed work.** The faithful redesign ships everything that has a
maintainable source; the three un-sourceable blocks are dropped or backlogged rather than built on
fragile/scraped feeds.

## Consequences

- Phase 9 (#301) is closed as **won't-do (no maintainable source)**; the player-counts card (Phase 6,
  #298) is the substitute for the player section.
- A future, independent feature could add a **self-recorded** player-count history chart by reusing the
  existing background poll — tracked separately from this epic.

## Sources

- ITAD API docs — `docs.isthereanydeal.com` (player history & subscriptions internal/absent).
- Steamworks `ISteamUserStats` — `GetNumberOfCurrentPlayers` (current only); Steam keeps no history.
- SteamDB / SteamCharts — historical series, no public API, no scraping.
- Xbox: `displaycatalog.mp.microsoft.com`, `catalog.gamepass.com`, `NikkelM/Game-Pass-API` (all unofficial).
- Steam Store `appdetails` `package_groups` (undocumented but real).
