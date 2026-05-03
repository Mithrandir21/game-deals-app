---
**Path scope:** `common/**`, `feature/*/src/main/java/**/ui/*ViewModel.kt`, `domain/**`, `remote/**`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Concurrency

This codebase shows disciplined coroutine scope management, a small set of carefully tuned Flow operators that respect virtual time, and consistent test-time scheduling across all async work.

## Patterns

### Virtual-Time-Correct Delay Operators

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** delay-sensitive flows in search, deal-details, and elsewhere

**The pattern.**
Three custom Flow operators (`mapDelayAtLeast`, `flatMapLatestDelayAtLeast`, `withMinimumDuration`) measure elapsed time via `coroutineScope { async + launch + await + join }` rather than wall-clock time. Padding (`delay(remaining)`) runs concurrently with the work, so elapsed time is measured by the test scheduler, not `System.currentTimeMillis()`. Behavior is identical under production `Dispatchers.Main` and `runTest` virtual clocks.

**Why this works for us.**
UX requires a minimum loading-state duration (e.g., "don't flash the spinner for less than 750 ms") to avoid jarring flicker. Wall-clock delays are flaky in tests; virtual-time correctness gives deterministic, fast coverage.

**Known trade-offs / when it strains.**
Adds a small `coroutineScope` overhead per emission. The `async + launch + await + join` shape is non-obvious until documented. New developers may reach for `delay` alone and re-introduce wall-clock timing.

**How to apply it.**
```kotlin
flow.mapDelayAtLeast(750) { value -> expensiveWork(value) }

flow.flatMapLatestDelayAtLeast(1000) { query ->
  repository.search(query)
}

suspend fun loadWithMinDuration() = withMinimumDuration(500) {
  repository.fetchData()
}
```

**Seen in.**
- common/src/main/java/pm/bam/gamedeals/common/FlowExtensions.kt
- feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
- feature/deal/src/main/java/pm/bam/gamedeals/feature/deal/ui/DealDetailsViewModel.kt

**Deep dive (senior).**
Operators are exhaustively tested in `common/src/test/.../FlowExtensionsTest.kt`. Tests assert via `testScheduler.currentTime` to verify the operator emits after at least N ms under `runTest` and adds no extra delay if the work takes longer. Use these only for UX-driven minimum delay; for resilience use `catch` + `retry`.

### `viewModelScope.launch` for State and Event Emission

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all 6 feature ViewModels

**The pattern.**
ViewModels launch flows into `viewModelScope`, connecting repository Flows to mutable state holders (`MutableStateFlow`, `MutableSharedFlow`). Structured cancellation is enforced — when the ViewModel is cleared, all in-flight coroutines are cancelled.

**Why this works for us.**
Automatic lifecycle coupling; no manual cleanup. Scope is cancelled when the host is destroyed, preventing state emissions into destroyed UIs.

**Known trade-offs / when it strains.**
Multiple overlapping `launch`es for the same logical flow can race (two "load" jobs both writing `uiState`). Mitigate with serialized-Job tracking or `flatMapLatest`.

**How to apply it.**
```kotlin
init {
  viewModelScope.launch {
    repository.observeData()
      .catch { _state.value = Error }
      .collect { _state.value = it }
  }
}
```

**Seen in.**
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
- feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt

### `Job` Cancellation for Serialized Async Work

**Status:** emerging
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** HomeViewModel, DealDetailsViewModel

**The pattern.**
When a ViewModel function may be called multiple times (e.g., `loadDealDetails()`, `loadTopStoresDeals()`), prior in-flight coroutines must be cancelled before starting a new one. The class stores a nullable `Job?` and cancels it before re-launching.

**Why this works for us.**
Prevents overlapping async loads from writing conflicting state. Ensures only the latest request's result lands in the state holder.

**Known trade-offs / when it strains.**
Explicit Job tracking is boilerplate compared to `flatMapLatest`-style automatic cancellation. Doesn't fully prevent races when two callers both observe `loadJob == null` before either assigns. Prefer `flatMapLatest` on a trigger flow when the call site allows it.

**How to apply it.**
```kotlin
private var loadJob: Job? = null

fun loadDetails() {
  loadJob?.cancel()
  loadJob = viewModelScope.launch {
    // work
  }
}
```

**Seen in.**
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/deal/src/main/java/pm/bam/gamedeals/feature/deal/ui/DealDetailsViewModel.kt

### Repository Flows with `.onError` (Re-Throwing) Logging

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all repositories

**The pattern.**
Repositories expose `Flow<T>` for observable data and suspend functions for one-shot fetches. Flows are decorated with `.onError { ... }` (logs and re-throws) or `.catchAndContinue { ... }` (swallows and emits a default). The `onError` extension allows a side effect (logging) before re-throwing — distinct from the standard `catch` operator which swallows.

**Why this works for us.**
Repositories define what to fetch and how to cache it; downstream errors are surfaced explicitly to the ViewModel, which decides how to render them. The re-throwing variant ensures exceptions don't silently disappear.

**Known trade-offs / when it strains.**
Doubles up catch clauses (repository `.onError` + ViewModel `.catch`). If a ViewModel forgets to `.catch`, exceptions crash the app. Requires discipline to choose the right variant.

