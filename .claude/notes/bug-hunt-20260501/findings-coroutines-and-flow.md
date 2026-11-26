# Coroutine and Flow Defect Hunt — Findings

Hunt date: 2026-05-01. Branch: `dev`. Scope: `*/src/main/**/*.kt`.

## Summary

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 3 |
| Medium | 2 |
| Low | 1 |

**Top 3 by impact:**
1. BUG-001 — `GiveawaysViewModel.loadGiveaway` leaks the init Room collector (race)
2. BUG-002 — `DealDetailsController.load` swallows `CancellationException`
3. BUG-003 — `GameViewModel.reloadGameDetails` stacks parallel collectors

---

### BUG-001: `GiveawaysViewModel.loadGiveaway` does not cancel the original Room collector — concurrent collectors race onto `_uiState`

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Race condition / cancellation leak |
| **Location** | `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:36-66` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** The `init` block launches a long-lived collector on `giveawaysRepository.observeGiveaways()` (a hot Room `Flow`). When `loadGiveaway(parameters)` is called it launches a *second* long-lived collector on `observeGiveaways(parameters)`. Neither launch cancels the prior one. Both collectors stay active inside `viewModelScope` and concurrently write to `_uiState` whenever Room invalidates the `giveaways` table.

**Impact.** After the user applies a filter via `loadGiveaway(...)`, every Room invalidation causes both flows to re-emit. The unfiltered collector produces "all giveaways"; the filtered collector produces the user-requested subset. Whichever emits last wins, so the UI flips between filtered and unfiltered results on every DB change (e.g. after `refreshGiveaways()` writes new rows). Same antipattern class as PR #68 (`loadTopStoresDeals`) but on a different ViewModel.

**Evidence.**
```kotlin
init {
    viewModelScope.launch {
        flow { emitAll(giveawaysRepository.observeGiveaways()) }   // collector A — long-lived
            .map { ... }
            .collect { _uiState.emit(it) }
    }
}

fun loadGiveaway(parameters: GiveawaySearchParameters) {
    viewModelScope.launch {                                        // collector B — long-lived
        flow { emitAll(giveawaysRepository.observeGiveaways(parameters)) }
            .map { ... }
            .collect { _uiState.emit(it) }
    }
}
```

**Recommended fix.** Drive the source from a `MutableStateFlow<GiveawaySearchParameters?>` and `flatMapLatest` into the Room flow so old subscriptions are cancelled automatically:

```kotlin
private val parametersFlow = MutableStateFlow<GiveawaySearchParameters?>(null)

init {
    viewModelScope.launch {
        parametersFlow
            .flatMapLatest { params ->
                if (params == null) giveawaysRepository.observeGiveaways()
                else giveawaysRepository.observeGiveaways(params)
            }
            .map { GiveawaysScreenData(SUCCESS, it.toImmutableList()) }
            .catch { emit(_uiState.value.copy(status = ERROR)) }
            .collect { _uiState.emit(it) }
    }
}

fun loadGiveaway(parameters: GiveawaySearchParameters) {
    parametersFlow.value = parameters
}
```

**Confidence rationale.** Production `observeGiveaways()` returns a Room Flow that does not complete; the unit test (`GiveawaysViewModelTest.kt:102-103`) uses `flowOf(...)` (which completes) so the test does not exercise the race. Antipattern matches the one already fixed on `dev` for stores.

---

### BUG-002: `DealDetailsController.load` swallows `CancellationException`

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Non-cooperative cancellation |
| **Location** | `common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt:55-70` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** The `load(...)` body wraps work in `try { … } catch (t: Throwable) { fatal(logger, t); … }` and does not rethrow `CancellationException`. The inner fallback also catches `Throwable`. Both `_dealDetails.emit(...)` calls inside the catch blocks suspend, and a third unconditional `_dealDetails.emit(null)` runs in the inner catch.

**Impact.** Breaks structured concurrency. If the host scope is cancelled while `load` is running (config change, navigation, sheet dismissal), the cancellation is logged as a fatal error to Crashlytics and the cancellation state is overwritten with a `DealDetailsError` or `null` emission instead of propagating cleanly. `DealsMediator.kt:76-81` already implements the correct pattern.

**Evidence.**
```kotlin
} catch (t: Throwable) {                            // catches CancellationException
    fatal(logger, t)                                 // fatal log on user navigation
    try {
        _dealDetails.emit(DealBottomSheetData.DealDetailsError(...))
    } catch (inner: Throwable) {
        fatal(logger, inner)
        _dealDetails.emit(null)
    }
}
```

