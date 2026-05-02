# Findings — Lifecycle / Leak Hunter

Hunter: `android-bug-hunting-lifecycle-leak-hunter`
Date: 2026-05-02
Branch HEAD: `wave/2026-05-02-bug-hunt/issue-75-77-giveaways-refresh-outcome`

Found 3 findings (0 Critical, 0 High, 2 Medium, 1 Low).

## Scope summary

Single-Activity Compose application (`MainActivity` is the only Activity, hosting `setContent { NavGraph() }`). No Fragments in production (`LoggingBaseFragment` is unused), no `BroadcastReceiver`, no `getSystemService` registrations, no `Handler.postDelayed`, no `bindService`, no Cursor/Stream class fields, no `WeakReference` misuse, and no custom `CoroutineScope` outside `viewModelScope` / `coroutineScope`. All DI-injected `Context` is annotated `@ApplicationContext`. ViewModels do not hold `Context`/`Activity`/`View`/`Fragment`. Compose state collection consistently uses `collectAsStateWithLifecycle`. WebView teardown in `feature/webview/.../ui/WebView.kt` (PR #30/#67) is correct.

The findings below are real bugs that remain at HEAD. Significant overlap with the coroutine/Flow hunter — see dispatcher report for deduplication.

---

### BUG-001: `GiveawaysViewModel` accumulates duplicate Room collectors per filter / reload

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Lifecycle — uncancelled coroutine collectors accumulating in `viewModelScope` (race + bounded leak) |
| **Location** | `feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:36-66` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `GiveawaysViewModel` launches a long-lived collector on `viewModelScope` in its `init { … }` block (line 37) that subscribes to `giveawaysRepository.observeGiveaways()`. Every call to `loadGiveaway(parameters)` (line 59) launches another top-level coroutine that subscribes to a second `observeGiveaways(...)` flow with filter parameters. The previously-launched collector is never cancelled, so subscribers accumulate on each filter change. Likewise, `reloadGiveaways()` (line 47) launches yet another fire-and-forget coroutine emitting to the same `_uiState`. This is exactly the pattern PR #33 fixed in `HomeViewModel`; `GiveawaysViewModel` was not migrated.

**Impact.** Stale-collector accumulation: each filter change adds a permanent subscriber until the VM is cleared. Multiple subscribers race to write `_uiState` on every Room emission. After N filter changes, a Room update triggers N+1 parallel mappers. Filtered collector contradicts the original unfiltered `init` collector. Bounded by VM lifetime but accumulating during the screen session.

**Evidence.**
```kotlin
// feature/giveaways/.../GiveawaysViewModel.kt:36-66
init {
    viewModelScope.launch {                                   // collector #1, lives forever
        flow { emitAll(giveawaysRepository.observeGiveaways()) }
            …
            .collect { _uiState.emit(it) }
    }
}

fun reloadGiveaways() { viewModelScope.launch { … .collect { _uiState.emit(it) } } }

fun loadGiveaway(parameters: GiveawaySearchParameters) {
    viewModelScope.launch {                                   // collector #N, never cancelled
        flow { emitAll(giveawaysRepository.observeGiveaways(parameters)) }
            …
            .collect { _uiState.emit(it) }
    }
}
```

**Recommended fix.** Mirror PR #33's `HomeViewModel` pattern (`private var loadJob: Job?` cancelled before relaunch), or drive the active flow off a `MutableStateFlow<GiveawaySearchParameters?>` + `flatMapLatest` (the pattern in `StoreViewModel`/`SearchViewModel`/`GameViewModel`).

**Confidence rationale.** Direct read of three `viewModelScope.launch { … .collect { … } }` blocks with no cancellation between them. Repository's `observeGiveaways*()` returns a Room `Flow` (cold) — each `.collect` is a distinct DB observer.

---

### BUG-002: `GameViewModel.reloadGameDetails` launches a new collector without cancelling the prior one

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Lifecycle — uncancelled coroutine collector |
| **Location** | `feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt:62-68` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** `reloadGameDetails()` is wired to the user's "Retry" snackbar action (`GameScreen.kt:351 currentOnRetry()`). Each retry launches a fresh `viewModelScope.launch { loadGameDetailsFlow(gameId).collect { … } }` without cancelling the prior one. If the user taps Retry repeatedly while a slow network call is pending, multiple network requests + `_uiState` writers run in parallel and may emit out of order. The `init` collector also writes `_uiState` for the same `gameIdFlow`.

**Impact.** Bounded leak (cancelled at `onCleared()`), unbounded accumulation within a session if the user spams retry. Visible as "Loading" flicker or stale `Error` after a successful retry.

**Evidence.**
```kotlin
// feature/game/.../GameViewModel.kt:62-68
fun reloadGameDetails() {
    val gameId = gameIdFlow.value ?: return
    viewModelScope.launch {                          // not cancelled on subsequent calls
        loadGameDetailsFlow(gameId)
            .collect { _uiState.emit(it) }
    }
}
```

**Recommended fix.** Either store and cancel `private var reloadJob: Job?`, or — preferably — trigger reload by re-emitting on `gameIdFlow` (e.g. drop+re-emit, or turn `gameIdFlow` into a `Channel`/`SharedFlow` of "load tickets") so the existing `flatMapLatest` in `init` cancels the previous attempt.

**Confidence rationale.** Pattern matches PR #33's fixed bug. Lower confidence than BUG-001 only because retry is human-rate (less likely to actually overlap).

---

### BUG-003: `DealDetailsController.load` launches a new coroutine without cancelling prior load

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Lifecycle — uncancelled coroutine collector |
| **Location** | `common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt:23-71` (callers: `feature/store/.../StoreViewModel.kt:72-74`, `feature/home/.../HomeViewModel.kt:103-105`); same pattern in `feature/deal/.../DealDetailsViewModel.kt:34-78` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** `DealDetailsController.load(scope, …)` is fire-and-forget: it launches `scope.launch { … }` that emits `DealDetailsLoading` then `DealDetailsData`/`DealDetailsError` to `_dealDetails`. No tracking job is kept, so successive calls overlap. If the user taps a different deal card before the previous load finishes, both coroutines complete and the bottom sheet may briefly show the older deal's data before the newer one wins. `StoreViewModel` and `HomeViewModel` discard the returned `Job`.

**Impact.** Race + brief UI flicker showing stale deal info when the user switches deals quickly while a previous fetch is still pending. No memory leak beyond `viewModelScope`.

**Evidence.**
```kotlin
// common/ui/.../DealDetailsController.kt:23-71
fun load(scope: CoroutineScope, dealId: String, …): Job = scope.launch {
    try {
        _dealDetails.emit(DealBottomSheetData.DealDetailsLoading(...))
        val data = withMinimumDuration(750L) { … }
        _dealDetails.emit(data)                              // no guard against newer load
    } catch (t: Throwable) { … }
}
```

**Recommended fix.** Track the in-flight `Job` on `DealDetailsController` and cancel it in `load(...)` before launching the new one.

**Confidence rationale.** Reading the source confirms no cancellation. Lower confidence on user visibility — marked Low because the consequence is brief visual stale data, not a leak past VM destruction.
