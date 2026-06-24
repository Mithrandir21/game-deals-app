# Caching system — design overview

*A self-contained explanation of how this application caches, invalidates, and refreshes data from its
upstream provider. Written to be readable without knowledge of the specific platform it runs on — the
concepts are portable to any client that sits in front of a rate-limited third-party API.*

---

## 1. Context & constraints

The application is a price-comparison client for video-game deals. Its data comes almost entirely from a
single third-party HTTP API (IsThereAnyDeal, "the provider"). That provider shapes every decision here:

- **It defines no caching semantics.** No `Cache-Control`, no `ETag`s, no documented freshness windows. The
  client owns the *entire* caching policy: what to store, for how long, and when to discard it.
- **It rate-limits aggressively and opaquely.** There is no published quota, and the terms of service allow
  access to be revoked without notice. Every avoidable request is a liability, and a burst of requests is a
  real risk — not just a performance concern.
- **Its data spans a wide volatility range.** Some of it is near-static (a game's title, the list of
  storefronts); some is moderately dynamic (curated "most-wishlisted" rankings); some is highly volatile and
  money-facing (the current sale price a user is about to click through to buy).

So the system is not "a cache" in the singular sense. It is a **policy layer** that applies different rules
to different classes of data, over a single shared mechanism.

## 2. Design goals

1. **Fewer upstream calls.** Both to stay inside the (unknown) rate limit and to respect the provider.
2. **Faster reads.** Resolve from local storage instantly wherever correctness allows.
3. **Correctness on price.** Prices are the volatile, money-facing data; showing a dead sale has real cost.
   Where these goals conflict, price correctness wins for the surfaces a user transacts from.

A recurring theme below is that these three goals are often in tension, and the system resolves the tension
*differently per data class* rather than with one global rule.

---

## 3. The core pattern: read-through caching

Every cached resource is served through one small, shared abstraction (internally called `CachedResource`).
It is deliberately tiny and storage-agnostic. Given three things —

- how to **read** the currently-cached copy,
- how to tell whether that copy is **expired**, and
- how to **refresh** it from upstream (fetch + write to the local store) —

…it decides whether a refresh is needed and runs it. The read path is always the same:

```
        read local store
              │
        is it present & fresh?
         ┌────┴─────┐
        yes         no
         │           │
     serve it    fetch upstream ──► write to local store ──► serve it
                     │
                 (on failure: see §6, serve-stale)
```

Two properties matter:

- **It is persistent.** The local store survives the app being closed and reopened, so a returning user sees
  data immediately on the next launch rather than waiting on the network. The cache's job is to make the
  *second* view of anything instant.
- **It is the single chokepoint.** Because all cached reads funnel through one abstraction, cross-cutting
  behaviour (expiry, serve-stale-on-error, refresh de-duplication) is implemented once and applies uniformly.

What freshness *means*, and how the data is stored, are parameters — covered next.

---

## 4. Freshness model: TTL tiers by volatility

Each resource is stamped with an **expiry timestamp** when written (`now + TTL`). A read treats the copy as
stale once the current time passes that stamp. The TTL is chosen by how volatile the data is and how costly
staleness would be — not one number, but a small set of tiers:

| Tier | TTL | Rationale |
|------|-----|-----------|
| **Transact** (details a user is about to act on) | **2 hours** | Money-facing; a stale price here is the worst outcome, so the window is short and reads *block* on a refresh when stale (§7). |
| **Browse / feed** (lists, rankings, bundles) | **8–12 hours** | Shown in bulk; a slightly old list is acceptable, and these are the highest-volume call paths. |
| **Append-only series** (price history) | **24 hours**, refreshed **incrementally** | The series only ever grows; a refresh fetches *only* the points newer than the last cached one rather than the whole history. |
| **Metadata** (game info) | **7 days** | Changes rarely. |
| **Identity mappings** (external id → provider id) | **30 days** | Effectively stable; see §6/§12 for why it is long but *not* permanent. |
| **User data** (saved items) | **no TTL** | Invalidated by login state, not by time (§8). |

Concrete values per resource are in the table in §10.

---

## 5. Storage shapes & cache keys

### Two storage shapes

The local store holds cached data in one of two shapes, chosen by how the data is consumed:

- **Normalised rows** — one column per field, for data that is **queried, filtered, or sorted** in the store
  itself (e.g. the deal lists, which are read back filtered by storefront and region).
