# Android Bug Hunt — Report

**Date:** 2026-05-01 · **Branch:** `dev` · **Scope:** `*/src/main/**/*.kt` (worktrees and `build/` excluded)

## Summary

- **Total findings:** 10
- **Critical:** 0 · **High:** 3 · **Medium:** 4 · **Low:** 3
- **Specialists run:** coroutines-and-flow, lifecycle-leaks, compose-correctness, main-thread-violations, resource-leaks
- **Specialists skipped:** kmp-defects (project is single-platform Android, no `commonMain`/`expect`)
- **Modules covered:** 17 (`:app`, `:base`, `:common`, `:common:ui`, `:logging`, `:testing`, `:remote`, `:remote:gamerpower`, `:remote:cheapshark`, `:domain`, `:feature:store`, `:feature:deal`, `:feature:game`, `:feature:search`, `:feature:home`, `:feature:webview`, `:feature:giveaways`)

## Quick-win table

| ID | Severity | Category | Location | Effort | Confidence | Title |
|---|---|---|---|---|---|---|
| BUG-001 | High | Cancellation | `common/ui/.../DealDetailsController.kt:55-70` | Trivial | High | `DealDetailsController.load` swallows `CancellationException` |
| BUG-002 | High | Race | `feature/giveaways/.../GiveawaysViewModel.kt:36-66` | Small | High | `GiveawaysViewModel.loadGiveaway` stacks parallel Room collectors |
| BUG-003 | High | Race | `feature/game/.../GameViewModel.kt:62-68` | Small | High | `GameViewModel.reloadGameDetails` stacks parallel collectors |
| BUG-004 | Medium | Compose stability | `common/.../CommonFlowExtensions.kt:80-92` | Trivial | High | `SingleEventEffect` captures collector lambda without `rememberUpdatedState` |
| BUG-005 | Medium | Race | `feature/giveaways/.../GiveawaysViewModel.kt:46-56` | Small | High | `GiveawaysViewModel.reloadGiveaways` races with init collector |
| BUG-006 | Medium | Compose stability | `domain/.../models/Search.kt:52` | Small | High | `SearchParameters.equals` always returns `false`, defeats Compose skipping |
| BUG-007 | Medium | Latent flow bug | `feature/giveaways/.../GiveawaysViewModel.kt:46-56` | Trivial | Medium | `reloadGiveaways` flow has no `SUCCESS` emission path |
| BUG-008 | Low | Race | `feature/home/.../HomeViewModel.kt:83-97`, `feature/giveaways/.../GiveawaysViewModel.kt:41-63` | Trivial | Medium | Read-modify-write on `_uiState.value.copy(...)` instead of `update {}` |
| BUG-009 | Low | Compose stability | `domain/.../models/Giveaway.kt:129-133` | Small | Medium | `GiveawaySearchParameters` carries unstable `List<Pair<…>>` fields |
| BUG-010 | Low | Compose stability | `domain/.../models/Game.kt:32-39` | Small | Medium | `GameDetails.deals: List<GameDeal>` is unstable; defeats `CompactGameDetail`/`WideGameDetail` skipping |

---

## Findings (full detail)

### BUG-001: `DealDetailsController.load` swallows `CancellationException`

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

### BUG-002: `GiveawaysViewModel.loadGiveaway` does not cancel the original Room collector — concurrent collectors race onto `_uiState`

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

fun loadGiveaway(parameters: GiveawaySearchParameters) { parametersFlow.value = parameters }
```

**Confidence rationale.** Production `observeGiveaways()` returns a Room Flow that does not complete; the unit test (`GiveawaysViewModelTest.kt:102-103`) uses `flowOf(...)` (which completes) so the test does not exercise the race. Antipattern matches the one already fixed on `dev` for stores (PR #68).

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

**Impact.** Multiple retry taps trigger N parallel network calls (`gamesRepository.getGameDetails`); whichever completes last sets the UI state. The init's `flatMapLatest` cancels its own re-runs but cannot cancel the orphan launches from `reloadGameDetails`. A late-arriving error from a previous retry can clobber a successful reload, or vice versa.

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
        loadGameDetailsFlow(gameId).collect { _uiState.emit(it) }
    }
}
```

**Recommended fix.** Drive retries through the same source-of-truth flow:
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

**Confidence rationale.** Identical antipattern to PR #68 (`loadTopStoresDeals`); the corrected discipline already exists in `HomeViewModel` via `loadJob?.cancel()`.

---

### BUG-004: `SingleEventEffect` captures collector lambda without `rememberUpdatedState`

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Compose effect captures stale lambda |
| **Location** | `common/src/main/java/pm/bam/gamedeals/common/CommonFlowExtensions.kt:80-92` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** The helper keys its `LaunchedEffect` only on `sideEffectFlow` and captures the `collector` lambda directly. If the parent recomposes with a `collector` that closes over different state, the new lambda is **not** observed — the coroutine still calls the lambda captured at first launch. The KDoc does not warn callers.

