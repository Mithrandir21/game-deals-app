# ITAD caching strategy

**Status:** In progress — **Phase 0 shipped** (#261); Phases 1–8 to follow. This document is the agreed strategy, built in phases.
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
| Deal details | (source) | **volatile-transact** | **2h** | `(dealId, country)` | new `DealDetailsCache` | **fresh-blocking** |
| Game details | (source) | **volatile-transact** | **2h** | `(gameId, country)` | new `GameDetailsCache` | **fresh-blocking** |
| Current prices | `/games/prices/v3` | volatile | **2h** (single) | `(gameId, country, shops)` | new `GamePrice` | fresh-blocking (transact) / SWR (list) |
| Price history | `/games/history/v2` | append-only | **incremental**, sweep at 30d | `(gameId, country)` | new `PriceHistoryPoint` | SWR |
| Game info | `/games/info/v2` | metadata | **7d** | `(gameId)` | `Game` table (+`expires`) | SWR |
| appid→UUID lookup | `/games/lookup`, `/games/search` | identity | **long-TTL** (not literal) | `(appid)` / `(normalizedTitle)` | new `GameIdMapping` | long-lived; **misses not cached** |
| Free-text search | `/games/search` | query | **in-memory, short** (or none) | `(query)` | in-memory only | n/a |
| Stats rankings | `/stats/*` (+`/games/prices/v3`) | curated feed | **12h** | `(rankingType, country)` | new `RankedGame` | SWR |
| Bundles | `/bundles/v1` | curated feed | **12h** | `(country)` | new `Bundle*` tables | SWR |
| Waitlist | `/waitlist/games/v1` | user-specific | login-scoped (not TTL) | `(userScope)` | new `WaitlistEntry` | refresh on launch/login |
| Collection | `/collection/games/v1` | user-specific | login-scoped (not TTL) | `(userScope)` | new `CollectionEntry` | refresh on launch/login |
| User info | `/user/info/v2` | user-specific | session/short | `(userScope)` | optional | refresh on login |

### 4.1 The all-stores deals page (special case)

`DealsRepository.getDeals(query)` is deliberately **not** Room-cached — results are paged, sorted,
filtered and region-sensitive, and persisting them would collide with the store-scoped `Deal` table. It keeps
that property. Its only caching benefits are the **cross-cutting** ones: in-flight de-duplication and 429
backoff. (If profiling later shows the first page is hot, an in-memory first-page cache keyed by
`(query, country)` can be added without touching Room.)

### 4.2 Transact vs list price reads

`/games/prices/v3` serves two callers. A `GamePrice` row carries exactly one `expires` (stamped at write
time), so two TTLs on one row is unmodelable — both callers share a **single 2h TTL** and differ only in
**staleness UX**, not freshness:
- **Transact** (deal/game detail open): **fresh-blocking** — never show a dead price on the surface the user
  is about to click through from. On a warm cache a failed refresh falls back to stale (D7); on a cold/empty
  cache the path **fast-fails** instead of spinning through the full 429 backoff (see §6).
- **List enrichment** (e.g. stats rankings): **SWR** — the list paints from cache immediately and updates in
  place.

The single 2h TTL means list-enrich refetches prices every 2h (rather than a longer feed tier), so the stats
path's call volume is higher — the concurrency limiter in §7 matters *more*, not less.

`RankedGame` (and any feed that shows prices) **references the `GamePrice` cache rather than snapshotting
prices into its own rows**, so the 12h feed TTL and the 2h price TTL can't diverge into two prices for one
game.

---

## 5. Region as a cache dimension (D5)

Prices, deals, stats and bundles all vary by `country`. Moving from clear-on-region-change to
`(resource × country)` keying means:

- Add a **`country` column** to every region-scoped entity (`Deal`, `Store`, `GamePrice`,
  `PriceHistoryPoint`, `RankedGame`, `Bundle`), part of the primary key / read predicate.
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

**Concurrency limiter (Phase 5):** a lightweight client-side semaphore bounding simultaneous ITAD calls —
cheaper than a full budget and enough to cap the stats per-game price fan-out (the heaviest path). Pull it in
under Phase 5, or gate Phase 5 on it; its value rose now that prices use a single 2h TTL. If `shops` joins the
price key, it must also join the **coalescer** key, or two different-`shops` refreshes collide into one call.

**Deferred:** a global per-interval request budget/throttle. Revisit if coalescing + the limiter + 429 backoff
prove insufficient under real traffic (e.g. Home loading deals + stats + bundles concurrently on cold start).

---

## 8. User-specific data (D8)

Waitlist & Collection move from in-memory `StateFlow` to Room, with **remote as source of truth**:

- New `WaitlistEntry` / `CollectionEntry` tables (id set + minimal display fields) so the heart/collected state
  is populated **instantly on cold start** and readable offline — no empty-then-fill flicker.
- Writes stay **optimistic**: update Room locally, push to ITAD, reconcile with a remote refresh on
  launch/login. A push that fails offline must enqueue to a **pending-writes outbox** replayed *before* the
  reconciling refresh — otherwise remote-as-truth silently reverts the user's edit and the heart-tap is lost
  with no signal. (Today's code is remote-first and sidesteps this; the optimistic flip introduces it.)
- **Not TTL-cached.** Invalidation is login-scoped: a fresh login refreshes from remote; logout clears the
  tables. (These are the only caches keyed to identity rather than `country`.)
- Keeps the existing `WaitlistRepository` / `CollectionRepository` public shape; only the backing store changes
  (StateFlow → DAO-backed Flow).

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

---

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
| **1** | Gate `Games` / `Releases` / `Giveaways` behind TTL (add `expires` to `Game`; stop refetch-on-every-subscribe). | Biggest call-count win for the least work. | `Game.expires` |
| **2** | Region dimension: add `country` to region-scoped entities; switch to `(resource × country)` reads; retire #212 clear-on-change. **Backfill current region** on migration (not treat-as-expired). | Unblocks correct multi-region caching for later phases. | Yes |
| **3** | Per-game **prices** + **deal/game details** caches (transact tier, fresh-blocking). | The money-facing surfaces; needs Phase 2's `country`. | New tables |
| **4** | **Price history** incremental cache (`since`-based top-up + 30d retention). | High-value, append-only; independent of the price caches. | New table |
| **5** | **Stats rankings** + **bundles** caches (feed tier, 12h, SWR), **plus a client-side concurrency limiter** over ITAD calls. | Stats' per-game price enrichment is the heaviest path; the limiter caps its fan-out (more important now prices use a single 2h TTL). | New tables |
| **6** | appid→UUID **long-TTL mapping** table (misses not cached). | Removes a repeated lookup on the Steam-bridge path. | New table |
| **7** | Persist **Waitlist/Collection** to Room (remote-as-truth, optimistic writes). | UX polish (no cold-start flicker) + offline read. | New tables |
| **8** | **Eviction sweep** on launch + `cacheSchemaVersion`. | Bounds growth once the new tables exist. | No |

---

## 12. Open questions / risks

- **`shops` in the price key.** `/games/prices/v3` takes a `shops` filter; if the app ever requests subsets,
  `shops` must join the cache key or caches will cross-contaminate — and then it must also join the
  **coalescer** key (§7), or two different-`shops` refreshes collide into one shared call. Today calls appear
  unfiltered — confirm before keying.
- **Stores TTL bump (8h→7d).** Low risk (shops are static), but a new store/logo would take up to 7d to
  appear. Acceptable; flagged for sign-off.
- **History retention vs the chart.** 30d retention means a rarely-opened game re-downloads its series; fine
  given `since` makes the top-up cheap, but worth confirming the chart's UX expectation.
- **Migration of existing cached rows on the `country` change (Phase 2).** **Resolved: backfill the current
  region** — treat-as-expired causes a first-launch refetch storm (§5). Use the computed-default / `ADD COLUMN`
  with SQL default pattern from the v8→v10 deal-flag migrations.
- **ADR.** D1 (backbone) and D5 (region keying supersedes #212) are arguably ADR-worthy; consider splitting a
  `docs/adr/0002-itad-caching.md` capturing those two rationales.
