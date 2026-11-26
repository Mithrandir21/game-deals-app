# Findings — Coroutine and Flow Defects

Hunter: `android-bug-hunting-coroutine-and-flow-defects`
Date: 2026-05-02
Branch HEAD: `wave/2026-05-02-bug-hunt/issue-75-77-giveaways-refresh-outcome`

Found 5 findings (0 Critical, 3 High, 1 Medium, 1 Low).

---

### BUG-001: `DealDetailsController.load` swallows `CancellationException`

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Non-cooperative cancellation / structured-concurrency violation |
| **Location** | `common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt:55` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** The outer `catch (t: Throwable)` block catches every throwable, including `CancellationException`. It then calls `storesRepository.getStore(...)` again and `_dealDetails.emit(...)` from inside the catch. When the parent scope is cancelled, `withMinimumDuration { … }` throws `CancellationException`; we land in this catch, ignore the cancellation signal, and execute another suspend operation that may itself throw `CancellationException`, caught by the inner `catch (inner: Throwable)`, and we still call `_dealDetails.emit(null)` after our scope was supposed to be dead. This breaks structured concurrency.

**Impact.** On normal navigation away mid-load, the details `StateFlow` gets a spurious `DealDetailsError` (or `null`) emission. Since the controller is held by the surviving ViewModel (Home/Store), the next time the bottom sheet observer becomes active it reads this stale error state and renders the error UI for ~one frame.

**Evidence.**
```kotlin
fun load(scope: CoroutineScope, dealId: String, ...): Job = scope.launch {
    try {
        _dealDetails.emit(DealBottomSheetData.DealDetailsLoading(...))
        val data = withMinimumDuration(750L) { ... }   // CancellationException happens here on nav-away
        _dealDetails.emit(data)
    } catch (t: Throwable) {                            // catches CancellationException too
        fatal(logger, t)
        try {
            _dealDetails.emit(DealBottomSheetData.DealDetailsError(...))
        } catch (inner: Throwable) {
            fatal(logger, inner)
            _dealDetails.emit(null)                    // continues after scope cancellation
        }
    }
}
```

**Recommended fix.** Re-throw `CancellationException` before the broad catch — same pattern already used in `DealsMediator.kt:76`:

```kotlin
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (t: Throwable) {
    fatal(logger, t)
    _dealDetails.emit(DealBottomSheetData.DealDetailsError(...))
}
```

**Confidence rationale.** High — `withMinimumDuration` always reaches a suspend point (`block()` then `delay`), so cancellation reliably surfaces as `CancellationException`. The sibling `DealsMediator` already documents and applies the correct pattern.

---

### BUG-002: `DealDetailsController.load` does not cancel a prior load — concurrent loads race on `_dealDetails`

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Race condition |
| **Location** | `common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt:23` (callers at `feature/home/.../HomeViewModel.kt:104`, `feature/store/.../StoreViewModel.kt:73`) |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `DealDetailsController.load` returns a `Job` but neither `HomeViewModel.loadDealDetails` nor `StoreViewModel.loadDealDetails` keeps a reference to the previous job, so each call launches a fresh coroutine without cancelling the prior one. The 750ms minimum-loading guard inside `withMinimumDuration(750L)` makes the race window large: tap deal A → tap deal B within ~700ms → both coroutines run to completion, both write to `_dealDetails` (a `MutableStateFlow<DealBottomSheetData?>`), and the slower one to finish wins.

**Impact.** Sporadic wrong-content in the deal-details bottom sheet when users tap deals quickly. Same class as the already-fixed issue #33 but on the per-tap path.

**Evidence.**
```kotlin
// DealDetailsController.kt:23-29
fun load(scope: CoroutineScope, dealId: String, ...): Job = scope.launch { ... }

// HomeViewModel.kt:103-105 — caller drops the returned Job
fun loadDealDetails(...) {
    dealDetailsController.load(viewModelScope, dealId, dealStoreId, ...)
}

// StoreViewModel.kt:72-74 — same pattern
fun loadDealDetails(...) {
    dealDetailsController.load(viewModelScope, dealId, dealStoreId, ...)
}
```