**Recommended fix.** Rethrow `CancellationException` first (apply to both catch sites):
```kotlin
} catch (t: CancellationException) {
    throw t
} catch (t: Throwable) {
    fatal(logger, t)
    ...
}
```

**Confidence rationale.** Well-known antipattern; the surrounding codebase has the corrected pattern in `DealsMediator.kt`, demonstrating the intended discipline.

---

### BUG-003: `GameViewModel.reloadGameDetails` launches a parallel collector instead of cancelling the prior load

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | Race condition / cancellation leak |
| **Location** | `feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt:62-68` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** Each call to `reloadGameDetails()` launches a *new* `viewModelScope.launch { loadGameDetailsFlow(gameId).collect { _uiState.emit(it) } }`. The original collector created in `init { ... gameIdFlow.flatMapLatest { loadGameDetailsFlow(it) } ... }` remains active in parallel. Repeated retry taps stack collectors and concurrent emits race onto `_uiState`.

**Impact.**
- Multiple retry taps trigger N parallel network calls (`gamesRepository.getGameDetails`); whichever completes last sets the UI state.
- The init's `flatMapLatest` cancels its own re-runs but cannot cancel the orphan launches from `reloadGameDetails`.
- A late-arriving error from a previous retry can clobber a successful reload, or vice versa.

**Evidence.**
```kotlin
init {
    viewModelScope.launch {
        gameIdFlow.filterNotNull().distinctUntilChanged()
            .flatMapLatest { loadGameDetailsFlow(it) }      // collector A
            .collect { _uiState.emit(it) }
    }
}

fun reloadGameDetails() {
    val gameId = gameIdFlow.value ?: return
    viewModelScope.launch {                                 // collector B (and C, D, ...)
        loadGameDetailsFlow(gameId)
            .collect { _uiState.emit(it) }
    }
}
```

**Recommended fix.** Drive retries through the same source-of-truth flow. A reload trigger combined with `gameIdFlow` is cleaner than holding/cancelling a `Job?`:

```kotlin
private val reloadTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

init {
    viewModelScope.launch {
        combine(gameIdFlow.filterNotNull().distinctUntilChanged(),
                reloadTrigger.onStart { emit(Unit) }) { id, _ -> id }
            .flatMapLatest { loadGameDetailsFlow(it) }
            .collect { _uiState.emit(it) }
    }
}

fun reloadGameDetails() { reloadTrigger.tryEmit(Unit) }
```

**Confidence rationale.** Identical antipattern to PR #68 (`loadTopStoresDeals`); the fix discipline exists in `HomeViewModel` via `loadJob?.cancel()`. Low ambiguity.

---

### BUG-004: `GiveawaysViewModel.reloadGiveaways` does not cancel the in-flight Room collector — `LOADING` flickers and races with stale `SUCCESS`

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Race condition |
| **Location** | `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:46-56` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `reloadGiveaways()` emits `LOADING` then runs `giveawaysRepository.refreshGiveaways()` (a network call that writes new rows into Room). Meanwhile the `init` collector is still subscribed to the Room flow and will emit `SUCCESS` with existing rows (and again with the new rows when the refresh completes). The two coroutines race on `_uiState`.

**Impact.** UI shows interleavings like `LOADING → SUCCESS(stale) → SUCCESS(fresh)` or `ERROR → SUCCESS(stale-overwrite)`. If `refreshGiveaways()` fails, the catch emits `ERROR`, but the still-active init collector overwrites it back to `SUCCESS` on the next Room emission.

**Evidence.**
```kotlin
fun reloadGiveaways() {
    viewModelScope.launch {
        flow {
            emit(_uiState.value.copy(status = GiveawaysScreenStatus.LOADING))
            giveawaysRepository.refreshGiveaways()
        }
            .catch { emit(_uiState.value.copy(status = GiveawaysScreenStatus.ERROR)) }
            .collect { _uiState.emit(it) }   // racing with the init collector
    }
}
```

**Recommended fix.** Resolved naturally by BUG-001's fix — once the source-of-truth is a single `flatMapLatest`-driven collector, `reloadGiveaways()` only needs to flip status to LOADING via `_uiState.update { it.copy(...) }` and call `refreshGiveaways()` while letting the existing collector pick up new rows.

**Confidence rationale.** Direct consequence of two collectors being alive at once; same root cause as BUG-001 but a distinct user-visible symptom.

---