**Impact.** Today's only call site (`HomeScreen.kt:115-119`) is safe by accident because `goToGame` resolves to a stable `remember(navController)`-captured reference. Any future caller closing over screen-local state in its `collector` will silently fire stale callbacks (wrong navigation target, wrong analytics payload, etc).

**Evidence.**
```kotlin
LaunchedEffect(sideEffectFlow) {
    lifecycleOwner.repeatOnLifecycle(lifeCycleState) {
        sideEffectFlow.collect(collector)   // captured-at-first-launch
    }
}
```

**Recommended fix.**
```kotlin
val currentCollector by rememberUpdatedState(collector)
LaunchedEffect(sideEffectFlow, lifecycleOwner, lifeCycleState) {
    lifecycleOwner.repeatOnLifecycle(lifeCycleState) {
        sideEffectFlow.collect { currentCollector(it) }
    }
}
```

**Confidence rationale.** Textbook D7 pattern. Latent today only because the existing call chain uses stable references.

---

### BUG-005: `GiveawaysViewModel.reloadGiveaways` does not cancel the in-flight Room collector — `LOADING` flickers and races with stale `SUCCESS`

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

**Recommended fix.** Resolved naturally by BUG-002's fix — once the source-of-truth is a single `flatMapLatest`-driven collector, `reloadGiveaways()` only needs to flip status to LOADING via `_uiState.update { it.copy(...) }` and call `refreshGiveaways()` while letting the existing collector pick up new rows.

**Confidence rationale.** Direct consequence of two collectors being alive at once; same root cause as BUG-002 but a distinct user-visible symptom.

---

### BUG-006: `SearchParameters.equals` always returns `false` defeats Compose skipping

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Unstable parameters causing recomposition storms |
| **Location** | `domain/src/main/java/pm/bam/gamedeals/domain/models/Search.kt:52` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `SearchParameters` is a `@Serializable data class` whose `equals` is overridden to *unconditionally return `false`*:
```kotlin
override fun equals(other: Any?): Boolean = false
```
The same instance is then passed through three Compose layers: `SearchScreen` → `Screen(existingSearchParameters = …)` (`SearchScreen.kt:114`) → `SearchFilters(existingSearchParameters = …)` (`:197`) → `Filters(existingSearchParameters)` (`:322`). With `equals == false`, every parameter comparison fails, so every parent recomposition forces every child that takes a `SearchParameters` to recompose.

**Impact.** The `Filters` bottom-sheet (two `Slider`s + `Switch` + label rebuilds) recomposes on every state change in `searchData`, even with identical filters. Compose's skipping mechanism is defeated for any composable taking `SearchParameters`. Author's own comment asserts the rationale: avoiding `StateFlow` strong-equality conflation — but solving that at the model layer breaks `equals` for the entire Compose tree.

**Recommended fix.** Solve the `StateFlow` conflation at the flow boundary, not on the type. Either (a) replace the `StateFlow` with a `MutableSharedFlow` that doesn't conflate, or (b) wrap each emission in a unique sentinel (e.g. `Pair<Long, SearchParameters>`) so the envelope differs while the inner value retains structural equality. Restore data-class `equals`.

**Confidence rationale.** The override is explicit and the type is concretely passed as a Compose parameter at multiple call sites.

---

### BUG-007: `GiveawaysViewModel.reloadGiveaways` flow has no `SUCCESS` emission path

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Flow operator ordering / latent bug |
| **Location** | `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:46-56` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** Inside `reloadGiveaways()`, `flow { emit(LOADING); refreshGiveaways() }` emits one value then completes (no second `emit`). The downstream `.collect { _uiState.emit(it) }` therefore only ever observes the LOADING value; the success path of `refreshGiveaways()` produces no `SUCCESS` emission from this flow — it relies on the init-collector picking it up (the racing collector from BUG-005). If you adopt the BUG-002 fix that retires the init collector, you must also restructure this so a successful refresh leaves the UI in a non-LOADING state.

**Impact.** After the BUG-002 refactor, this method as written would leave the UI stuck on LOADING after a successful refresh. Today the bug is masked by the parallel init collector.

**Recommended fix.** Don't `collect` here at all — call `refreshGiveaways()` and bracket it with `_uiState.update { it.copy(status = LOADING) }` before and a try/catch flipping to ERROR on failure; let the source-of-truth collector emit SUCCESS when Room delivers the new rows.

**Confidence rationale.** Confidence is Medium because the bug only manifests after BUG-002 is fixed. Today the missing SUCCESS is silently filled in by the init collector. **Fix BUG-002, BUG-005, and BUG-007 together** — they share root cause.

---

### BUG-008: Read-modify-write on `_uiState.value.copy(...)` across concurrent coroutines

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (lost update) |
| **Location** | `feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt:83,91,96,97`; `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:41,49,53,63` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** Multiple sites read `_uiState.value` and emit a copy without using `MutableStateFlow.update { ... }`. Concurrent coroutines (long-lived init collector + a user-triggered `onReleaseGame` / `loadGiveaway` / `reloadGiveaways`) can read the same snapshot, mutate independent fields, and clobber each other on emit.

