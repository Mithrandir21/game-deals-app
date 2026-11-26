# Coroutine & Flow Defects — Bug Hunt Findings

Scope: production source across feature/domain/remote/common (tests excluded). Detectors D1–D15 run. **4 findings — 0 Critical, 0 High, 1 Medium, 3 Low.**

Overall the codebase has disciplined coroutine/Flow hygiene: proper `SupervisorJob` application scope, `CancellationException` rethrown everywhere, `repeatOnLifecycle`-backed event collection (`SingleEventEffect`), `collectAsStateWithLifecycle`, correct one-shot-event SharedFlows (`replay=0, extraBufferCapacity=1, DROP_OLDEST`) vs `StateFlow` for state, structured `coroutineScope`/`async`/`awaitAll` fan-outs, and WorkManager for must-complete work.

### BUG-001: `FollowedFranchiseRepository.toggle` / `remove` is a non-atomic read-modify-write — lost updates

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Race condition (lost update) |
| **Location** | `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/franchise/FollowedFranchiseRepository.kt:48-64` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `toggle` and `remove` each read the **whole** followed-franchise list via `getFollowed()`, mutate it in memory, then `persist()` the result to both the `MutableStateFlow` and `Storage`. No `Mutex` serializes this, and there are suspension points between the read (`getFollowed()`/`load()`) and the write (`persist()` → `storage.save`). Two concurrent calls each read the same baseline list and the last `persist()` wins, dropping the other's change.

**Impact.** Following/unfollowing two series in quick succession (each handler is a separate `viewModelScope.launch`, so they interleave) can silently lose one change: a follow that "didn't take", or an unfollow that reappears. The persisted `Storage` drifts from user intent. The per-id Waitlist/Collection/Ignored repos mutate Room by a single id and are immune; this repo rewrites the entire list.

**Evidence.**
```kotlin
override suspend fun toggle(franchiseId: Long, name: String) {
    val current = getFollowed()                     // read whole list (suspends)
    val next = if (current.any { it.franchiseId == franchiseId }) current.filterNot { it.franchiseId == franchiseId }
               else current + FollowedFranchise(franchiseId, name, clock.nowMillis())
    persist(next)                                   // write whole list (suspends) — last writer wins
}
```

**Recommended fix.** Serialize the read-modify-write under a `Mutex` (shared by `toggle`, `remove`, and the `load`-seeding path); keep the `storage.save` inside the critical section so persistence stays consistent.

**Confidence rationale.** The non-atomic whole-list rewrite with intervening suspension points is plain in source. Only UI tap cadence bounds it; a fast double-tap on two different toggles reaches the interleave. Medium severity reflects that it needs two near-simultaneous mutations.

### BUG-002: `NotesRepository.ensureLoaded` check-then-act races into a duplicate network fetch

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (redundant work) |
| **Location** | `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/notes/NotesRepository.kt:96-100` (called from `:55`) |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `ensureLoaded()` does a check-then-act on `notes.value` (`if (notes.value != null) return` then `notes.value = accountSource.getNotes()...`), invoked from `notes.onStart { ensureLoaded() }` in `observeNote`. Two collectors subscribing at once (e.g. two game cards visible) can both pass the null check before either assigns, both firing `accountSource.getNotes()`.

**Impact.** A duplicate ITAD `/notes` round-trip on first load; the second response overwrites the first. Result is idempotent (same data), so no corruption — just a wasted call and possible brief flicker. Bounded to once per session/login.

**Evidence.**
```kotlin
private suspend fun ensureLoaded() {
    if (notes.value != null) return                          // check
    notes.value = runCatching { accountSource.getNotes() }   // act — two collectors both reach here
        .getOrElse { emptyList() }.associate { it.gameId to it.note }
}
```

**Recommended fix.** Guard with a `Mutex` (re-check inside the lock), or coalesce the load through a single shared `Deferred` / the project's `RequestCoalescer`.

**Confidence rationale.** Check-then-act across a suspension point is unambiguous. Low severity because the fetch is idempotent; only cost is a redundant request on a cold cache.

### BUG-003: `DealsViewModel.retry()` / `DiscoverResultsViewModel.loadFirstPage()` launch untracked first-page loads that can race

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (stale result) |
| **Location** | `feature/discover/src/commonMain/kotlin/pm/bam/gamedeals/feature/discover/ui/DiscoverResultsViewModel.kt:112-133`; `feature/deals/src/commonMain/kotlin/pm/bam/gamedeals/feature/deals/ui/DealsViewModel.kt:255-259` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** Both VMs cancel the load-more `appendJob` before a first-page load but do **not** assign the first-page coroutine to any tracked `Job`. `DiscoverResultsViewModel.loadFirstPage()` (via `retry()`) and `DealsViewModel.retry()` each do a bare `viewModelScope.launch { ... }` writing the final result to `uiState`. Two rapid invocations run concurrently with no mutual cancellation; whichever completes last wins `uiState`, possibly the older request → stale results.

