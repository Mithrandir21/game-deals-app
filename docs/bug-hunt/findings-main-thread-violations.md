# Main-Thread Violations — Bug Hunt Findings

Scope: Android `:app` + KMP `commonMain`/feature/domain/remote/common (tests excluded). **2 findings — 0 Critical, 0 High, 1 Medium, 1 Low.**

**Verified clean:** all 16 Room DAOs are `suspend`/`Flow` (no `allowMainThreadQueries`); `SharedPreferencesBackend.commit()` is fine because `StorageImpl` wraps every backend call in `withContext(ioDispatcher)`; Ktor (OkHttp/Darwin) deserializes responses off the caller's dispatcher; `Application.onCreate` pushes DB warm-up/cache maintenance/Sentry/notification lifecycle onto `Dispatchers.IO` with StrictMode on in debug; no `runBlocking`/`Thread.sleep` in production; no file I/O, `BitmapFactory`, `ContentResolver`, `packageManager`, or `HttpURLConnection`; no repository/`decodeFromString` calls inside `@Composable` bodies.

### BUG-001: Cached-blob JSON deserialization runs on the main thread (multiple repositories)

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Main-thread violation (CPU-bound JSON parse on Main) |
| **Location** | `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/games/GamesRepository.kt:170`, `:186`; `.../deals/DealsRepository.kt:128`; `.../bundles/BundlesRepository.kt:74`; `.../stats/StatsRepository.kt:87` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** Read-through caches store the network payload as serialized JSON in Room and, on a cache hit, `decodeFromString` it back into models. These `suspend` repository methods contain no `withContext`; the decode runs on the calling coroutine's dispatcher. The DB read is off-main (Room `setQueryCoroutineContext`), but a suspend DAO call resumes the caller on the caller's dispatcher, so the decode that follows runs on Main. The only callers are feature ViewModels on `viewModelScope` (Main) with no `flowOn`/`withContext` (grep across `feature/` for `flowOn|withContext|Dispatchers.` returns nothing).

**Impact.** CPU JSON parse on the UI thread on every warm-cache read: Game Page open (`getGameDetails`, `getPriceHistory`), deal click-through (`getDeal`), Stats rankings, Bundles list. A few ms for small payloads; tens of ms of first-frame jank for a long price-history series or large list. Silent — doesn't throw, and StrictMode won't catch it (the disk read already happened off-thread).

**Evidence.**
```kotlin
val cached = gameDetailsCacheDao.get(gameId, country)   // off-main (Room), resumes on caller dispatcher
    ?: error("Game details for $gameId ($country) missing after refresh")
return json.decodeFromString(GameDetails.serializer(), cached.json)   // CPU parse on Main (GamesRepository.kt:170)
// caller: GamePageViewModel.loadFlow() is a plain flow{} collected in viewModelScope with NO flowOn
```

**Recommended fix.** Wrap the decode (and the refresh-path encode) in `withContext(Dispatchers.Default)` in the repositories — `Default` because it's pure CPU, not blocking I/O:
```kotlin
return withContext(Dispatchers.Default) { json.decodeFromString(GameDetails.serializer(), cached.json) }
```
Per-call-site `withContext` is more robust than ViewModel `flowOn` because some callers (`onRegionsSelected`, `onCandidatePicked`) call the repo directly inside `viewModelScope.launch`, not via a `flow {}`.

**Confidence rationale.** High that it runs on Main (no `withContext` in repo; suspend-resume semantics; all callers confirmed on Main with no dispatcher switch). Medium severity (not High) because payloads are single objects / modest lists, not the >100KB that would mean ANR — real-data magnitude is the only uncertain dimension.

### BUG-002: Price-history decode is unconditional — runs on Main even when discarded

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Main-thread violation (redundant CPU work on Main) |
| **Location** | `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/games/GamesRepository.kt:186` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** `getPriceHistory` decodes the cached blob into `cachedHistory` at the top of the method, before the freshness check. On a fresh refresh the value is recomputed from the network top-up; the eager decode still ran (on Main) and may be thrown away.

**Impact.** A guaranteed main-thread JSON parse on every Game Page open with cached price history, including the "cache fresh, nothing to do" case. Subsumed by the BUG-001 fix; flagged separately for the redundant placement.

**Evidence.**
```kotlin
val cachedEntry = priceHistoryCacheDao.get(gameId, country)
val cachedHistory = cachedEntry?.let { json.decodeFromString(PriceHistory.serializer(), it.json) }  // :186 on Main, eager
...
cache.refreshIfNeeded()
return refreshed ?: cachedHistory ?: PriceHistory(...)
```

**Recommended fix.** Move the decode into `withContext(Dispatchers.Default)` (BUG-001); ideally defer it until after `refreshIfNeeded()`. (The `since` cursor still needs the decoded points, so a full skip isn't always possible — at minimum get it off Main.)

**Confidence rationale.** High on placement and Main-thread execution; Low severity — same parse as BUG-001, just slightly redundant, bounded to one screen.
