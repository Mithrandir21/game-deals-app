# Lessons Learned

Condensed, structured lessons from past development sessions. Claude reads this file at the start of each session (via a separate skill) so it can apply past learnings without re-deriving them.

Each lesson has an immutable ID. When a lesson is superseded or turns out to be wrong, it is moved to `## Archive` with an updated `Status` line — its content is never rewritten. This preserves the audit trail.

**Claude:** apply lessons from `## Active` only. Consult `## Archive` only if something appears contradictory and you need the history.

## Active

### L-2026-05-02-10 · Re-verify findings against `origin/<merge-target>` HEAD before working them
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** workflow, planner-agents, worker-agents, merge-target-drift, github-sync, github-issue-waves
**Applies to:** Planner/worker sub-agents in `github-issue-waves`; bug-hunt-to-issues filing in `github-sync`; any flow that reads issues filed from a feature/wave branch and acts on them later

Issues filed from a wave/feature branch can become partially or fully obsolete by the time a worker picks them up — `dev` moves between filing and execution. Issue #90 is the canonical example: the `DealDetailsController` portion was already fixed on `dev` (commit `b64307b`) when the worker started; only the `DealDetailsViewModel` portion remained real. Without the planner re-verifying, the worker would have proposed a no-op or duplicated the existing fix.

Discipline at three layers:

- **`github-sync` (filing-time):** Step 2.5 (added 2026-05-02) extracts a signature from each finding's Evidence block and substring-matches it against `git show origin/<merge-target>:<path>`. Findings whose code is already fixed on the merge target default to skip in the triage plan.
- **`github-issue-waves` planners:** when reporting file lists for a candidate issue, read `origin/<merge-target>:<path>` for each cited path. If the antipattern is gone, narrow the issue's scope or surface a `partially-fixed` flag in the planner's YAML output so the orchestrator can warn the user.
- **Workers (execution-time):** before editing, diff the issue's cited Evidence against current `dev` HEAD. Narrow scope to portions still real, and call out the narrowing in the PR body so the reviewer doesn't expect changes the issue text implied.

The signature-line check is fuzzy on purpose — exact line matches break under whitespace/refactor drift. Treat the verifier's verdict as advisory; the human's veto on the triage plan is the source of truth.