**Impact.** Rare, low-impact: a double-tapped "Retry" (or retry landing during the init load) can briefly show superseded results. No crash/leak; self-corrects. The browse-filter path in `DealsViewModel` is already protected by `collectLatest` (`:165-176`); this gap is only the imperative `retry()`/`loadFirstPage()` entry points.

**Evidence.**
```kotlin
fun loadFirstPage() {
    appendJob?.cancel()                       // cancels load-more, not a prior first-page load
    viewModelScope.launch { ... uiState.update { ... } }   // untracked — two can race
}
fun retry() = loadFirstPage()
```

**Recommended fix.** Track the first-page job and cancel it on re-entry, or route retries through a trigger flow consumed with `collectLatest`/`flatMapLatest` (the pattern `GamePageViewModel.reloadTrigger` and the Deals browse `combine` already use).

**Confidence rationale.** Untracked launch is clear. Medium (not High) because it needs two near-simultaneous `retry()` calls, and the `status != DATA` guard on `loadNextPage` prevents the more common append/first-page interleave.

### BUG-004: `GamePeekController` cancels the previous load without joining, so a stale emission can land on the shared `data` flow

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (stale emission) |
| **Location** | `common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/GamePeekController.kt:40-46, 53-68, 121-124` |
| **Effort** | Small |
| **Confidence** | Low |

**Description.** `load`, `loadByTitle`, and `dismiss` each do `loadJob?.cancel()` then launch a new coroutine that emits into the single shared `data: MutableStateFlow<GamePeekSheetData?>`. `cancel()` is asynchronous and not followed by `join()`, so the previous coroutine may still run to its next suspension point as the new one begins emitting. If the old coroutine reaches a terminal `data.emit(...)` (the `Error`/`Data` emit in `emitGameDetails`) after the new one emitted `Loading`, the sheet can momentarily show stale content/error.

**Impact.** Edge-case visual glitch only: rapidly peeking game A then B could flash A's error/content in B's sheet for a frame. No crash/leak; the suspending repository calls give cancellation a prompt cancellation point before the stale `emit`, so the window is narrow.

**Evidence.**
```kotlin
fun load(scope: CoroutineScope, gameId: String, gameName: String, thumb: String?) {
    loadJob?.cancel()                       // async cancel, no join
    loadJob = scope.launch {
        data.emit(GamePeekSheetData.Loading(gameId, gameName, thumb))
        emitGameDetails(gameId, gameName, thumb)   // old job may still emit Error/Data here
    }
}
```

**Recommended fix.** Use `cancelAndJoin()` before relaunching, or model the peek as `flatMapLatest` over a `gameId` key flow so the framework guarantees the previous inner flow is cancelled before the new one emits (mirroring `GamePageViewModel`'s `reloadTrigger.flatMapLatest { loadFlow() }`).

**Confidence rationale.** Low: the race is real in principle but the suspending `getGameDetails`/`fetchGameDetailsByTitle` calls almost always provide a cancellation point before the stale `emit`, so the stale value rarely wins. Flagged for awareness; not a confirmed user-visible defect.

### Detectors that found nothing
- **D1 GlobalScope** — none; `GameDealsApplication.kt:68` uses a proper `SupervisorJob + Dispatchers.IO` app scope.
- **D2 runBlocking** — none in production.
- **D3 lifecycle-aware collection** — screens use `SingleEventEffect` (`repeatOnLifecycle`) + `collectAsStateWithLifecycle`. `NotificationRouteBus.routes` in `NavGraph.kt:54` collects in a bare `LaunchedEffect(Unit)` but is a consume-once buffered `Channel` — intended.
- **D4/D5** — nullable `StateFlow<T?>` are all lazy-seeded state; every `MutableSharedFlow` is a one-shot event bus with correct `replay=0`.
- **D6** — all `async`/`awaitAll` fan-outs are awaited inside a `coroutineScope`.
- **D7/D8** — Storage I/O is wrapped in `withContext(ioDispatcher)`; no blocking calls in suspend contexts.
- **D9** — `CancellationException` is rethrown in every reviewed `catch (Throwable)` over suspend work.
- **D10–D15** — none (must-complete work uses WorkManager; the one `.onEach` is terminally collected; combine paths use loading sentinels).

Note: `RequestCoalescer` (`domain/.../cache/RequestCoalescer.kt`) is well-documented but currently **not instantiated in production** (dead code) — not a coroutine defect, just unused.