- **Serialised blobs** — the whole object stored as one opaque serialised string, for data that is always
  **read and written as a single unit** (e.g. a deal's full detail object, a price-history series, a bundle
  list, a ranking). Blobs keep these caches trivially simple — one row in, one row out — at the cost of not
  being sub-queryable. They are used precisely where sub-querying isn't needed.

This is a deliberate, repeated trade-off: *normalise what you query, blob what you read whole.*

### Cache keys

Most data varies by **region** (country), because prices, availability, and currency differ per storefront
region. So most caches are keyed by **`(resource id, region)`** rather than by id alone. The important
consequence: **multiple regions coexist** in the cache. Switching region does not invalidate anything — it
simply reads a different key. Switching *back* is instant because the prior region's data is still there.

Key shapes in use:

- `(id, region)` — region-scoped per-item data (deal/game details, price history, store deals).
- `(region)` — region-scoped whole-list data (bundles).
- `(type, region)` — region-scoped feeds (each ranking kind).
- `(id)` only — **region-invariant** data (an external-id→provider-id mapping is the same everywhere).
- `(account)` — user data, scoped to the logged-in identity (§8).

---

## 6. Resilience: surviving a flaky, rate-limited upstream

Three mechanisms make the system robust against the provider being slow, rate-limiting, or briefly
unavailable. The first is the single most important policy in the whole design.

### Serve-stale-on-error (the central trade-off)

When a refresh fails, **the previously-cached copy is kept and served — even if it is expired** — rather than
propagating the error. The failure is only surfaced when there is genuinely nothing to fall back on (the
cache is empty), or when a caller explicitly opts in (e.g. an explicit pull-to-refresh that wants to show a
retry affordance while still displaying the old data).

The principle: for this upstream, **bounded staleness is almost always better than a hard failure.** A flaky
network or a rate-limit response degrades the experience to "last-known data" instead of an error screen.

### Refresh de-duplication (coalescing)

If several parts of the app request a refresh of the *same* resource at the same moment, they share a single
in-flight upstream call rather than each firing their own. This matters when, say, a list and the individual
items visible within it would otherwise each trigger the same fetch.

### Rate-limit handling — reactive *and* proactive

- **Reactive: backoff on "too many requests."** When the provider returns a 429 (rate-limit) response, the
  request is retried transparently with exponential backoff that honours the server's stated retry delay.
- **Proactive: a concurrency limiter.** A semaphore caps the number of upstream calls *in flight at once*.
  This is a throughput cap, not a per-interval rate limit — it prevents a burst (e.g. a screen that, on a
  cold load, would otherwise fan out one call per item across several lists) from spiking into a rate-limit
  response in the first place. Requests beyond the cap simply wait their turn.

These three are complementary: coalescing collapses *identical* duplicate calls; the limiter caps the
*volume* of distinct calls; backoff recovers gracefully when a limit is hit anyway.

---

## 7. Invalidation & refresh triggers

"Invalidation" here is not a single event — it is a set of triggers, each with defined behaviour:

| Trigger | What happens |
|--------|--------------|
| **TTL expiry** | The default. The next read after the stamp passes treats the copy as stale and refreshes it (subject to serve-stale-on-error). |
| **Staleness UX** | Depends on how the data is read. **Continuously-observed lists** (e.g. the deal lists) emit the cached copy immediately and refresh in the background — *stale-while-revalidate*. **One-shot reads** (details, rankings, bundles) refresh inline when stale before returning, but always fall back to the cached copy if that refresh fails (serve-stale). The **transact** details go further: when stale they make the caller *wait* on the refresh (a click-through must not show a dead price) — still falling back to stale on failure. |
| **Region switch** | Not an invalidation at all. Reads move to the new region's key; the old region's rows are left in place (and reclaimed later by the eviction sweep). |
| **Login / logout** | User-data caches are scoped to the account. Logged-out reads are empty; a fresh login reconciles from the server as source of truth (§8). |
| **Eviction sweep** | A startup task bounds total growth by deleting *long-dead* rows — never simply "everything expired," which would destroy the serve-stale fallback (§9). |
| **Format-version bump** | A stored cache-format version number; bumping it on an app update force-clears the caches whose serialised shape changed (§9). |

The key idea is that **expiry ≠ deletion**. A row past its TTL is "should be refreshed on next read," not
"safe to delete" — because it is exactly the fallback that serve-stale-on-error depends on. Deletion is a
separate, more conservative process (§9).

---

## 8. Writes & user data (a different shape)