**Recommended fix.** Cancel the prior job before launching the next, inside `DealDetailsController.load` itself so all callers benefit:

```kotlin
private var loadJob: Job? = null
fun load(scope: CoroutineScope, ...): Job {
    loadJob?.cancel()
    return scope.launch { ... }.also { loadJob = it }
}
```

Or drive loads through a `MutableStateFlow<DealRequest?>` collected with `flatMapLatest`.

**Confidence rationale.** High — both call sites discard the returned `Job`, and `withMinimumDuration(750L)` guarantees the race window is hundreds of milliseconds wide.

---

### BUG-003: `GiveawaysViewModel.loadGiveaway` leaks an unbounded number of concurrent `_uiState` writers

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Race condition / coroutine leak |
| **Location** | `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:58` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `init { … observeGiveaways() … .collect { _uiState.emit(it) } }` starts a permanent collector over the unfiltered DAO Flow. `loadGiveaway(parameters)` then launches *another* permanent collector over the *filtered* DAO Flow, also `.collect { _uiState.emit(it) }`, without cancelling the previous one. `GiveawaysScreen.kt:103` calls `viewModel.loadGiveaway(existingParameters)` every time the user closes the filter sheet, so after N filter-applies there are N+1 collectors all sharing `_uiState`. Each Room invalidation wakes every collector; the unfiltered one will overwrite a filtered SUCCESS with the full list.

**Impact.** Filtered list gets clobbered by the unfiltered list on the next DB invalidation. Also a coroutine leak — collectors live until `onCleared()`, growing per filter interaction.

**Evidence.**
```kotlin
// GiveawaysViewModel.kt
init {
    viewModelScope.launch {
        flow { emitAll(giveawaysRepository.observeGiveaways()) }
            .map { GiveawaysScreenData(status = SUCCESS, giveaways = it.toImmutableList()) }
            .catch { emit(_uiState.value.copy(status = ERROR)) }
            .collect { _uiState.emit(it) }            // collector #1, never cancelled
    }
}
fun loadGiveaway(parameters: GiveawaySearchParameters) {
    viewModelScope.launch {                            // never cancels prior
        flow { emitAll(giveawaysRepository.observeGiveaways(parameters)) }
            .map { GiveawaysScreenData(status = SUCCESS, ...) }
            .catch { emit(_uiState.value.copy(status = ERROR)) }
            .collect { _uiState.emit(it) }            // collector #2, #3, … each call
    }
}
```

```kotlin
// GiveawaysScreen.kt:100-105
onShowFiltersChanged = { newShowFilters ->
    showFilters = newShowFilters
    if (!newShowFilters) {
        viewModel.loadGiveaway(existingParameters)
    }
},
```

**Recommended fix.** Drive the screen with a single collector and a parameters `StateFlow`, mirroring `SearchViewModel`/`StoreViewModel`:

```kotlin
private val params = MutableStateFlow(GiveawaySearchParameters())
init {
    viewModelScope.launch {
        params.flatMapLatest { giveawaysRepository.observeGiveaways(it) }
            .map { GiveawaysScreenData(SUCCESS, it.toImmutableList()) }
            .catch { emit(_uiState.value.copy(status = ERROR)) }
            .collect { _uiState.emit(it) }
    }
}
fun loadGiveaway(parameters: GiveawaySearchParameters) { params.value = parameters }
```

**Confidence rationale.** High — both `viewModelScope.launch { … .collect { … } }` blocks both write `_uiState` and neither holds a reference that lets the other be cancelled. The Compose call site triggers `loadGiveaway` on every filter dismiss, making leak growth deterministic.

---

