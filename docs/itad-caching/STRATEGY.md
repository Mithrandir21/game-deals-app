# ITAD caching strategy

**Status:** **Complete — Phases 0–8 shipped** (#261, #262, #263, #264, #265, #266, #267, #268, #269). Two items are **deferred by design**, to be picked up on their own merits: Phase 7's **7b** (optimistic writes + pending-writes outbox, for offline waitlisting) and D9's **negative-result caching**. The standalone per-game `GamePrice` cache (originally Phase 3, then carried to Phase 5) was **retired**: the transact price already lives inside the #264 detail blobs, and its only other reader — stats rankings — now **snapshots** the price into its 12h feed blob (Phase 5 decision), so a separate 2h price table has no caller and only the avoided 12h↔2h divergence as a rationale, which rankings accept. This document is the agreed strategy, built in phases.
**Scope:** Caching of IsThereAnyDeal (ITAD) data across the app. Sibling sources (IGDB releases,
GamerPower giveaways) are touched only where they share the same `CachedResource` seam.
**Related:** deal-source migration [`docs/deal-source-migration/HANDOVER.md`](../deal-source-migration/HANDOVER.md) ·
ADR [`docs/adr/0001-deal-source-itad.md`](../adr/0001-deal-source-itad.md)

---

## 1. Goals & constraints

1. **Fewer network calls.** ITAD rate-limits and its ToS allows revocation without notice — every avoidable
   request matters.
2. **Show data faster.** Reads should resolve from local storage instantly wherever correctness allows.
3. **Stay correct on price.** Prices/deals are the volatile, money-facing data; staleness here has real UX
   cost (a user clicking through to a dead sale).

ITAD itself defines **no** caching semantics (no `Cache-Control`, no documented TTLs), so the app owns the
entire policy — what to cache, for how long, and how to invalidate.

---

## 2. Decisions (resolved)

| # | Decision | Choice |
|---|----------|--------|
| D1 | Backbone | **Extend the existing `CachedResource` + Room `expires` pattern** as the single mechanism, applied uniformly. Persistent across restarts, unit-testable, no new caching concept. |
| D2 | Price/deal TTL | **Tiered by surface** — transact ≠ browse ≠ feed (see §4). |
| D3 | Metadata TTL | **7 days**; identity mappings (appid→UUID) **long-TTL**, not literally permanent (ITAD UUIDs can merge — guard with `cacheSchemaVersion`); never permanently cache search *misses*. |
| D4 | Price history | **Incremental** — persist the series, top-up with the `since` parameter; long TTL. |
| D5 | Region | **Key caches by `(resource × country)`** — add a `country` column; data for multiple regions coexists. Replaces the #212 clear-on-region-change. |
| D6 | Staleness UX | **Hybrid** — stale-while-revalidate for browse lists/feeds; fresh-blocking for the transact surface (deal/game details). |
| D7 | Rate-limit resilience | **Serve-stale-on-error** + **in-flight de-duplication** + **429 backoff honoring `Retry-After`**. (No global request budget for now.) |
| D8 | User-specific data | **Persist Waitlist/Collection to Room**, remote-as-truth, refreshed on launch/login. Not TTL-cached. |
| D9 | Eviction | **Launch sweep that keeps the latest row per key** (never deletes the last row serve-stale / offline reads rely on) + **negative-result caching**; price history gets its own retention rule. |
| D10 | Prefetch | **Lazy only** — no speculative calls; the cache makes the *second* view instant. |

---

## 3. Architecture

### 3.1 Current state (baseline)

- A mature, Flow-agnostic, Room-free seam already exists:
  [`CachedResource<T>`](../../domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/cache/CachedResource.kt)
  (`clock` + `read` + `expiresAtMillis` + `refresh`; `refreshIfNeeded(force)`; stale = empty OR any entry expired).
- Wired today only for **Store deals** (`DealsRepository`, 8h) and the **Stores list** (`StoresRepository`, 8h),
  via an `expires: Long` column stamped as `clock.nowMillis() + TTL` on insert.
- **Under-cached:** `Games`, `Releases`, `Giveaways` sit in Room tables but refetch on **every** `observe*`
  subscribe (`onStart { refresh }` / `replaceAll`) with **no TTL gate**.
- **Uncached:** all-stores deals page, deal details, game details, **price history**, **stats rankings**
  (which additionally enrich per-game prices via `/games/prices/v3` — the most expensive path), **bundles**,
  search/lookup, game info.
- **User-specific:** Waitlist/Collection are an in-memory `MutableStateFlow<Set<String>>` — empty on cold
  start until a remote refresh lands.

### 3.2 Target shape

Keep `CachedResource` as the one read-through primitive, and add three cross-cutting capabilities around it:

```
            ┌──────────────────────────── repository ─────────────────────────────┐
  observe*/ │   CachedResource<T>(clock, read, expiresAtMillis, refresh)          │
  suspend → │     • refreshIfNeeded(force)                                        │
  read      │     • serve-stale-on-error  (D7)   ← new behaviour in the seam      │
            │     • coalesced refresh     (D7)   ← RequestCoalescer keyed by      │
            │                                       (resource, key, country)      │
            └─────────────────────────────────┬───────────────────────────────────┘
                                              │ refresh()
                                ┌─────────────▼───────────────────┐
   Room (durable L2) ◀──────────│   *Source → ItadHttpClient      │
   per-resource tables,         │     • 429 backoff + Retry-After │  (D7, client layer)
   `expires` + `country` cols   └─────────────────────────────────┘
```

Three additions, smallest blast radius first:

1. **`serve-stale-on-error` in `CachedResource`.** `refreshIfNeeded` wraps `refresh()`; if it throws and the
   cache is non-empty (even if expired), swallow and keep the cached data. A `force=true` caller (explicit
   pull-to-refresh) may opt to surface the error. Keeps the resilience logic in the one tested seam.
2. **`RequestCoalescer`** — a small keyed-`Deferred` map so concurrent identical refreshes (same
   resource + key + country) share one in-flight network call. Pure Kotlin, lives next to `CachedResource`.
3. **429 backoff** at the Ktor layer
   ([`ItadHttpClient`](../../remote/itad/src/commonMain/kotlin/pm/bam/gamedeals/remote/itad/logic/ItadHttpClient.kt)) —
   `HttpRequestRetry` honoring `Retry-After`. Cross-cutting for **all** ITAD calls, cached or not.

`CachedResource` stays Android-free and `commonMain`; the three additions follow the same rule.

---

## 4. Per-resource policy matrix

TTLs are expressed against the existing `millisInHour` helper. (Today only `millisInHour` exists; add
`millisInDay = millisInHour * 24` for the metadata/feed tiers.)

| Resource | ITAD endpoint | Volatility class | TTL | Cache key | Storage | Staleness |
|----------|---------------|------------------|-----|-----------|---------|-----------|
| Store deals | `/deals/v2?shops=` | volatile-browse | **8h** (keep) | `(storeId, country)` | `Deal` table | SWR |
| Stores list | `/service/shops/v1` | metadata | **7d** (bump from 8h) | `(country)` | `Store` table | SWR |
| All-stores deals page | `/deals/v2` (paged) | volatile-browse, paged | **not persisted** | — | — | coalesce + 429 only (see §4.1) |
| Deal details | (source) | **volatile-transact** | **2h** | `(dealId, country)` | `DealDetailsCache` ✅ v13 (#264) | **fresh-blocking** |
| Game details | (source) | **volatile-transact** | **2h** | `(gameId, country)` | `GameDetailsCache` ✅ v13 (#264) | **fresh-blocking** |
| Current prices | `/games/prices/v3` | volatile | n/a | — | ~~`GamePrice`~~ **retired** — transact price is in the detail blobs above; rankings snapshot it (§4.2) | n/a |
| Price history | `/games/history/v2` | append-only | **incremental**, sweep at 30d | `(gameId, country)` | `PriceHistoryCache` ✅ v14 (#265) (blob, not per-point) | SWR |
| Game info | `/games/info/v2` | metadata | **7d** | `(gameId)` | `Game` table (+`expires`) | SWR |
| appid→UUID lookup | `/games/lookup` | identity | **30d** (long, not literal) | `(steamAppId)` | `GameIdMapping` ✅ v17 (#267) | long-lived; **misses not cached**; region-invariant (no `country`) |
| Free-text search | `/games/search` | query | **in-memory, short** (or none) | `(query)` | in-memory only | n/a |
| Stats rankings | `/stats/*` (+`/games/prices/v3`, +`/games/info/v2`) | curated feed | **12h** | `(rankingType, country)` | `StatsRankingsCache` ✅ v16 (#266) (blob: `List<RankedGame>` incl. snapshotted price + boxart) | read-through (12h) |
| Bundles | `/bundles/v1` | curated feed | **12h** | `(country)` | `BundlesCache` ✅ v15 (#266) (blob: `List<Bundle>`) | read-through (12h) |
| Waitlist | `/waitlist/games/v1` | user-specific | login-scoped (not TTL) | `(gameId)` | `WaitlistGameId` ✅ v18 (#268) (id set; display fields deferred) | refresh on launch/login |
| Collection | `/collection/games/v1` | user-specific | login-scoped (not TTL) | `(gameId)` | `CollectionGameId` ✅ v18 (#268) (id set; display fields deferred) | refresh on launch/login |
| User info | `/user/info/v2` | user-specific | session/short | `(userScope)` | optional | refresh on login |

### 4.1 The all-stores deals page (special case)

`DealsRepository.getDeals(query)` is deliberately **not** Room-cached — results are paged, sorted,
filtered and region-sensitive, and persisting them would collide with the store-scoped `Deal` table. It keeps
that property. Its only caching benefits are the **cross-cutting** ones: in-flight de-duplication and 429
backoff. (If profiling later shows the first page is hot, an in-memory first-page cache keyed by
`(query, country)` can be added without touching Room.)

### 4.2 Transact vs list price reads

`/games/prices/v3` serves two callers that differ in how fresh a price must be:
- **Transact** (deal/game detail open): **fresh-blocking** — never show a dead price on the surface the user
  is about to click through from. On a warm cache a failed refresh falls back to stale (D7); on a cold/empty
  cache the path **fast-fails** instead of spinning through the full 429 backoff (see §6).
- **List enrichment** (e.g. stats rankings): tolerant — a ranking tile can show a slightly stale price.

The original design gave both a shared 2h `GamePrice` cache; the **as-built decision below** instead lets the
transact surface own its price (inside the detail blobs) and lets rankings snapshot theirs into the 12h feed
blob — which retires `GamePrice` entirely.

The original plan was for `RankedGame` to **reference** a shared `GamePrice` cache rather than snapshot
prices into its own rows, so the 12h feed TTL and the 2h price TTL couldn't diverge into two prices for one
game.

**As built / decided:**
- **Transact** half — already covered. `/games/prices/v3` is not a domain-level resource but an enrichment
  call *inside* `ItadSourceImpl` when it assembles `DealDetails` / `GameDetails`, and those whole objects are
  blob-cached at the 2h transact TTL by #264.
- **List** half (Phase 5 decision) — stats rankings **snapshot** the enriched price into their 12h feed blob
  (`StatsRankingsCache`) instead of referencing a separate price cache. A ranking-tile price may therefore be
  up to 12h stale, which is acceptable off the transact surface; in exchange the heaviest cost (the per-game
  `/games/info/v2` boxart fan-out, see §7) is eliminated on the second load by the same blob.
- The standalone **`GamePrice` table is therefore retired** — with both halves covered, it has no caller, and
  building it would be speculative (D10). If a surface ever needs a *fresh* price independent of the detail
  blobs, revisit it then.

---

## 5. Region as a cache dimension (D5)

Prices, deals, stats and bundles all vary by `country`. Moving from clear-on-region-change to
`(resource × country)` keying means:

- Add a **`country` column** to every region-scoped entity (`Deal`, `Store`, `PriceHistoryCache`,
  `StatsRankingsCache`, `BundlesCache`), part of the primary key / read predicate. (The new cache tables
  bake `country` into their composite key from the start; `GamePrice` was retired — see §4.2.)
  - **As built (Phase 2):** only `Deal` is keyed — by `(storeID, country)` via an `ADD COLUMN`
    that keeps the `dealID` primary key (a refetch under another region overwrites the same
    `dealID`, so the cache self-heals rather than coexisting). `Store` is **deferred as
    region-invariant**: it is fetched globally (no `country` param) and was never region-cleared, so
    keying it would be an unverified behaviour change — revisit if shops prove per-country. The
    new region-scoped tables in Phases 3–5 are created **with** `country` from the start.
- Reads filter by the active country (from `RegionRepository`); switching region is then **instant** for any
  region previously fetched, and offline-friendly.
- `DealsRepository.clearCachedDeals()` (the #212 hook) is **no longer needed for correctness** — kept only as
  an optional "free the other regions' rows" maintenance call, or removed in favour of the launch sweep (§7).
- Requires a Room migration (`ADD COLUMN country`). **Backfill the current region** rather than treating
  pre-migration rows as already-expired: treat-as-expired forces every region-scoped table to refetch at once
  on the first post-update launch — a self-inflicted rate-limit storm, exactly when the global budget is still
  deferred. If a clear is ever unavoidable, refetch lazily on access, not eagerly on launch.

---

## 6. Staleness model (D6)

- **SWR (browse lists, feeds, metadata):** the DAO `Flow` emits cached rows immediately; a background refresh
  runs and Room change-tracking pushes the update. This is what `observe*` already does — extend it to the
  newly-cached feeds (stats, bundles) and gate the under-cached tables (games/releases/giveaways) behind TTL so
  they stop refetching on every subscribe.
- **Fresh-blocking (transact):** deal/game details await the refresh when stale, so a click-through can't show
  a dead price. On a **warm** cache a failed refresh falls back to the stale row (D7), so blocking stays
  unbounded by design. On a **cold/empty** cache there is no stale row to protect, so the empty-cache path
  **fast-fails** (a shorter retry/timeout budget than the warm path) and surfaces a retryable state rather than
  spinning through the full 429 backoff only to error.
- **Suspend read-paths** that currently `refresh-then-read` and block (e.g. `getStoreDeals`) should adopt the
  same hybrid rule: block only on the transact tier; elsewhere return cached and refresh in the background.

---

## 7. Rate-limit resilience (D7)

| Behaviour | Where it lives | Notes |
|-----------|----------------|-------|
| **Serve-stale-on-error** | `CachedResource.refreshIfNeeded` | On `refresh()` failure with a non-empty cache, keep cached data (even if expired). `force=true` callers may opt to surface the error for explicit pull-to-refresh. |
| **In-flight de-duplication** | `RequestCoalescer` (next to `CachedResource`) | Keyed by `(resource, key, country)`; concurrent identical refreshes await one shared `Deferred`. Prevents a list and its visible tiles each firing the same price call. |
| **429 backoff + `Retry-After`** | `ItadHttpClient` (Ktor `HttpRequestRetry`) | Exponential backoff that respects the server header. Cross-cutting for every ITAD call, cached or not. Maps cleanly onto the existing `RemoteExceptionTransformer`. |

**Concurrency limiter (Phase 5a):** a lightweight client-side semaphore bounding simultaneous ITAD calls —
cheaper than a full budget and enough to cap the heaviest path, the stats **per-game `/games/info` boxart
fan-out** (`ItadStatsSourceImpl` issues one info call per ranked game). Ships first within Phase 5 as a Ktor
plugin in `ItadHttpClient`, so it benefits even the cold/stale-refresh fetch that the 12h ranking blob can't
avoid. (The `GamePrice`/`shops` coalescer-key caveat is moot now that `GamePrice` is retired — §4.2.)

**Deferred:** a global per-interval request budget/throttle. Revisit if coalescing + the limiter + 429 backoff
prove insufficient under real traffic (e.g. Home loading deals + stats + bundles concurrently on cold start).

---

## 8. User-specific data (D8)

Waitlist & Collection move from in-memory `StateFlow` to Room, with **remote as source of truth**:

- New `WaitlistGameId` / `CollectionGameId` tables so the heart/collected state is populated **instantly on
  cold start** and readable offline — no empty-then-fill flicker. **As built (7a):** the **id set only**;
  display fields (title/boxart for an offline *list*) are deferred until a surface reads the list from Room
  rather than from `get*` (which fetches them from remote today).
- **Not TTL-cached.** Invalidation is login-scoped: a fresh login refreshes from remote; logout clears the
  tables. (These are the only caches keyed to identity rather than `country`.)
- Keeps the existing `WaitlistRepository` / `CollectionRepository` public shape; only the backing store changes
  (StateFlow → DAO-backed Flow).

**Split into 7a (shipping) and 7b (deferred):**
- **7a — Room persistence, remote-first writes.** The backing store change above, keeping today's
  **remote-first** writes (`toggle*` awaits the ITAD add/remove, *then* writes Room). Because Room therefore
  never holds an unconfirmed local edit, the remote-as-truth `replaceAll` refresh is **always safe** — no
  reconcile conflict, no outbox. Delivers the cold-start / offline-read UX win at low risk. Offline *writes*
  are not supported (an offline toggle fails, as today).
- **7b — optimistic writes + outbox (deferred).** Flip `toggle*` to Room-first (instant heart) and add a
  **pending-writes outbox** replayed *before* the reconciling refresh — otherwise remote-as-truth silently
  reverts the user's edit. This is the only part that enables **offline waitlisting**, and it is where all the
  reconcile-ordering correctness risk lives, so it is deferred and decided on its own merits. (Today's code is
  already remote-first and sidesteps the outbox — 7a keeps that property; only 7b's optimistic flip needs it.)

---

## 9. Eviction & retention (D9)

- **Launch sweep — keep the latest row per key.** A maintenance job (app-startup initializer) bounds growth,
  but **must not delete the last row a key has**: serve-stale-on-error (D7) and offline reads (§5) depend on a
  stale row surviving. So it keeps the newest row per cache key (dropping older duplicates / superseded
  regions) and leaves long-lived metadata (appid→UUID, game info within TTL) untouched — *not* a blanket
  `DELETE … WHERE expires < now`, which would erase exactly the fallback D7 relies on. Roughly bounds growth to
  recently-browsed games; no last-access tracking needed.
- **Negative results are cacheable.** `CachedResource` treats an *empty* read as always-stale, so a game with
  no current deals/prices in a region would refetch on every view (the common case). Persist a "checked,
  nothing here" marker carrying `fetchedAt` (or track per-key fetch time separately from row count) so an
  empty-but-fresh result is honored with a short TTL.
- **Price history retention:** the series is long-lived and append-only, so it isn't swept by the volatile
  rule. Give it its own retention — drop a game's series if not refreshed in **30 days** (it top-ups via
  `since` on next open). Optionally cap points per series if any single game's history is large.
- **Cache-format version:** a `cacheSchemaVersion` preference; bump it when the cache *shape* changes across
  an app update to force a one-time clear of volatile tables (Room migrations cover schema, this covers
  semantic format changes that aren't a column change).

**As built (Phase 8, #269):** `CacheMaintenance.runStartupMaintenance()` runs once per cold start (fire-and-forget
off the app's background scope). The format-version guard reads/writes `cacheSchemaVersion` via `Storage` and, on
a bump, clears the blob caches + `GameIdMapping`. The sweep is age-based per table — `expires < now − 7d` grace
for the detail/feed blobs (so recent serve-stale rows survive), `fetchedAt < now − 30d` for price history,
`expires < now` for the identity mappings. The "keep latest row per key / superseded regions" nuance is moot in
practice: every cache table is PK-keyed (one row per key), and a region switch's old-region rows fall out via the
same grace sweep once their short TTL lapses. **Negative-result caching remains deferred** (no phase assigned).

## 10. Cross-cutting

- **Force refresh:** `CachedResource.refreshIfNeeded(force = true)` already exists — wire pull-to-refresh on
  the relevant surfaces to it.
- **Observability:** the repos already `debug(logger) { "… refresh needed: $refreshed" }`. Extend to a simple
  hit/miss/coalesced/stale-served counter so the rate-limit win is measurable.
- **Cache keys:** game UUIDs (`String`), `storeID` (`Int`), `dealID` (`String`) are the natural keys; pair each
  region-scoped key with `country`.

---

## 11. Phased rollout

Ordered for early wins and minimal blast radius. Each phase is independently shippable.

| Phase | What | Why first / notes | Schema change |
|------:|------|-------------------|:-------------:|
| **0** | ✅ **Done (#261).** Cross-cutting foundation: serve-stale-on-error (`CachedResource`), `RequestCoalescer`, and 429/`Retry-After` (`ItadHttpClient`), all unit-tested. | Pure behaviour, no schema; immediately reduces wasted/duplicate calls everywhere. | No |
| **1** | ✅ **Done (#262).** Gate `Games` (7d) / `Releases` (24h) / `Giveaways` (12h) behind TTL via `CachedResource`; stop refetch-on-every-subscribe. | Biggest call-count win for the least work. | `Game`/`Release`/`Giveaway.expires` (v11) |
| **2** | ✅ **Done (#263).** Region dimension: `Deal` keyed by `(storeID, country)`; reads filter by the active region; #212 clear-on-change retired. **Store kept region-invariant** (fetched globally today); `ADD COLUMN` backfilling the default region. | Unblocks correct multi-region caching for later phases. | `Deal.country` (v12) |
| **3** | ✅ **Done (#264).** **deal/game details** caches: new `DealDetailsCache` / `GameDetailsCache` tables (v13), region-keyed `(id, country)`, 2h TTL, fresh-blocking via `CachedResource`. The per-game **`GamePrice`** cache from this issue was **retired** (§4.2) — the transact price is in the detail blobs and rankings snapshot theirs, so it has no caller. | The money-facing surfaces; needs Phase 2's `country`. | `DealDetailsCache` / `GameDetailsCache` (v13) |
| **4** | ✅ **Done (#265).** **Price history** incremental cache: new `PriceHistoryCache` table (v14), region-keyed `(gameId, country)`, full series stored as a blob; stale entries top-up incrementally via the source's new `since` bound and merge. Long SWR TTL (24h) + serve-stale-on-error; `fetchedAt` stored for the 30d retention sweep (the sweep job itself lands in Phase 8). | High-value, append-only; independent of the price caches. | `PriceHistoryCache` (v14) |
| **5** | ✅ **Done (#266)** — three sub-PRs: **(5a) concurrency limiter** — `ItadConcurrencyLimiter` Ktor `Semaphore` plugin in `ItadHttpClient` (default 5 in-flight), caps the per-game `/games/info` fan-out; no schema. **(5b) bundles cache** — `BundlesCache` blob (v15), 12h, keyed `(country)`; `getBundle(id)` resolves from the cached list. **(5c) stats rankings cache** — `StatsRankingsCache` blob (v16) of `List<RankedGame>` incl. snapshotted price + boxart, 12h, keyed `(rankingType, country)`. `GamePrice` retired (§4.2). Feeds use the suspend read-through pattern (cold blocks, fresh instant, stale refreshes, serve-stale-on-error), not Flow-SWR — Home reads them as suspend one-shots. | Stats' per-game `/games/info` boxart fan-out is the heaviest path; the limiter caps it and the 12h blob removes it on the 2nd load. | `BundlesCache` (v15), `StatsRankingsCache` (v16) |
| **6** | ✅ **Done (#267).** Steam-appID→ITAD-UUID **long-TTL mapping** in `findGameIdBySteamAppId`: new `GameIdMapping` table (v17), keyed `steamAppId` (region-invariant), 30d TTL. Genuine misses are **not** cached (D3); a lookup failure serves a stale mapping if present. `title` confirmed vestigial on this path (lookup is by appID via `/games/lookup`). | Removes a repeated lookup on the Steam-bridge path. | `GameIdMapping` (v17) |
| **7** | 🟡 **In progress (#268)** — split (§8): **✅ (7a) Room persistence, remote-first writes** — `WaitlistGameId` / `CollectionGameId` **id-set** tables (v18) behind a DAO Flow, **auth-gated** (the observed set is empty whenever logged out; a logged-out `get*` clears the rows). `observeIs*`/`observeIds` now correct **instantly on cold start + offline**; `get*` is the remote-as-truth `replaceAll` reconcile; `toggle*` stays **remote-first** (Room never holds an unconfirmed edit ⇒ refresh always safe ⇒ no outbox). Public API unchanged (no feature edits). Display fields + an offline *list* are deferred (the list screen still reads `get*` from remote, so storing title/boxart would be unread). **⬜ (7b, deferred)** optimistic writes + pending-writes outbox — enables offline waitlisting; carries the reconcile-ordering risk, decided later. | UX polish (no cold-start flicker) + offline read. | `WaitlistGameId` + `CollectionGameId` (v18) |
| **8** | ✅ **Done (#269).** `CacheMaintenance.runStartupMaintenance()` (fire-and-forget off the app's background scope, mirroring `warmDomainDatabase`): a **`cacheSchemaVersion`** guard (stored in `Storage`; on bump, clears the format-versioned ITAD caches + `GameIdMapping` per D3) and an **eviction sweep** — `PriceHistoryCache` 30d-by-`fetchedAt`, the detail/feed blobs by `expires < now − 7d` grace (keeps recent serve-stale rows), `GameIdMapping` by `expires < now`. User data + migration-covered column tables untouched. Negative-result caching (D9) deferred. | Bounds growth once the new tables exist. | No (version lives in `Storage`) |

---

## 12. Open questions / risks

- ~~**`shops` in the price key.**~~ **Resolved (moot).** This only mattered for the standalone `GamePrice`
  cache, which is retired (§4.2). Prices are now snapshotted into the detail blobs (transact) and the stats
  ranking blob (list), neither keyed by `shops`. Revisit only if a dedicated price cache is ever reintroduced.
- **Stores TTL bump (8h→7d).** Low risk (shops are static), but a new store/logo would take up to 7d to
  appear. Acceptable; flagged for sign-off.
- **History retention vs the chart.** 30d retention means a rarely-opened game re-downloads its series; fine
  given `since` makes the top-up cheap, but worth confirming the chart's UX expectation.
- **Migration of existing cached rows on the `country` change (Phase 2).** **Resolved: backfill the current
  region** — treat-as-expired causes a first-launch refetch storm (§5). Use the computed-default / `ADD COLUMN`
  with SQL default pattern from the v8→v10 deal-flag migrations.
- **ADR.** D1 (backbone) and D5 (region keying supersedes #212) are arguably ADR-worthy; consider splitting a
  `docs/adr/0002-itad-caching.md` capturing those two rationales.