Everything above is read caching. The user's own data — their saved lists (a "waitlist" of games and a
"collection") — is **read-write**, with the **server as the source of truth**, and is handled differently:

- **Persisted locally, scoped to the account.** The set of saved item-ids is stored locally so the
  saved/un-saved state is correct **instantly on startup and offline**, with no empty-then-fill flicker. The
  locally-observed set is **gated on login state**: it reads as empty whenever logged out, and is reconciled
  from the server on login.
- **Remote-first writes.** Toggling an item awaits the server write *first*, then updates the local copy. The
  consequence is important: because the local copy is only ever updated *after* the server confirms, **it
  never holds an unconfirmed edit**, which means the "server is the source of truth" reconciliation can
  blindly replace the local set without ever clobbering a pending user action. No conflict resolution is
  needed.
- **Reconciliation.** A refresh fetches the server's current set and replaces the local set wholesale (an
  atomic swap, so observers never see a momentary empty state).

This is the conservative half of a classic offline-write design. The *optimistic* half — updating locally
first for an instant response, queueing failed writes in an outbox, and replaying them before the next
server reconcile — is **deliberately deferred** (§13), because that is where all the ordering/correctness
risk lives and the only thing it buys is *offline writing*, which is marginal for this app.

---

## 9. Eviction & format versioning

Two startup mechanisms keep the cache bounded and forward-compatible. Both run once, in the background, when
the app launches.

### Eviction sweep (bounds growth)

As a user browses, per-item caches accumulate one row per item viewed; left unchecked this grows without
bound. The sweep trims it, with one firm rule: **never a blanket "delete where expired."** That would erase
exactly the recently-expired rows that serve-stale-on-error relies on. Instead it is **age-based with a grace
window beyond expiry**:

- Per-item detail/feed caches: delete rows expired **more than 7 days** ago (recently-expired rows survive as
  serve-stale fallbacks; only the long-dead are reclaimed).
- Price-history series: delete a series **not refreshed in 30 days** (it cheaply rebuilds via incremental
  refresh on next view).
- Identity mappings: delete once expired (no grace — re-deriving one is cheap).
- User data and the long-lived metadata tables are left untouched.

The net effect is to roughly bound storage to *recently-browsed* items, without tracking per-row access
times.

### Format version (a clear-on-upgrade escape hatch)

Some cache changes aren't a change to the *fields* of a row but to the *meaning or shape of a serialised
blob* — something a normal schema migration can't express. For that there is a stored **cache-format version
number**. On startup, if the stored number differs from the current one, the format-versioned caches are
cleared once and the number is updated. Bumping this constant in a future release is the deliberate
"start fresh" switch for semantic cache changes (and also the safety valve for the rare case where an
upstream identity id is merged/reassigned).

---

## 10. Per-resource policy (reference table)

| Resource | What it is | Freshness | Cache key | Storage | On staleness |
|----------|-----------|-----------|-----------|---------|--------------|
| Store deals | Current deals for one storefront | 8h | `(store, region)` | normalised rows | stale-while-revalidate |
| All-deals page | Paged/sorted/filtered deal search | **not persisted** | — | — | coalescing + rate-limit only |
| Storefront list | The set of storefronts | 8h | global | normalised rows | stale-while-revalidate |
| New releases | Upcoming/new game releases* | 24h | global | normalised rows | stale-while-revalidate |
| Giveaways | Active free-game giveaways* | 12h | global | normalised rows | stale-while-revalidate |
| Deal details | One deal's full info (+ cheaper stores) | **2h** | `(deal, region)` | blob | **fresh-blocking** + serve-stale |
| Game details | One game's info + current best prices | **2h** | `(game, region)` | blob | **fresh-blocking** + serve-stale |
| Price history | A game's historical-low time series | 24h, **incremental** | `(game, region)` | blob | stale-while-revalidate; 30-day retention |
| Game metadata | Title, art, etc. | 7d | `(game)` | normalised rows | stale-while-revalidate |
| Id mapping | External id → provider game id | 30d | `(externalId)` | normalised rows | hits cached; **misses never cached** |
| Free-text search | Query results | not cached | — | — | (transient) |
| Rankings | Curated "most X" feeds | 12h | `(type, region)` | blob | read-through |
| Bundles | Active storefront bundles | 12h | `(region)` | blob | read-through |
| Saved items | User's waitlist / collection | login-scoped | `(account)` | id sets | server-as-truth, remote-first writes |