### BUG-004: `GameViewModel.reloadGameDetails` launches a parallel collector that races with prior reloads

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Race condition |
| **Location** | `feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt:62` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `reloadGameDetails()` calls `viewModelScope.launch { loadGameDetailsFlow(gameId).collect { _uiState.emit(it) } }` without cancelling the previous reload. If the user mashes retry, two `loadGameDetailsFlow` collectors run in parallel, each emitting `Loading` (via `.onStart`) → `Data`/`Error` to `_uiState`. Whichever finishes last wins, and intermediate `Loading` emissions can also fight in the wrong order (a Loading from a still-running first call landing after the second call's Data leaves the screen stuck on Loading).

**Impact.** Sporadic "stuck on Loading" or wrong-data after rapid retries.

**Evidence.**
```kotlin
fun reloadGameDetails() {
    val gameId = gameIdFlow.value ?: return
    viewModelScope.launch {                          // no prior-job cancellation
        loadGameDetailsFlow(gameId)
            .collect { _uiState.emit(it) }
    }
}
```

**Recommended fix.** Either store the `Job` and cancel before relaunching, or drive reloads through a `MutableSharedFlow<Unit>` combined with `gameIdFlow` so `flatMapLatest` handles cancellation:

```kotlin
private val reloadTick = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
init {
    viewModelScope.launch {
        combine(gameIdFlow.filterNotNull().distinctUntilChanged(), reloadTick) { id, _ -> id }
            .flatMapLatest { loadGameDetailsFlow(it) }
            .collect { _uiState.emit(it) }
    }
}
fun reloadGameDetails() { reloadTick.tryEmit(Unit) }
```

**Confidence rationale.** Medium — the race is real but only fires under fast repeated retries. In typical use a user taps reload once.

---

### BUG-005: `withMinimumDuration` measures elapsed time with `System.currentTimeMillis()` instead of virtual time

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Time / dispatcher inconsistency |
| **Location** | `common/src/main/java/pm/bam/gamedeals/common/FlowExtensions.kt:98` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** `withMinimumDuration` measures `System.currentTimeMillis()` before/after `block()` and pads with `delay(...)` for the remainder. This is inconsistent with `mapDelayAtLeast` (lines 80–89) and `flatMapLatestDelayAtLeast` (lines 117–126), both of which the doc comment specifically calls out as virtual-time-friendly. Used in `DealDetailsController.load` at `DealDetailsController.kt:40`, so any test driving that path with `runTest` virtual time sees the wall-clock `elapsed` as ~0 and the function pads the full `delayMillis`. Benign in production; causes test flakes/long waits and inconsistency with siblings.

**Impact.** No production runtime defect — wall-clock is what users experience.

**Evidence.**
```kotlin
suspend inline fun <T> withMinimumDuration(delayMillis: Long, crossinline block: suspend () -> T): T = coroutineScope {
    val before = System.currentTimeMillis()           // wall-clock; ignores TestDispatcher
    val result = block()
    val elapsed = System.currentTimeMillis() - before
    if (elapsed < delayMillis) delay(delayMillis - elapsed)
    result
}
```

**Recommended fix.** Mirror `mapDelayAtLeast`:

```kotlin
suspend inline fun <T> withMinimumDuration(delayMillis: Long, crossinline block: suspend () -> T): T = coroutineScope {
    val pad = launch { delay(delayMillis) }
    val result = block()
    pad.join()
    result
}
```

**Confidence rationale.** Medium — the wall-clock choice may be intentional, but it diverges from the explicitly-documented virtual-time pattern of the surrounding helpers and trips up `runTest`.

---

## Detectors with no findings

- **D1 GlobalScope:** zero hits in `*/src/main/**`.
- **D2 runBlocking:** only in tests.
- **D5 SharedFlow as state:** the one `MutableSharedFlow<HomeUiEvent>` in `HomeViewModel` is correctly used for events (extraBufferCapacity=1, DROP_OLDEST), not state.
- **D6 async without await:** the two `async { }` calls in `FlowExtensions.kt` are both `await()`'d.
- **D7 Missing flowOn:** no `flow { … }` builders that perform blocking work — repos delegate to Retrofit suspend functions and Room Flow which already dispatch correctly.
- **D8 Blocking calls:** zero hits in main.
- **D10 Suspend fun returning Job/Deferred:** zero hits.
- **D12 Custom CoroutineScope:** zero hits in main.
- **D13 Dispatchers.Main vs Main.immediate:** no explicit `withContext(Dispatchers.Main)`.
- **D14 onEach without launchIn:** the single `.onEach` in `SearchViewModel.kt:49` is composed into a flow that is `.collect`ed.
- **D15 combine/zip initial-value surprise:** no `combine`/`zip` in main.