**How to apply it.**
```kotlin
fun observeGiveaways(): Flow<List<Giveaway>> =
  giveawaysDao.observeAllGiveaways()
    .onError { fatal(logger, it) }   // log, then re-throw

// In ViewModel:
viewModelScope.launch {
  repository.observeGiveaways()
    .catch { _state.value = Error }
    .collect { _state.value = Success(it) }
}
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/giveaway/GiveawaysRepository.kt
- common/src/main/java/pm/bam/gamedeals/common/FlowExtensions.kt

### Test Virtual-Time Discipline with `runTest`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all test classes touching Flow or suspending code

**The pattern.**
Tests use `kotlinx.coroutines.test.runTest { }` with an implicit `TestDispatcher` and virtual clock. No `Thread.sleep()` and no `System.currentTimeMillis()` checks. Delay-based assertions measure `testScheduler.currentTime` before and after the operation.

**Why this works for us.**
Virtual time runs in microseconds of wall-clock time regardless of delay durations. Tests execute in milliseconds; scheduling is fully deterministic.

**Known trade-offs / when it strains.**
Code that directly checks `System.currentTimeMillis()` will see wall-clock time and break. The custom delay operators were designed to avoid this. Instrumented tests can't rely on virtual time — they need real delays or an injected clock.

**How to apply it.**
```kotlin
@Test
fun `operation respects minimum duration`() = runTest {
  val start = testScheduler.currentTime
  val result = withMinimumDuration(1000) { 42 }
  val elapsed = testScheduler.currentTime - start
  assertEquals(42, result)
  assertEquals(1000, elapsed)
}
```

**Seen in.**
- common/src/test/java/pm/bam/gamedeals/common/FlowExtensionsTest.kt
- feature/home/src/test/java/pm/bam/gamedeals/feature/home/ui/HomeViewModelTest.kt
- testing/src/main/java/pm/bam/gamedeals/testing/MainCoroutineRule.kt

### `MutableSharedFlow(replay=0, DROP_OLDEST)` for One-Shot Events

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** HomeViewModel events, SearchViewModel state, GameViewModel reload trigger

**The pattern.**
State flows hold UI state with a default value. Shared flows carry one-off events (navigation, snackbar, reload trigger). Both are created as `Mutable*Flow` and exposed via `as*Flow()`. `MutableSharedFlow` is configured with `replay = 0`, `extraBufferCapacity = 1`, `onBufferOverflow = BufferOverflow.DROP_OLDEST`.

**Why this works for us.**
Read-only public APIs prevent accidental writes from the UI. `DROP_OLDEST` ensures events don't queue indefinitely; recent-event loss is acceptable for UI events the collector hasn't observed yet.

**Known trade-offs / when it strains.**
The mutable/immutable pair requires discipline; a typo can leak the mutable. `DROP_OLDEST` is correct for UI events but wrong for critical commands — copy-paste hazard.

**How to apply it.**
```kotlin
private val _state = MutableStateFlow(Loading)
val state: StateFlow<Data> = _state.asStateFlow()

private val _events = MutableSharedFlow<Event>(
  replay = 0,
  extraBufferCapacity = 1,
  onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val events: SharedFlow<Event> = _events.asSharedFlow()
```

**Seen in.**
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt

### `flatMapLatest` for Latest-Wins Query Cancellation

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** SearchViewModel, GameViewModel, GiveawaysViewModel

**The pattern.**
When a query or parameter changes, `flatMapLatest` automatically unsubscribes from the previous inner Flow and starts a new one. Old in-flight transformations are cancelled.

**Why this works for us.**
Search queries typed quickly should not queue; only the final query is executed. Stale results never overwrite newer ones.

**Known trade-offs / when it strains.**
Cancellation is silent — no callback when an inner Flow is dropped. Intermediate states from partially-started subscriptions are lost.

**How to apply it.**
```kotlin
viewModelScope.launch {
  queryFlow
    .flatMapLatest { query ->
      repository.search(query).map { SearchData.Results(it) }
    }
    .catch { emit(SearchData.Error) }
    .collect { _state.emit(it) }
}
```

**Seen in.**
- feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
- feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt

## What we don't do

- **No `GlobalScope` and no scopeless `launch { }`.** All coroutines are rooted in `viewModelScope` or test scopes. **Why we avoid it:** unscoped coroutines outlive their owners and leak state into destroyed UIs.
- **No `runBlocking` in production code.** Found only in test setup. **Why we avoid it:** `runBlocking` defeats structured concurrency; the production codebase has no place where blocking the caller is correct.
- **No explicit `Dispatcher` injection.** All work happens on `Dispatchers.Main` (via `viewModelScope`) and implicit IO for Room/Retrofit. **Why we avoid it:** dispatcher-aware injection is overkill for the size of this codebase; Room and Retrofit handle their own threading.
- **No `SupervisorJob` or custom `CoroutineExceptionHandler`.** Coroutine cancellation is allowed to propagate; ViewModels don't suppress it.
- **No `launch { }` without `.collect()` or `.catch()`.** Every Flow subscription is guarded against downstream exceptions.
