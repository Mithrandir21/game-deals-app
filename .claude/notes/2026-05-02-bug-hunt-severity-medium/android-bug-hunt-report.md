# Android Bug Hunt — Report

Date: 2026-05-02
Branch: `wave/2026-05-02-bug-hunt/issue-75-77-giveaways-refresh-outcome` (HEAD)
Workspace: `.claude/notes/2026-05-02-bug-hunt-severity-medium/`

## Summary

- **Total findings: 7** (after deduplication of 9 raw findings — three pairs were the same root cause flagged from two angles)
- **Critical: 0 · High: 3 · Medium: 2 · Low: 2**
- **Specialists run (5):** coroutine-and-flow-defects, lifecycle-leak-hunter, compose-correctness, main-thread-violations, resource-leaks
- **Specialist with no findings:** `main-thread-violations`
- **Skipped specialist:** `kmp-defects` (project is not KMP — no `expect`/`actual`, no `commonMain`)

The codebase is in good shape on the structural axes (no main-thread blocking, no classic Activity/Fragment leaks, no raw I/O resource leaks, single-Activity Compose, all Retrofit endpoints `suspend`, all Room DAOs `suspend`/`Flow`, WebView teardown correct after PR #30/#67). The remaining defects cluster around a single class of bug: **uncancelled coroutine collectors writing to shared `MutableStateFlow`s** — present in `GiveawaysViewModel`, `GameViewModel`, and `DealDetailsController`. These produce real observable races (wrong content in the deal sheet, filtered list clobbered by unfiltered list) and one of them additionally swallows `CancellationException`, breaking structured concurrency.

## Quick-win table

| ID | Severity | Category | Location | Effort | Conf | Title |
|---|---|---|---|---|---|---|
| BUG-001 | High | Structured concurrency | `common/ui/.../DealDetailsController.kt:55` | Trivial | High | `DealDetailsController.load` swallows `CancellationException` |
| BUG-002 | High | Race condition | `common/ui/.../DealDetailsController.kt:23` | Small | High | `DealDetailsController.load` doesn't cancel prior load |
| BUG-003 | High | Race / collector leak | `feature/giveaways/.../GiveawaysViewModel.kt:36-66` | Small | High | `GiveawaysViewModel` accumulates duplicate Room collectors |
| BUG-004 | Medium | Compose stability | `domain/.../models/Search.kt:52` | Trivial | High | `SearchParameters.equals` always returns `false`, defeating Compose skipping |
| BUG-005 | Medium | Race condition | `feature/game/.../GameViewModel.kt:62-68` | Small | Medium | `GameViewModel.reloadGameDetails` launches parallel collector |
| BUG-006 | Low | Test inconsistency | `common/.../FlowExtensions.kt:98` | Trivial | Medium | `withMinimumDuration` uses wall-clock not virtual time |
| BUG-007 | Low | Resource (thread) | `domain/.../di/DomainModule.kt:55-57` | Trivial | Medium | Room `setQueryCallback` Executor never shut down, runs in release |

---

## Findings (full detail)

### BUG-001: `DealDetailsController.load` swallows `CancellationException`

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Non-cooperative cancellation / structured-concurrency violation |
| **Location** | `common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt:55` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** The outer `catch (t: Throwable)` block catches every throwable, including `CancellationException`. It then performs another suspend operation (`storesRepository.getStore`) and `_dealDetails.emit(...)` from inside the catch. When the parent scope is cancelled, the `withMinimumDuration { … }` block throws `CancellationException`; we land in this catch, ignore the cancellation signal, and continue executing — including writing `null` to `_dealDetails` after the scope was supposed to be dead.

**Impact.** On normal navigation away mid-load, the details `StateFlow` gets a spurious `DealDetailsError` (or `null`) emission. The controller is held by the surviving ViewModel (Home/Store), so the next time the bottom sheet observer becomes active it reads this stale error state and renders the error UI for ~one frame.

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
| **Category** | Race condition / uncancelled coroutine |
| **Location** | `common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt:23` (callers: `feature/home/.../HomeViewModel.kt:104`, `feature/store/.../StoreViewModel.kt:73`); same pattern in `feature/deal/.../DealDetailsViewModel.kt:34-78` |
| **Effort** | Small |
| **Confidence** | High |

*Reported by both coroutine-and-flow-defects and lifecycle-leak-hunter. Lifecycle hunter rated this Low; coroutine hunter rated this High based on the 750ms-wide race window. Held at High because `_dealDetails` is a `MutableStateFlow` that persists between sheet openings, so the loser of the race lingers in state.*

**Description.** `DealDetailsController.load` returns a `Job` but neither caller keeps a reference to the previous job, so each call launches a fresh coroutine without cancelling the prior one. The 750ms minimum-loading guard inside `withMinimumDuration(750L)` makes the race window large: tap deal A → tap deal B within ~700ms → both coroutines run to completion, both write to `_dealDetails` (a `MutableStateFlow<DealBottomSheetData?>`), and the slower one wins.

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

### BUG-003: `GiveawaysViewModel` accumulates duplicate Room collectors per filter / reload

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Race condition / coroutine collector leak |
| **Location** | `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:36-66` |
| **Effort** | Small |
| **Confidence** | High |

*Reported by both coroutine-and-flow-defects and lifecycle-leak-hunter. Held at High because the filtered collector actively contradicts the unfiltered `init` collector — Room invalidations cause SUCCESS-with-filter to be overwritten by SUCCESS-with-everything. This is a functional bug, not just bookkeeping.*

**Description.** `init { … observeGiveaways() … .collect { _uiState.emit(it) } }` starts a permanent collector over the unfiltered DAO Flow. `loadGiveaway(parameters)` then launches another permanent collector over the *filtered* DAO Flow, also `.collect { _uiState.emit(it) }`, without cancelling the previous one. `reloadGiveaways()` adds a third pattern. `GiveawaysScreen.kt:103` calls `viewModel.loadGiveaway(existingParameters)` every time the user closes the filter sheet — so after N filter-applies there are N+1 collectors all sharing `_uiState`. Each Room invalidation wakes every collector; the unfiltered one will overwrite a filtered SUCCESS with the full list. This is the pattern PR #33 fixed in `HomeViewModel` — `GiveawaysViewModel` was not migrated.

**Impact.** Filtered list gets clobbered by the unfiltered list on the next DB invalidation. Coroutine leak — collectors live until `onCleared()`, growing per filter interaction.

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

**Confidence rationale.** High — both `viewModelScope.launch { … .collect { … } }` blocks write `_uiState` and neither holds a reference that lets the other be cancelled. The Compose call site triggers `loadGiveaway` on every filter dismiss, making leak growth deterministic.

---

### BUG-004: `SearchParameters.equals` always returns `false`, defeating Compose skipping

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Compose stability / unstable parameter |
| **Location** | `domain/src/main/java/pm/bam/gamedeals/domain/models/Search.kt:52` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** `SearchParameters` is a `data class` whose `equals` is overridden to always return `false`. The override exists to defeat `MutableStateFlow`'s structural-equality conflation. The side effect: every `@Composable` taking a `SearchParameters` parameter is forced to recompose on every parent recomposition, because Compose's skipping rule compares parameters with `equals` — an always-`false` `equals` makes the value behave as if it changed every time.

**Impact.** `feature/search/.../SearchScreen.kt` passes `existingSearchParameters: SearchParameters` through `Screen` → `SearchFilters` → `Filters`. None of these composables can skip recomposition, so on every keystroke into the search field (`existingParameters.copy(title = it)` on line 117) the entire filters subtree re-evaluates. Any future `LaunchedEffect(params)` or `derivedStateOf` keyed on this type would also misbehave.

**Evidence.**
```kotlin
// domain/src/main/java/pm/bam/gamedeals/domain/models/Search.kt:48-53
/**
 * Returning `false` to avoid the default implementation of `equals` when attempting to emit
 * a new value in a `StateFlow`. See "Strong equality-based conflation" in StateFlow docs.
 */
override fun equals(other: Any?): Boolean = false
```

Composable call sites that take this type as a parameter (none can skip):
- `feature/search/.../SearchScreen.kt:94, 114, 134, 197, 309, 339-341`

**Recommended fix.** Revert the `equals` override and move non-conflating semantics to the producer side: replace `MutableStateFlow<SearchParameters>` with `MutableSharedFlow<SearchParameters>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)` in `SearchViewModel`, or wrap emissions in an `Indexed<T>` envelope. Removing the override restores data-class `equals`, re-enables Compose skipping, and unblocks `LaunchedEffect`/`derivedStateOf` keying.

**Confidence rationale.** High — verbatim antipattern. Note: a parallel wave PR (#88 on `wave/2026-05-02-bug-hunt/issue-76-search-parameters-equals`) may already address this; github-sync should dedupe before filing.

---

### BUG-005: `GameViewModel.reloadGameDetails` launches a parallel collector that races with prior reloads

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Race condition |
| **Location** | `feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt:62-68` |
| **Effort** | Small |
| **Confidence** | Medium |

*Reported by both coroutine-and-flow-defects and lifecycle-leak-hunter; merged. Both rated Medium.*

**Description.** `reloadGameDetails()` is wired to the user's "Retry" snackbar action. Each retry launches a fresh `viewModelScope.launch { loadGameDetailsFlow(gameId).collect { _uiState.emit(it) } }` without cancelling the previous reload. If the user mashes retry, two `loadGameDetailsFlow` collectors run in parallel, each emitting `Loading` → `Data`/`Error` to `_uiState`. Whichever finishes last wins; intermediate `Loading` emissions can also fight in the wrong order (a late `Loading` from an older retry leaves the screen stuck on Loading after a successful newer retry). The `init` collector also writes `_uiState` for the same `gameIdFlow`.

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

**Recommended fix.** Either store and cancel `private var reloadJob: Job?`, or trigger reload by re-emitting on `gameIdFlow` (turn it into a `Channel`/`SharedFlow` of "load tickets") so the existing `flatMapLatest` in `init` cancels the previous attempt:

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

### BUG-006: `withMinimumDuration` measures elapsed time with wall-clock instead of virtual time

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Time / dispatcher inconsistency (test-affecting) |
| **Location** | `common/src/main/java/pm/bam/gamedeals/common/FlowExtensions.kt:98` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** `withMinimumDuration` measures `System.currentTimeMillis()` before/after `block()` and pads with `delay(...)` for the remainder. Inconsistent with `mapDelayAtLeast` (lines 80–89) and `flatMapLatestDelayAtLeast` (lines 117–126), both virtual-time-friendly. Used in `DealDetailsController.load:40`, so any test driving that path with `runTest` virtual time sees `elapsed` as ~0 and the function pads the full `delayMillis`.

**Impact.** No production runtime defect — wall-clock is what users experience. Affects test ergonomics.

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

### BUG-007: Room `setQueryCallback` Executor never shut down and runs in release builds

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Resource (thread) — unconditional in release builds |
| **Location** | `domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:55-57` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** `provideDatabase` registers a SQL query callback with an `Executors.newSingleThreadExecutor()` instantiated inline. Two issues: (1) the executor is owned by no one — the worker thread lives for process lifetime; Room does not stop user-supplied callback executors when the DB is closed; (2) the callback is wired unconditionally, not gated on `BuildConfig.DEBUG` or `RemoteBuildType`, so the lambda + `verbose(logger)` dispatch runs in release builds for every query.

**Impact.** Bounded, single-process. One always-on worker thread + one log-message allocation per query in release. Not user-visible; matters for sustained background battery / memory accounting and for keeping the production process clean.

**Evidence.**
```kotlin
// domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:43-58
.setQueryCallback({ sqlQuery, bindArgs ->
    verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" }
}, Executors.newSingleThreadExecutor())
.build()
```

**Recommended fix.** Gate the callback on a debug build flag (mirror the pattern at `remote/cheapshark/.../RemoteNetworkModule.kt:33-37` for `HttpLoggingInterceptor`):

```kotlin
val builder = Room.databaseBuilder(...).fallbackToDestructiveMigration().addTypeConverter(...)
when (remoteBuildUtil.buildType) {
    RemoteBuildType.DEBUG -> builder.setQueryCallback(
        { sqlQuery, bindArgs -> verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" } },
        Executors.newSingleThreadExecutor()
    )
    else -> Unit
}
return builder.build()
```

**Confidence rationale.** Medium because (1) the executor is genuinely never released — leak source unambiguous; (2) impact is small. Marked Medium rather than High because the unconditional callback may be intentional (breadcrumb collection).

---

## Specialists that found nothing

- **android-bug-hunting-main-thread-violations** — 14 detectors run, all clean. StrictMode is wired in debuggable builds; all Room DAOs are `suspend`/`Flow`/`PagingSource`; all Retrofit endpoints are `suspend`; `SettingStorage.commit()` runs inside `withContext(ioDispatcher)`; `MainActivity.onCreate` only calls `setContent`; no `Thread.sleep`/`runBlocking`/`ContentResolver.query`/`PackageManager.getInstalledPackages` on Main.

## Notes and limitations

- **No KMP defect hunt was run** — project is pure Android (no `expect`/`actual`, no `commonMain`).
- **Native code, obfuscated modules, and test sources were not analyzed.** Hunt was scoped to `*/src/main/**` Kotlin code under the project root, excluding `build/` and `.claude/worktrees/`.
- **Recurring root cause.** BUG-002, BUG-003, and BUG-005 are all the same pattern: a function that launches a `viewModelScope.launch { … .collect { } }` (or `scope.launch { }`) without cancelling the previous one. PR #33 fixed this in `HomeViewModel`'s `loadTopStoresDeals` path; the fix has not been propagated to `DealDetailsController`, `GiveawaysViewModel`, or `GameViewModel`. Consider a single follow-up that audits every public ViewModel method for this antipattern, or factor the cancel-and-relaunch idiom into a small helper.
- **Possible parallel-PR overlap.** BUG-004 (`SearchParameters.equals = false`) may already be addressed by a parallel wave PR (#88 on `wave/2026-05-02-bug-hunt/issue-76-search-parameters-equals`) per the compose-correctness specialist's note. The github-sync skill should dedupe before filing.
- **Confidence-Low items.** None of the seven findings are Confidence Low; no items need extra human judgment beyond the standard review of the recommended fixes.
- **Findings files** (raw, per specialist) are also written to this workspace:
  - `findings-coroutine-and-flow-defects.md`
  - `findings-lifecycle-leak-hunter.md`
  - `findings-compose-correctness.md`
  - `findings-main-thread-violations.md`
  - `findings-resource-leaks.md`