**Impact.** A field-level update made by coroutine A can be overwritten when coroutine B emits its `_uiState.value.copy(...)` based on a stale snapshot. Visible as occasional missing status changes.

**Recommended fix.** Use `MutableStateFlow.update { current -> current.copy(...) }`, the documented atomic CAS helper.

**Confidence rationale.** Race is real but only widens if the other concurrency bugs (BUG-002, BUG-003) remain. Once a single source-of-truth collector exists, most racing emit sites disappear.

---

### BUG-009: Unstable `List<Pair<…>>` fields make `GiveawaySearchParameters` unskippable

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Unstable parameters causing recomposition storms |
| **Location** | `domain/src/main/java/pm/bam/gamedeals/domain/models/Giveaway.kt:129-133` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `GiveawaySearchParameters` carries `platforms: List<Pair<GiveawayPlatform, Boolean>>` and `types: List<Pair<GiveawayType, Boolean>>` — both raw `kotlin.collections.List`, not `ImmutableList`. The class has no `@Immutable`/`@Stable`. Used as a Compose parameter in `GiveawaysScreen.kt:128, :265, :290, :357`.

**Impact.** Each recomposition of `GiveawaysScreen` (e.g. when `uiState.giveaways` updates while filters are open) forces all four downstream composables to rebuild even when parameters are bit-identical. Same class of issue addressed by PR #70 — these filter parameters were missed. Bounded in practice because `Filters` only mounts while the modal sheet is open.

**Recommended fix.** Type both fields as `ImmutableList<Pair<…, Boolean>>` (`kotlinx.collections.immutable`) and replace `.toMutableList().map { … }` with `… .map { … }.toImmutableList()`. Optionally annotate the class `@Immutable`.

**Confidence rationale.** Type-stability rules are deterministic; "Medium" rather than "High" because the bounded mount lifetime limits real-world cost.

---

### BUG-010: `GameDetails.deals: List<GameDeal>` defeats skipping of `CompactGameDetail` / `WideGameDetail`

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Unstable parameters causing recomposition storms |
| **Location** | `domain/src/main/java/pm/bam/gamedeals/domain/models/Game.kt:32-39`; consumed at `feature/game/.../GameScreen.kt:147,193` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `GameDetails` has `deals: List<GameDeal>` (raw `List`, not `ImmutableList`) and no `@Immutable`. Compose marks the type unstable. `CompactGameDetail(gameDetails: GameDetails)` and `WideGameDetail(gameDetails: GameDetails)` therefore cannot skip when the parent recomposes with the same value. The outer `dealDetails: ImmutableList<Pair<Store, GameDetails.GameDeal>>` in `GameScreenData.Data` is correctly typed (PR #70), but the inner `gameDetails.deals` remained unstable.

**Recommended fix.** Type `deals` as `ImmutableList<GameDeal>`; annotate `@Immutable` once true. Same treatment for any other domain `List` field fed to composables.

**Confidence rationale.** Same mechanism as BUG-009; bounded impact because `GameScreen` is mostly static once data has loaded.

---

## Specialists that found nothing

- **Lifecycle leaks** — 0 findings. Single-Activity Compose app with `@ApplicationContext`-only Hilt provisions, no Fragments in use, zero callback-registration sites, WebView destroy fix (PR #67) verified.
- **Main-thread violations** — 0 findings. All Room DAOs are `suspend`/`Flow`, Retrofit endpoints are `suspend`, `SettingStorage` wraps SharedPreferences in `withContext(ioDispatcher)`, no synchronous heavy work in `Application.onCreate`, `Activity.onCreate`, or any ViewModel `init {}`.
- **Resource leaks** — 0 findings. No raw `Cursor` / `InputStream` / `OutputStream` / `OkHttp Response` / `TypedArray` / `ContentProviderClient` / `Bitmap.recycle` / `MediaPlayer` / `SQLiteDatabase` usage. Room transactions go through `withTransaction { }`.

## Notes and limitations

- **Not scanned:** test sources (`src/test`, `src/androidTest`), native code, and `.claude/worktrees/**` (throwaway agent worktrees).
- **Out-of-scope observation noted by Compose specialist (not filed):** `feature/search/.../SearchScreen.kt:340` — `existingHighest = existingSearchParameters.upperPrice?.toFloat() ?: priceLowest` looks like a logic bug (falls back to the *lower* bound when `upperPrice` is null). Worth a separate look.
- **Cluster-fix recommendation:** BUG-002, BUG-005, BUG-007 (and partially BUG-008) all stem from the same `GiveawaysViewModel` antipattern — fixing them in one PR yields the cleanest result. The shape of the fix mirrors PR #68 for stores.
- **Severity calibration note:** No Critical findings. Three High items are all race/cancellation defects with concrete user-visible symptoms (UI flips, multiplied network calls, fatal-logged cancellations). Effort is Trivial-to-Small across the board — these are quick wins.