**Source:** Wave 1 of campaign `2026-05-02-bug-hunt-2` (issue #90 → PR #94); pairs with the `Step 2.5 — Verify each finding against the merge target` section in `.claude/skills/android-bug-hunting-github-sync/SKILL.md`

### L-2026-05-02-09 · Wall-clock time in suspend helpers breaks test virtual time
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** coroutines, test-virtual-time, runtest, dispatchers, common-helpers
**Applies to:** Any `coroutineScope`-shaped helper in `:common` (or any module) that pads a minimum duration / debounce floor / rate-limit floor; new "minimum loading time" / "settle" / "throttle" utilities

A helper that measures `System.currentTimeMillis()` deltas and pads via `delay(...)` mixes wall-clock and virtual-clock domains. Under `runTest`, `delay` is virtualized but `currentTimeMillis()` is real wall-clock — so `elapsed` reads as ~0 (the test scheduler skipped real time), and the helper pads the *full* duration against real time. Tests slow to wall-clock pace and lose determinism, and any consumer of the helper is impossible to advance past via `testScheduler.advanceTimeBy(...)`.

Fix shape (matches the operators in this codebase already):
```kotlin
coroutineScope {
    val pad = launch { delay(delayMillis) }
    val result = block()
    pad.join()
    result
}
```

The pad coroutine and `block()` both run on the same `TestDispatcher` under `runTest`, so the whole thing is virtual. `inline`/`crossinline` semantics survive this rewrite. The user-perceived behaviour is unchanged: `pad.join()` after `block()` returns enforces the same minimum total duration in production.

Audit checklist when adding a new "minimum duration"-style helper:
1. No `System.currentTimeMillis()`, `Instant.now()`, `Clock.systemDefaultZone()`, or `nanoTime()` inside the suspend body.
2. All padding uses `delay(...)` directly (or `launch { delay(...) }.join()` when `block()` runs in parallel).
3. A test using `runTest { }` + `testScheduler.currentTime` reads at least the configured minimum after the helper returns instantly — without taking real wall-clock time to do so.

**Source:** Wave 1 PR #59 (issue #45 — `mapDelayAtLeast` / `flatMapLatestDelayAtLeast`); Wave 1 PR #93 (issue #91 — `withMinimumDuration` was the missed companion in the same pattern)

### L-2026-05-02-08 · `combine`-with-trigger flows: reason explicitly about write-order between trigger updates and downstream `_uiState` mutations
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** stateflow, combine, viewmodel, race-conditions, ordering, source-of-truth
**Applies to:** Feature ViewModel handlers that both (a) update an upstream `MutableStateFlow`/`MutableSharedFlow` feeding a `combine(...)` source-of-truth flow, AND (b) directly write `_uiState.update {}` or `_uiState.emit(...)` in the same function body

When a function does both — write upstream of `combine`, AND write `_uiState` directly — the upstream write synchronously propagates through `combine` and reaches the downstream collector before the function continues. The order of the two writes therefore determines which one wins. In `GiveawaysViewModel.reloadGiveaways()`:

```kotlin
fun reloadGiveaways() = viewModelScope.launch {
    refreshOutcomeFlow.value = RefreshOutcome.Idle    // (1) trigger upstream
    _uiState.update { it.copy(status = LOADING) }     // (2) direct downstream write
    flow<Unit> {
        giveawaysRepository.refreshGiveaways()
    }
        .catch { refreshOutcomeFlow.value = RefreshOutcome.Error }
        .collect { }
}
```

If the previous outcome was `Error`, write (1) causes `combine` to re-emit `SUCCESS` to `_uiState` first; write (2) then overwrites it with `LOADING`. The reverse order would have `LOADING` overwritten by `combine`'s `SUCCESS`. **Pattern:** when a `combine` upstream feeds the same `_uiState` you're directly writing, do upstream resets *before* direct writes. If the upstream feed is via a hot collector inside `init {}`, you can't sequence around it — the discipline applies regardless.

**Source:** Wave 2 PR #89 (issues #75/#77 — `RefreshOutcome` flow combined into `GiveawaysViewModel` source-of-truth)

### L-2026-05-02-07 · `StateFlow` conflation of identical-equals emissions: fix at the flow boundary, not by breaking `equals`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** stateflow, sharedflow, conflation, compose, stability, equals-override, anti-pattern
**Applies to:** Any `MutableStateFlow<T>` whose producer needs identical-equals successive emissions to *still* trigger downstream — typically when downstream is `flatMapLatest`, a `LaunchedEffect` keyed on the value, or any `combine`/`zip` that should re-fire on every "user-pressed-go-again"

`StateFlow` conflates by structural equality and silently drops re-emissions of the same value. The wrong fix is to override `equals` on the model type to always return `false` — that destroys Compose stability for every composable taking the type (Compose's skipping mechanism relies on `equals`), and breaks any future `LaunchedEffect` keyed on the value. The right fix is at the producer: `MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)` preserves replay-1 for late subscribers, doesn't conflate, and leaves the model type Compose-stable. Alternatively, wrap emissions in a sentinel envelope (`Indexed<T>`, `Pair<Long, T>`) so the envelope differs while the inner value retains structural equality.

```kotlin
// before — broken
private val parametersFlow = MutableStateFlow(SearchParameters())  // re-emits same params dropped
// SearchParameters had: override fun equals(other: Any?) = false  // destroys Compose stability

// after
private val parametersFlow = MutableSharedFlow<SearchParameters>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
// data class SearchParameters(...) — equals restored to data-class equals; Compose-stable
```

Note: `MutableSharedFlow(replay=1)` has empty replay until the first `emit`, whereas `MutableStateFlow(initial)` synthesises an initial emission. If the consumer chain depended on that synthetic null/initial first emission, you'll need an explicit `.onStart { emit(initial) }` or a `replayCache.firstOrNull() ?: initial` read.

**Source:** Wave 2 PR #88 (issue #76 — `SearchParameters.equals = false` defeated Compose skipping); pairs with L-2026-05-02-05 (test-side analogue: `flowOf` vs `MutableSharedFlow` for hot sources)

### L-2026-05-02-06 · Compose `LaunchedEffect` capturing a caller-provided lambda must wrap it in `rememberUpdatedState`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** compose, launchedeffect, rememberupdatedstate, stale-capture, lambda-capture
**Applies to:** Any helper of the shape `LaunchedEffect(key) { … callback(it) }` (or `repeatOnLifecycle { … callback(it) }`) where `callback` arrives via composable parameter and the parent is expected to recompose

`LaunchedEffect`'s coroutine is launched once per key-change and captures whatever lambda values were in scope at launch time. If the lambda comes from a recomposing parent and closes over screen-local state, the coroutine permanently fires the *first* version even after the parent recomposes with an updated closure — silently invoking stale callbacks (wrong navigation target, wrong analytics payload, etc). The fix is to wrap the parameter in `rememberUpdatedState` and call the snapshot through that handle:

```kotlin
val currentCollector by rememberUpdatedState(collector)
LaunchedEffect(sideEffectFlow, lifecycleOwner, lifeCycleState) {
    lifecycleOwner.repeatOnLifecycle(lifeCycleState) {
        sideEffectFlow.collect { currentCollector(it) }
    }
}
```

This is a textbook D7 idiom and applies symmetrically to `DisposableEffect` (cleanup-callback variant). Wrap inside the helper rather than relying on every caller to pass a stable reference — the safety contract belongs to the helper, not the call site. (The repo's `SingleEventEffect` previously was safe-by-accident because its only call site, `HomeScreen`, used a `remember(navController)`-stable reference; that's a fragile property of the chain, not of the helper.)

**Source:** Wave 1 PR #85 (issue #74 — `SingleEventEffect` captures `collector` lambda without `rememberUpdatedState`)

### L-2026-05-02-05 · Hot-source races need `MutableSharedFlow` in tests, not `flowOf(...)` — `flowOf` completes after one emission and masks second-emission bugs
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** testing, coroutines, flow, room, race-conditions, test-fixtures
**Applies to:** Test stubs for repository methods that return a hot `Flow` in production (Room `@Query` flows, `MutableSharedFlow`-backed signals, `callbackFlow`-driven sources) — i.e. anywhere the production flow does not complete

The `GiveawaysViewModelTest` originally stubbed `observeGiveaways()` with `flowOf(listOf(...))`, which completes after exactly one emission. Production's Room flow emits *every time the table is invalidated* — never completing. The parallel-collector race in `GiveawaysViewModel.loadGiveaway` (issue #72) needed a *second* emission to manifest, so the test never reproduced the bug despite covering the call site. Default test stub for hot sources should be `MutableSharedFlow` (or the equivalent `flow { while (isActive) emitAll(channel) }` shape) so tests can drive multiple emissions and exercise race-condition behaviour. Reserve `flowOf(...)` for fixtures that are genuinely terminal/one-shot (e.g. `flow.first()` consumers).

This is a structural test-shape rule, not a per-bug fix — applying it eagerly across `feature/*/src/test/.../ui/*ViewModelTest.kt` files would surface other latent races. Pairs with L-2026-05-01-07 (which governs the *expected emission count* under `UnconfinedTestDispatcher`).

**Source:** Wave 1 PR #86 (issue #72 — `GiveawaysViewModel.loadGiveaway` leaks long-lived Room collector)

### L-2026-05-02-04 · `try { ... } catch (Throwable)` blocks containing suspending work must rethrow `CancellationException` first
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** coroutines, cancellation, structured-concurrency, error-handling, crashlytics
**Applies to:** Any `try { … } catch (t: Throwable) { … }` whose catch body suspends (calls `emit`, `withContext`, `delay`, etc.) — or logs as fatal — inside `viewModelScope`, `lifecycleScope`, or any structured-concurrency scope where the host can be cancelled mid-work

Kotlin coroutines signal cancellation by throwing `CancellationException`, which is a `Throwable`. A bare `catch (t: Throwable)` swallows it: structured concurrency breaks (parent doesn't see the cancellation), the catch body runs to completion (emitting state, calling Crashlytics fatal, etc.), and the user-visible result is "navigation away from the screen logged a Crashlytics fatal and overwrote the cancellation state." The discipline is to rethrow `CancellationException` first:

```kotlin
} catch (t: CancellationException) {
    throw t
} catch (t: Throwable) {
    fatal(logger, t)
    _state.emit(...)
}
```

Apply at every catch site that meets the criterion above, including nested fallback catches. The pattern is already established in this repo at `DealsMediator.kt:76-81`; #71 backfilled `DealDetailsController` to match. **Auditing for other `catch (Throwable)` sites with suspend bodies is high-value** — each one is a latent cancellation-swallow.

**Source:** Wave 1 PR #84 (issue #71 — `DealDetailsController.load` swallows `CancellationException` across both catch sites)

### L-2026-05-02-03 · Gradle `implementation` is non-transitive on compile classpath: when a `:domain` type adopts a third-party type/extension, consumer modules need the dep added explicitly
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** gradle, modularization, kotlinx-collections-immutable, dependency-graph, build-config
**Applies to:** Any refactor that retypes a public field on a `:domain` (or other shared) data class to use a third-party type — `ImmutableList<T>`, `Instant`, `BigDecimal`, etc. — where consumer modules will call extension functions on the type

Gradle `implementation(libs.kotlinx.collections.immutable)` in `:domain` is enough for `:domain` itself to compile, but it does NOT propagate the dep to modules that depend on `:domain`. The *type* (`ImmutableList<GameDeal>`) flows through the public API surface fine — that's what `api` vs `implementation` controls — but **extension functions** on that type (`kotlinx.collections.immutable.toImmutableList`, `.persistentListOf`, etc.) are imported from the third-party library directly, and that import only resolves if the consumer module declares the dep itself. Symptom: `:domain` test runs green, but `:remote:cheapshark` (or `:feature:game`, or any other module that builds a value of the new type via an extension) fails compile with `Unresolved reference: toImmutableList`. Fix: add `implementation(libs.kotlinx.collections.immutable)` to every consumer module that imports an extension on the type. **Planner heuristic:** when retyping a `:domain` field, walk the import-graph of consumer modules and pre-add the dep — don't wait for the build to surface it module-by-module.

Same gotcha will apply to any future migration of this shape (e.g. `kotlinx.datetime.Instant` if/when it spreads, or a hypothetical `:domain` adoption of `Arrow`'s `Either`).

**Source:** Wave 2 PR #83 (issue #80 — GameDetails.deals → ImmutableList migration); also surfaced in PR #82 (issue #79) where `:feature:giveaways` already had the dep so the gotcha was masked

### L-2026-05-02-02 · `@Immutable` + `ImmutableList<…>` on every domain model used as a composable parameter
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** compose, stability, recomposition, immutable-collections, kotlinx-serialization
**Applies to:** `data class` types in `:domain` whose fields include raw `kotlin.collections.List<…>` and that are read inside a composable (or nested inside another type that is)

Compose marks raw `List` unstable, so any composable taking such a type can't skip on recomposition even when the value is bit-identical. Pattern: retype every `List<…>` field to `kotlinx.collections.immutable.ImmutableList<…>`, annotate the class `@Immutable`, and replace `.toMutableList().map { … }` (which produces a regular `List`) with `.map { … }.toImmutableList()` at every call site. The catalog already pins `kotlinx-collections-immutable` (since #38); add `implementation(libs.kotlinx.collections.immutable)` to whichever module needs it (`:domain`, `:common:ui`, `feature/*`).

**Important:** kotlinx-serialization 1.9.0 (the catalog version) supports `ImmutableList` natively — `@Serializable` domain models adopt it without a custom serializer, and `Saver` round-trips through `Json.encodeToString` keep working. **Operational note:** any two issues that both add `kotlinx-collections-immutable` to the same module's `build.gradle.kts` will conflict — schedule them in separate waves of the bug-hunt campaign.

**Source:** Wave 1 PR #82 (issue #79 — GiveawaySearchParameters @Immutable + ImmutableList migration); also #38 (PR #70) and pending #80

### L-2026-05-02-01 · `MutableStateFlow.update { it.copy(...) }` for field-level merges; `emit(...)` only for full-state replacements
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** viewmodel, stateflow, coroutines, race-conditions, atomicity
**Applies to:** Feature ViewModel handlers in `feature/*/src/main/.../ui/*ViewModel.kt` that mutate `_uiState`

Three patterns coexist in this codebase, and they are not interchangeable. (1) `_uiState.value.copy(...)` followed by `_uiState.emit(...)` is a read-modify-write — concurrent coroutines can read the same snapshot, mutate disjoint fields, and clobber each other on emit. Always replace with `_uiState.update { it.copy(...) }`, the documented atomic CAS helper that retries on contention. (2) `_uiState.update {}` works *inside* Flow chains too: `.onStart { _uiState.update { it.copy(state = LOADING) } }` is the right shape, not `.onStart { _uiState.emit(_uiState.value.copy(...)) }`. (3) Keep `.collect { _uiState.emit(it) }` *as-is* when the upstream Flow already produces the complete next state — that's a full-state replacement, not a field-level merge, and there's no race to fix.

Side note: when the LOADING transition moves out of a flow chain into a side-effect `update {}`, the chain now emits nothing — `logFlow` may need an explicit `flow<Unit>` type pin to keep inference happy.

**Source:** Wave 1 PR #81 (issue #78 — ViewModels read-modify-write `_uiState.value.copy(...)`)

### L-2026-05-01-09 · `AndroidView` lifecycle: hoist clients via `remember`, wire `onRelease` for true teardown
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** compose, androidview, webview, lifecycle, memory-leak
**Applies to:** Any `AndroidView { }` wrapping a stateful native view (`WebView`, `MapView`, `VideoView`, `ExoPlayer`'s `PlayerView`) — anywhere the view holds session/network/JS state that must be cleaned up when the composable leaves composition

`AndroidView`'s `factory` runs once when the view first enters composition, but `update` runs on every recomposition — so anything constructed inside `factory` (e.g. a `WebViewClient`) is fine for identity but anything constructed inside `update` is reborn each frame. Hoist clients into `remember { }` outside the `AndroidView` and assign them in `factory`. More importantly, **`AndroidView` does not destroy the underlying view when the composable leaves composition** — without an explicit `onRelease`, a `WebView` lingers with its full session, JS engine, network stack, and listeners until GC. Wire `onRelease` with the standard teardown sequence:

```kotlin
AndroidView(
    factory = { ctx -> WebView(ctx).apply { webViewClient = client; loadUrl(url) } },
    update = { /* ... */ },
    onRelease = { wv ->
        wv.stopLoading()
        wv.webChromeClient = null
        wv.webViewClient = WebViewClient()
        wv.loadUrl("about:blank")            // cancels in-flight network + JS before destroy
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
    },
)
```

The `loadUrl("about:blank")` is not cosmetic — it forces the WebView to abandon in-flight network and script execution before `destroy()` is called, which otherwise can leave threads hanging. Same shape applies to other resource-holding native views, adapted to their teardown API (`MapView.onDestroy()`, `ExoPlayer.release()`, etc.).

**Source:** Wave 3 PR #67 (issue #30 — WebView never destroyed when composable leaves composition)

### L-2026-05-01-08 · Use `ApplicationInfo.FLAG_DEBUGGABLE`, not `BuildConfig.DEBUG`, for one-off debug-only logic
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** android, agp, buildconfig, debug-gate, gradle
**Applies to:** Any module wanting a single "is this a debug build" boolean — `StrictMode` policies, debug-only logging, dev-only feature flags

This project's modules don't enable `android.buildFeatures.buildConfig`, so AGP 8 doesn't generate `BuildConfig` for them. Reaching for `BuildConfig.DEBUG` would force opting in to BuildConfig generation across that module just for one boolean (and add a build-script change with downstream effects on R8 metadata, Configuration Cache invalidation, etc.). Use `(applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` instead — equivalent semantics (debug build types implicitly set `debuggable=true`), no `buildConfig` opt-in. This is the pattern used in `GameDealsApplication.onCreate` to gate StrictMode (PR #63).

**Source:** Wave 1 PR #63 (issue #42 — Storage suspend / StrictMode gate)

### L-2026-05-01-07 · ViewModel emission tests under `UnconfinedTestDispatcher` should expect `size == 1` for steady state — not `[initial, current]`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** viewmodel, stateflow, coroutines, testing, unconfined-test-dispatcher
**Applies to:** Tests in `feature/*/src/test/.../ui/*ViewModelTest.kt` that collect a ViewModel's `uiState`/`screenState` flow into a list (e.g. `viewModel.uiState.take(N).toList()`) under `runTest` with the project's standard `UnconfinedTestDispatcher` rule

Under `UnconfinedTestDispatcher`, the ViewModel's `init { ... viewModelScope.launch { ... } }` block drains synchronously *before* the test starts collecting, and `MutableStateFlow` is conflated and replay-1. A correctly-shaped ViewModel therefore emits exactly one value to the test collector: the steady-state value after `init` finished. If a test asserts `size == 2, [initialValue, currentValue]`, that "second" emission is *not* the test catching a state transition — it is almost always (a) the spurious initial-flash from a `_uiState.stateIn(WhileSubscribed, initial)` derived StateFlow (the bug fixed in #37 / lesson L-2026-04-30-06), or (b) the test racing the dispatcher. Default expectation is `size == 1`. Larger sequences are only correct when the test *deliberately* drives a state transition during the collect window (e.g. user action, `LOADING` → `DATA` from a hand-fed fake). When in doubt, attach an inline comment naming the issue (`// see #37`) so a future reader doesn't roll the assertion back to match the buggy shape.

Three of six existing VM test files in this repo (`HomeViewModelTest`, `GiveawaysViewModelTest`, `StoreViewModelTest`) were anchored to the buggy two-value shape for months until #37 surfaced it; the other three (`SearchViewModelTest`, `GameViewModelTest`, `DealDetailsViewModelTest`) were already correct.

**Source:** Wave 2 PR #66 (issue #37 — six ViewModels redundant `stateIn`)

### L-2026-05-01-06 · In `:common:ui`, reach for the Activity via `LocalActivity.current` (null-safe) — never `view.context as Activity`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** compose, theme, activity, common-ui, preview-safety
**Applies to:** Code in `:common:ui` (and any other shared Compose module) that needs the host Activity to mutate window state — `WindowCompat`, `WindowInsetsController`, status-bar styling, `setRequestedOrientation`, etc.

`(view.context as Activity)` casts inside `LocalView.current` work in `MainActivity` but crash with `ClassCastException` in `@Preview`, `@PreviewParameter`, layout-inspector renders, and Robolectric/instrumented hosts that wrap the view in a non-Activity ContextThemeWrapper. Use `androidx.activity.compose.LocalActivity.current` (returns nullable `Activity?` — null in previews and tests) and skip the styling block when null. This was the fix in `GameDealsTheme`; the same pattern applies anywhere in `:common:ui` that needs Activity-typed access. **Project note:** `:common:ui` did not previously declare `androidx.activity:activity-compose` — it has to be added to `common/ui/build.gradle.kts` (the catalog already pins `activity-compose = 1.11.0`). Adding the dep is the right move: `LocalActivity` is the public, supported API since Activity 1.10; the cast is not.

**Source:** Wave 1 PR #61 (issue #41 — GameDealsTheme casts view.context as Activity)

### L-2026-05-01-05 · Don't mix `System.currentTimeMillis()` with `delay()` in Flow operators — `runTest` virtualizes only `delay`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** kotlin, coroutines, flow, testing, virtual-time
**Applies to:** Custom Flow operators in `common/.../FlowExtensions.kt` (and any new ones) that implement "at least N millis" / minimum-loading / rate-limit semantics

Under `kotlinx.coroutines.test.runTest` with a `TestDispatcher`, `delay(n)` advances the test scheduler's *virtual* time, but `System.currentTimeMillis()` keeps returning real wall-clock. If an operator measures elapsed time with `System.currentTimeMillis()` and then pads with `delay(remaining)`, the measured elapsed in tests is ~0, so the operator always pads the full window — every test asserting "at least N" passes by accident, and a regression where the inner work overran `N` would not be caught. This was the bug in `mapDelayAtLeast` / `flatMapLatestDelayAtLeast` / `latestDelayAtLeast` (production correct, tests blind). Pattern to use instead: launch the work and a `delay` in parallel inside a `coroutineScope`, then join both — all timing flows through `delay` and stays virtual-time-aware:

```kotlin
transform { v ->
    coroutineScope {
        val deferred = async { f(v) }
        val pad = launch { delay(delayMillis) }
        emit(deferred.await())
        pad.join()
    }
}
```

Generalization: never mix wall-clock measurement with `delay()` in coroutine code that may run under `TestDispatcher`. If you genuinely need real-time timing, inject a `kotlinx.datetime.Clock` (the project already has `CachedResource`-style Clock injection per L-2026-05-01-04's neighbor) and stub it in tests, rather than reaching for `System.currentTimeMillis()`.

**Source:** Wave 2 PR #59 (issue #45 — *DelayAtLeast operators virtual-time)

### L-2026-05-01-04 · Don't `.catch` after `.cachedIn` on a Paging Flow — let `LoadState.Error` surface load failures
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** paging, coroutines, flow, viewmodel, error-handling
**Applies to:** Feature ViewModels that expose `Flow<PagingData<T>>` to the UI via `collectAsLazyPagingItems()` — Store, Search, Giveaways, etc.

A `Pager` data Flow does not throw on load failures — those are surfaced as `LoadState.Error` on each `LoadResult`, and the UI's `LazyPagingItems.loadState.refresh/append/prepend` handlers (retry buttons, error rows) already key off them. What *can* throw upstream is the *construction* of the Pager Flow — `dealsRepository.getPagingStoreDeals(storeId)` and similar. A terminal `.catch { logger.fatalThrowable(it) }` placed *after* `.cachedIn(viewModelScope)` swallows that construction-time throwable without re-emitting, so `LazyPagingItems` is left pinned to the last-cached `PagingData` forever — no recovery, no retry, just a silent dead Flow. Removing the `.catch` lets Paging's own `LoadState` machinery handle load errors and lets construction errors propagate to `viewModelScope`'s exception handler where they're not silently buried. If you genuinely need to log construction failures separately, do it *before* `.cachedIn` (where `.catch` can re-emit a sentinel like `PagingData.empty()`), not after.

**Source:** Wave 1 PR #55 (issue #46 — StoreViewModel `.catch` after `.cachedIn`)

### L-2026-05-01-01 · Don't call `SavedStateHandle.toRoute<>()` inside ViewModels — use `savedStateHandle.get<Primitive>("propName")`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** navigation, compose, viewmodel, testing
**Applies to:** Feature ViewModels in this project that consume args seeded by typed Compose Navigation destinations (`composable<Destination.X>` / `navController.navigate(Destination.X(...))`)

`SavedStateHandle.toRoute<Destination.X>()` performs an internal `android.os.Bundle` round-trip that is unimplemented on plain JVM, so any unit test that constructs a ViewModel using it fails without an Android runtime. Avoid the call inside ViewModels: use `savedStateHandle.get<Int>("propName")!!` where `propName` matches the `@Serializable` property name on the `Destination` subclass — nav-compose 2.9 populates `SavedStateHandle` with property-name keys for primitive args, so end-to-end typing is preserved at the nav layer (registration + navigate calls stay typed) without dragging Android into ViewModel tests. `NavBackStackEntry.toRoute<>()` at the nav-boundary (composable lambda) is fine — that code only runs on Android.

**Source:** Wave 1 PR #50 (issue #23 — typed Compose Navigation)

### L-2026-05-01-02 · Robolectric is not used in this project — find a JVM-only path or move the test to `androidTest`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** testing, robolectric, project-policy
**Applies to:** Any decision to add a test dependency that brings an Android runtime into plain JVM unit tests

If a JVM unit test fails because some Android API is unimplemented (most commonly a `SavedStateHandle`/`Bundle` round-trip via `toRoute<>()`, or `android.os.*` stubs), do NOT add Robolectric. Find a JVM-only path: extract the Android-touching call out of the system under test, swap to a primitive-key read on the existing data structure, or move the test to instrumented (`androidTest`). The user has explicitly rejected Robolectric for this project — adding it again will be reverted.

**Source:** Wave 1 PR #50 review

### L-2026-05-01-03 · When dropping a public interface in `:domain` whose `@Inject` impl took internal-typed Daos, mark the renamed concrete class's constructor `internal`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** hilt, di, kotlin-visibility, multi-module
**Applies to:** Refactors that delete a public interface in `:domain` whose `@Inject` impl took `internal`-typed Daos / collaborators

While the public interface existed, the impl was free to keep a public constructor because Kotlin only checks visibility against the *declared* type and the API surface ran through the interface. Once the interface is removed, the renamed concrete class's `@Inject constructor(...)` becomes the public API surface and must NOT expose internal types — Kotlin fails compilation with "public function exposes its internal parameter type." Solution: declare it `@Inject internal constructor(...)`. The class stays public so feature modules can name the type for injection; the constructor is module-private so only Hilt's generated code in the same module can call it. This preserves the original `:domain` API narrowness — no Daos leaked.

**Source:** Wave 1 PR #49 (issue #16 — drop single-impl Repository interfaces)

### L-2026-04-30-06 · `_uiState.stateIn(WhileSubscribed, initial)` is the wrong shape — use `_uiState.asStateFlow()`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** viewmodel, stateflow, coroutines, compose
**Applies to:** New screen ViewModels in `feature/*` modules; copying the existing convention from Home/Store/Giveaways/Search/Game/DealDetails ViewModels.

The current convention `private val _uiState = MutableStateFlow(initial); val uiState = _uiState.stateIn(viewModelScope, WhileSubscribed(5000), initial)` is broken on two counts. (1) `MutableStateFlow` is already a hot, conflated, replay-1 StateFlow — `stateIn` produces a *second* derived StateFlow whose state machine is independent of `_uiState`. (2) Under `WhileSubscribed(5000)`, after subscribers drop, `uiState.value` returns the frozen last derived value, and new subscribers see `initialValue` for one frame even when `_uiState` has a different value — producing a spurious "LOADING" flash on resume after >5 s of backgrounding. Use `_uiState.asStateFlow()`. Tracked as issue #37 across all six existing ViewModels.

**Source:** android-bug-hunting-dispatcher audit (issues #30–#48)

### L-2026-04-30-05 · `flow { emitAll(repo.observeXxx()) }` is intentional — preserves synchronous-throw safety
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, repository, error-handling
**Applies to:** ViewModel code that consumes a repository `observeXxx()` Flow returned by `:domain` repositories

When you see `flow { emitAll(repo.observeXxx()) }.map{…}.catch{…}`, do not "simplify" the wrapper away. The repository's `observeXxx()` returns `Flow<…>` but can throw synchronously during construction (e.g., backing-store read failures surfaced before the first emission). Wrapping in `flow {}` converts that synchronous throw into an upstream exception that downstream `.catch {}` can handle; without the wrapper the throw escapes the `viewModelScope.launch` builder. Examples in `HomeViewModel.loadTopStoreDataFlow` / `loadNewReleases` / `loadGiveaways` and `GiveawaysViewModel.{init, loadGiveaway}`.

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-04 · Keep ViewModel functions Flow-shaped; don't lower to `viewModelScope.launch { try/catch }`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, viewmodel, architecture
**Applies to:** Any feature ViewModel function in this project that triggers work in response to a UI event

In this project, ViewModel handlers are deliberately structured as `viewModelScope.launch { someFlow.onStart{…}.map{…}.onError{…}.onCompletion{…}.catch{…}.collect { _state.emit(it) } }`. Do not "modernize" them into imperative `try/catch` blocks even when the body is short — Flow is the medium that composes loading-state emission, `logFlow`, `mapDelayAtLeast` (minimum-loading UX), `onError`/`onCompletion` rethrow semantics, and SharedFlow event side-effects. A bulk lowering across `feature/*` was attempted (b41c34d) and reverted (4f20fa5). Generalizes L-2026-04-27-01 from "which error helper" to "what shape is the function."

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-03 · Test internals from inside the owning module, not via test-only factories
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** testing, multi-module, mockwebserver, internal-visibility
**Applies to:** Multi-module Android projects where a facade/data-source has `internal` collaborators (Retrofit `*Api` types, KSP-generated artifacts) that an outside test wants to construct against `MockWebServer`.

Do not introduce a test-only `Factory.create(...)` in `main/` to give a downstream module's test access to `internal` collaborators — the cost is permanent (a production-source seam that exists only for tests) and the test pattern stops matching its peers. Keep downstream repository tests mocking the facade interface like their peers, and put the HTTP-wiring/integration test inside the module that owns the impl: same module's `test/` source set sees `internal` for free. Net effect: repository tests stay narrow at the facade boundary, integration coverage lives at the right layer, and no test-only types leak into production source.

**Source:** PR #27 (refactor/15-remote-source-facade)

### L-2026-04-30-02 · GitHub Actions JDK must match `compileOptions.targetCompatibility`, not just the Kotlin toolchain
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** ci, gradle, hilt, jdk, github-actions
**Applies to:** Android projects with Hilt where the workflow JDK is older than the project's `compileOptions.targetCompatibility`

When the project's `compileOptions.targetCompatibility` (and matching Kotlin `jvmToolchain(...)`) is newer than the runner's JDK, most Java compile tasks succeed because Gradle auto-provisions the toolchain. But `:app:hiltJavaCompileDebug` runs as a plain `JavaCompile` against Gradle's own JVM — *not* the toolchain — and fails with `error: invalid source release: <N>`. Set the workflow JDK (`actions/setup-java@v4` `java-version`) to at least the project's target version; don't rely on the Kotlin toolchain to paper over the mismatch.

**Source:** Wave 1 PR #25 (Hilt KAPT → KSP)

### L-2026-04-30-01 · Ports in `:domain`, adapters in `:remote:*`, wiring in `:app`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** hilt, di, multi-module, ports-and-adapters, architecture
**Applies to:** Any refactor that lifts an interface from a remote/data module up into `:domain` so `:domain` no longer depends on `:remote:*`

When you move a Source/Repository *interface* up to `:domain` (the port) but leave its `@Provides`-bound impl down in `:remote:*` (the adapter), `:domain` correctly drops `implementation(project(":remote:*"))`. But the composition root — `:app` — must then add those `:remote:*` modules as `implementation` deps itself, otherwise Hilt fails at `:app:hiltJavaCompileDebug` with `MissingBinding` for the port type. The Hilt graph follows Gradle visibility; if `:app` can't see the adapter module, it can't see its `@Module @Provides`. This is mechanical, not a design flaw — but it surprises you the first time because the symptom shows up at app-level Hilt compilation rather than at the module boundary.

**Source:** Wave 2 PR #29 (issue #17 — Remote→Domain mapper relocation)

### L-2026-04-27-01 · Prefer flow-native error helpers over `runCatching` inside Flow chains
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-27 · **Tags:** kotlin, coroutines, flow, error-handling
**Applies to:** ViewModel/repository code that wraps a single suspend call inside a Flow pipeline

When lifting a `suspend` call into a Flow, prefer `flow { emit(call()) }` plus the project's `catchAndContinue(defaultValue, action)` helper from `common/logic/util/FlowExtensions.kt` over `runCatching { ... }.getOrNull()`. It keeps error handling on the Flow itself (so cancellation and downstream operators see the right thing) and reuses the codebase's existing helper instead of inlining a raw `.catch { ... emit(default) }`.

**Source:** CommitmentPackagesViewModel FAQ refactor

### L-2026-04-20-01 · On migration branches, map conflicting *features* not *files*
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-20 · **Tags:** merge-conflicts, migration, di, architecture
**Applies to:** Any long-running branch refactoring infrastructure (DI, networking, shared modules) while `main` ships features

Don't resolve merge conflicts file-by-file. First identify which *features* landed on `main` during the migration, then decide per feature: was it already migrated? Does it need to be? Does it stay where it is? This gives a coherent strategy instead of ad-hoc ours/theirs picks that silently break DI wiring. In practice, a dozen conflicting files often reduce to just two or three feature-level decisions.

**Source:** merge conflict resolution

## Archive