\* New releases and giveaways come from two *sibling* third-party providers rather than the main deals API,
but flow through the same read-through caching mechanism described here.

---

## 11. Worked examples

**A — Opening a game's detail page (the transact path).**
1. *First open (cold cache):* nothing local → fetch upstream → store the detail blob stamped `now + 2h` →
   show it.
2. *Second open within 2h:* local copy is fresh → shown instantly, **no upstream call**.
3. *Open after 2h, upstream healthy:* stale → block briefly on a refresh → store + show the fresh price.
4. *Open after 2h, upstream rate-limited/down:* stale, refresh fails, but a cached copy exists → **serve the
   stale copy** rather than erroring (the user still sees the last-known price).

**B — Switching region.** The user changes country. The detail/list reads now resolve a different
`(…, region)` key. The new region's data is fetched and cached alongside the old region's, which is left
intact. Switching back is instant — no refetch — until the old rows age out via the sweep.

**C — Returning user, saved items.** On launch, the locally-persisted set of saved item-ids is read
immediately, so hearts/markers render correct on the first frame with no network wait. A background
reconcile then refreshes the set from the server.

**D — Cold-loading a feed-heavy screen.** A screen shows several rankings, each needing per-item enrichment —
potentially dozens of upstream calls at once. The concurrency limiter caps how many run simultaneously
(smoothing the burst), the 12h feed cache means the *second* visit makes none of those calls, and if any
individual call is rate-limited it backs off and the rest proceed.

---

## 12. Notable design decisions & trade-offs

- **Serve-stale over hard-fail.** The defining choice (§6). For an unreliable, rate-limited upstream,
  last-known data beats an error almost everywhere; the exception is the transact tier, which blocks on a
  refresh when it *can* but still falls back to stale rather than failing outright.
- **Region as a key dimension, not a clear trigger.** Keying by `(resource, region)` lets regions coexist and
  makes region-switching instant and offline-friendly, replacing an earlier "wipe everything on region
  change" approach.
- **Blob vs normalised** is decided per resource by whether the store needs to query inside the object.
- **Snapshot vs reference for feed prices.** Rankings embed ("snapshot") each item's price into the 12h feed
  cache rather than referencing a separate, shorter-lived price cache. The accepted cost is that a price on a
  *ranking tile* can be up to 12h stale; the benefit is eliminating a large per-item call fan-out on repeat
  views. (On the transact surfaces, where staleness actually matters, the price is in the 2h detail cache.)
- **A separate "current prices" cache was designed, then dropped.** It would have had no independent
  consumer: the transact price already lives inside the detail blobs, and the feed price is snapshotted as
  above. Building it would have been speculative dead code, so it was cut. (A general principle the project
  applied repeatedly: *don't cache what nothing reads independently.*)
- **Cache hits but never cache misses, for identity lookups.** A successful external-id→provider-id lookup is
  cached for 30 days; a *negative* result ("no such game") is deliberately **not** stored, so a later retry
  can still succeed if the provider gains the entry. (Permanently caching a miss would be a correctness bug.)
- **Incremental refresh for append-only data.** The price-history series is topped up with only the points
  newer than the last cached one, rather than refetched wholesale.

---

## 13. Deliberately out of scope / deferred

- **Optimistic offline writes + an outbox.** Updating saved-items locally *before* the server confirms (for
  an instant response and offline editing) requires a pending-writes queue replayed before reconciliation, or
  the server-as-truth refresh silently reverts the user's edit. That ordering is the entire risk surface, and
  offline *writing* is marginal here, so it is deferred. (The conservative remote-first path ships today.)
- **Negative-result caching for reads.** A read that legitimately returns empty (e.g. a game with no current
  deals in a region) currently re-checks on every view, because an empty result is treated as "never
  fetched." Caching an "checked, nothing here" marker with a short TTL is a known, deferred improvement.
- **Paged search results are not persisted.** They are sorted/filtered/region-sensitive and would collide
  with the per-storefront caches; they get only the cross-cutting benefits (coalescing, rate-limit handling).
- **No global request budget.** Coalescing + the concurrency limiter + backoff are considered sufficient; a
  hard per-interval budget is held in reserve.

---

*Summary: one small read-through primitive, applied with per-data-class TTL tiers and storage shapes; made
robust by serving stale data on failure and by both proactively and reactively respecting the upstream's
limits; invalidated by time, region, and login scope rather than by wholesale clearing; bounded by a
conservative startup sweep; and kept forward-compatible by a format-version switch.*