### BUG-005: `GiveawaysViewModel.reloadGiveaways` flow has no `SUCCESS` emission path

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Flow operator ordering / latent bug |
| **Location** | `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:46-56` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** Inside `reloadGiveaways()`, `flow { emit(LOADING); refreshGiveaways() }` emits one value then completes (no second `emit`). The downstream `.collect { _uiState.emit(it) }` therefore only ever observes the LOADING value; the success path of `refreshGiveaways()` produces no `SUCCESS` emission from this flow — it relies on the init-collector picking it up (the racing collector from BUG-004). If you adopt the BUG-001 fix that retires the init collector, you must also restructure this so a successful refresh leaves the UI in a non-LOADING state.

**Impact.** After the BUG-001 refactor, this method as written would leave the UI stuck on LOADING after a successful refresh. Today the bug is masked by the parallel init collector.

**Evidence.**
```kotlin
flow {
    emit(_uiState.value.copy(status = LOADING))
    giveawaysRepository.refreshGiveaways()           // no further emit on success
}
    .catch { emit(_uiState.value.copy(status = ERROR)) }
    .collect { _uiState.emit(it) }
```

**Recommended fix.** Don't `collect` here at all — call `refreshGiveaways()` and bracket it with `_uiState.update { it.copy(status = LOADING) }` before and a try/catch flipping to ERROR on failure; let the source-of-truth collector emit SUCCESS when Room delivers the new rows.

**Confidence rationale.** Confidence is Medium because the bug only manifests after BUG-001 is fixed. Today the missing SUCCESS is silently filled in by the init collector.

---

### BUG-006: Read-modify-write on `_uiState.value.copy(...)` across concurrent coroutines

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (lost update) |
| **Location** | `feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt:83,91,96,97`; `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:41,49,53,63` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** Multiple sites read `_uiState.value` and emit a copy without using `MutableStateFlow.update { ... }`. Concurrent coroutines (the long-lived init collector + a user-triggered `onReleaseGame` / `loadGiveaway` / `reloadGiveaways`) can read the same snapshot, mutate independent fields, and clobber each other on emit.

**Impact.** A field-level update made by coroutine A can be overwritten when coroutine B emits its `_uiState.value.copy(...)` based on a stale snapshot. Visible as occasional missing status changes (e.g. SUCCESS landing then immediately reverted to LOADING by a sibling). Severity is Low only because the offending sites all eventually settle to SUCCESS or ERROR.

**Evidence.**
```kotlin
// HomeViewModel.kt:91
.onStart { _uiState.emit(_uiState.value.copy(state = HomeScreenStatus.LOADING)) }
```

**Recommended fix.** Use `MutableStateFlow.update { current -> current.copy(...) }`, the documented atomic CAS helper:
```kotlin
_uiState.update { it.copy(state = HomeScreenStatus.LOADING) }
```

**Confidence rationale.** Race is real but only widens if the other concurrency bugs (BUG-001, BUG-003) remain. Once a single source-of-truth collector exists, most racing emit sites disappear. Flagged Low / Medium confidence — verify against the final state of surrounding fixes.

---

## Detectors that found nothing

- D1 `GlobalScope` usage — none.
- D2 `runBlocking` in production — none.
- D3 `.collect` without `repeatOnLifecycle` — Compose UI uses `collectAsStateWithLifecycle()` everywhere; ViewModels collect inside `viewModelScope`.
- D5 `MutableSharedFlow(replay=0)` used as state — only one (`HomeViewModel._events`), correctly used for one-shot UI events with `extraBufferCapacity = 1, DROP_OLDEST`, consumed via `SingleEventEffect`.
- D6 `async { ... }` never awaited — no app-level `.async`; the ones in `FlowExtensions.kt` are properly `await()`ed inside `coroutineScope { ... }`.
- D7 missing `flowOn` — every IO entry point is a Retrofit `suspend fun` or Room `Flow`/`suspend fun` (both internally dispatch off-Main).
- D8 blocking calls inside coroutines — `SettingStorage.commit()` is intentionally on `Dispatchers.IO`; no other blocking I/O detected.
- D10 `suspend fun` returning `Job`/`Deferred` — none.
- D11 `viewModelScope` for must-complete writes — analytics writes go via `FirebaseAnalytics` (fire-and-forget); no critical user-data writes seen in `viewModelScope`.
- D12 missing `SupervisorJob` — no custom `CoroutineScope(...)` constructions in app code.
- D13 `Dispatchers.Main` vs `.immediate` — no explicit `withContext(Dispatchers.Main)` re-dispatches found.
- D14 `onEach` without `launchIn`/`collect` — the single `onEach` (in `SearchViewModel`) is correctly inside a `flatMapLatest` chain that is collected.