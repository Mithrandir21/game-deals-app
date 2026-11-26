# Compose Correctness — Findings (2026-05-01)

Scope: `*/src/main/**/*.kt`. Worktrees and `build/` excluded.

## Summary

- **Critical:** 0
- **High:** 0
- **Medium:** 2
- **Low:** 2

Top 3 findings:
1. `SearchParameters.equals` always returns `false` defeats Compose skipping (Medium / High confidence)
2. `SingleEventEffect` captures collector lambda without `rememberUpdatedState` (Medium / High confidence)
3. Unstable `List<Pair<…>>` fields make `GiveawaySearchParameters` unskippable (Low / Medium confidence)

---

## Finding 1 — `SearchParameters.equals` always returns `false` defeats Compose skipping

- **Severity:** Medium
- **Category:** D9 — Unstable parameters causing recomposition storms
- **Location:** `/Users/bam/REPO/PRIVATE/game-deals-android-app/domain/src/main/java/pm/bam/gamedeals/domain/models/Search.kt:52`
- **Effort:** Small
- **Confidence:** High

**Description.** `SearchParameters` is a `@Serializable data class` whose `equals` is overridden to *unconditionally return `false`*:

```kotlin
override fun equals(other: Any?): Boolean = false
```

The same instance is then passed through three Compose layers: `SearchScreen` -> `Screen(existingSearchParameters = …)` (`SearchScreen.kt:114`) -> `SearchFilters(existingSearchParameters = …)` (`:197`) -> `Filters(existingSearchParameters)` (`:322`). Compose decides whether a child can skip recomposition by calling `equals` on each parameter slot. With `equals == false`, every parameter comparison fails, so every parent recomposition forces every child that takes a `SearchParameters` to recompose, even when the underlying field values are identical.

**Impact.** The `Filters` bottom-sheet (two `Slider`s + `Switch` + label rebuilds) recomposes on every state change in `searchData`, even with identical filters. The `Properties.encodeToMap` rebuild path runs through the saver each time. Compose's skipping mechanism is defeated for any composable taking `SearchParameters`. Author's own comment on the line asserts the rationale: avoiding `StateFlow` strong-equality conflation — but solving that at the model layer breaks `equals` for the entire Compose tree.

**Evidence.**
```kotlin
// domain/.../Search.kt
data class SearchParameters(
    val storeID: Int? = null, …,
    val title: String? = null, …,
    val onSale: Boolean? = null,
) {
    /** Returning `false` to avoid the default implementation of `equals` when
        attempting to emit a new value in a `StateFlow`. */
    override fun equals(other: Any?): Boolean = false   // <-- line 52
}

// feature/search/.../SearchScreen.kt
@Composable
private fun Filters(existingSearchParameters: SearchParameters, …)   // <-- line 329
```

**Recommended fix.** Solve the `StateFlow` conflation at the flow boundary, not on the type. Either (a) replace the `StateFlow` with a `MutableSharedFlow` that doesn't conflate, or (b) wrap each emission in a unique sentinel (`Pair<Long, SearchParameters>` / `Indexed<SearchParameters>`) so the envelope differs while the inner value retains structural equality. Restoring data-class `equals` is the right behaviour for both Compose stability and any future `LaunchedEffect` keying.

**Confidence rationale.** The override is explicit and the type is concretely passed as a Compose parameter at multiple call sites. No ambiguity on mechanism.

---

## Finding 2 — `SingleEventEffect` captures collector lambda without `rememberUpdatedState`

- **Severity:** Medium
- **Category:** D7 — State/lambda captured in remembered effect without `rememberUpdatedState`
- **Location:** `/Users/bam/REPO/PRIVATE/game-deals-android-app/common/src/main/java/pm/bam/gamedeals/common/CommonFlowExtensions.kt:80-92`
- **Effort:** Trivial
- **Confidence:** High

**Description.** The helper keys its `LaunchedEffect` only on `sideEffectFlow`:

```kotlin
@Composable
fun <T : Any> SingleEventEffect(
    sideEffectFlow: Flow<T>,
    lifeCycleState: Lifecycle.State = Lifecycle.State.STARTED,
    collector: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(sideEffectFlow) {
        lifecycleOwner.repeatOnLifecycle(lifeCycleState) {
            sideEffectFlow.collect(collector)
        }
    }
}
```

The `collector` lambda is captured by the launched coroutine. If the parent recomposes with a `collector` that closes over different state, the new lambda is **not** observed — the coroutine still calls the lambda captured at first launch. The KDoc does not warn callers.

**Impact.** Today's only call site (`HomeScreen.kt:115-119`) closes over `goToGame`, which transitively resolves to `navActions.navigateToGame(...)` where `navActions` is `remember(navController)`d in `NavGraph.kt:22`. The chain happens to be safe by accident. Any future caller closing over screen-local state in its `collector` will silently fire stale callbacks (wrong navigation target, wrong analytics payload, etc).

**Evidence.**
```kotlin
// CommonFlowExtensions.kt:87
LaunchedEffect(sideEffectFlow) {
    lifecycleOwner.repeatOnLifecycle(lifeCycleState) {
        sideEffectFlow.collect(collector)   // captured-at-first-launch
    }
}

// HomeScreen.kt:115
SingleEventEffect(viewModel.events) { event ->
    when (event) {
        is HomeViewModel.HomeUiEvent.NavigateToGame -> goToGame(event.gameId)
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

## Finding 3 — Unstable `List<Pair<…>>` fields make `GiveawaySearchParameters` unskippable

- **Severity:** Low
- **Category:** D9 — Unstable parameters causing recomposition storms
- **Location:** `/Users/bam/REPO/PRIVATE/game-deals-android-app/domain/src/main/java/pm/bam/gamedeals/domain/models/Giveaway.kt:129-133`
- **Effort:** Small
- **Confidence:** Medium

**Description.** `GiveawaySearchParameters` carries `platforms: List<Pair<GiveawayPlatform, Boolean>>` and `types: List<Pair<GiveawayType, Boolean>>` — both raw `kotlin.collections.List`, not `ImmutableList`. The class has no `@Immutable`/`@Stable`. It is used as a Compose parameter on `ScreenScaffold` (`GiveawaysScreen.kt:128`), `GiveawayFilters` (`:265`), `Filters` (`:290`), and `GiveawaySortByOptions` (`:357`).

**Impact.** Each recomposition of `GiveawaysScreen` (e.g. when `uiState.giveaways` updates while filters are open) forces all four downstream composables to rebuild even when parameters are bit-identical. This is the same class of issue addressed by the screen-state migration in PR #70 — these filter parameters were missed. Bounded in practice because `Filters` only mounts while the modal sheet is open.

**Evidence.**
```kotlin
// Giveaway.kt:129
data class GiveawaySearchParameters(
    val platforms: List<Pair<GiveawayPlatform, Boolean>> = …,
    val types:     List<Pair<GiveawayType,     Boolean>> = …,
    val sortBy:    GiveawaySortBy = GiveawaySortBy.DATE,
)

// GiveawaysScreen.kt:108
existingParameters = existingParameters.copy(
    platforms = existingParameters.platforms.toMutableList()
        .map { if (it.first == platform) platform to selection else it })
// .map produces a regular List, sustaining the unstable type.
```

**Recommended fix.** Type both fields as `ImmutableList<Pair<…, Boolean>>` (`kotlinx.collections.immutable`) and replace `.toMutableList().map { … }` with `… .map { … }.toImmutableList()`. Optionally annotate the class `@Immutable`.

**Confidence rationale.** Type-stability rules are deterministic; "Medium" rather than "High" because the bounded mount lifetime limits real-world cost.

---

## Finding 4 — `GameDetails.deals: List<GameDeal>` defeats skipping of `CompactGameDetail` / `WideGameDetail`

- **Severity:** Low
- **Category:** D9 — Unstable parameters causing recomposition storms
- **Location:** `/Users/bam/REPO/PRIVATE/game-deals-android-app/domain/src/main/java/pm/bam/gamedeals/domain/models/Game.kt:32-39`; consumed at `/Users/bam/REPO/PRIVATE/game-deals-android-app/feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameScreen.kt:147,193`
- **Effort:** Small
- **Confidence:** Medium

**Description.** `GameDetails` has `deals: List<GameDeal>` (raw `List`, not `ImmutableList`) and no `@Immutable`. Compose marks the type unstable. `CompactGameDetail(gameDetails: GameDetails)` and `WideGameDetail(gameDetails: GameDetails)` therefore cannot skip when the parent recomposes with the same value. The outer `dealDetails: ImmutableList<Pair<Store, GameDetails.GameDeal>>` in `GameScreenData.Data` is correctly typed (PR #70), but the inner `gameDetails.deals` remained unstable.

**Evidence.**
```kotlin
// domain/.../Game.kt:32
data class GameDetails(
    val info: GameInfo,
    val cheapestPriceEver: GameCheapestPriceEver,
    val deals: List<GameDeal>,   // unstable
)

// GameScreen.kt:174 — read inside CompactGameDetail
text = … gameDetails.deals.minBy { it.priceValue }.priceDenominated …
```

**Recommended fix.** Type `deals` as `ImmutableList<GameDeal>`; annotate `@Immutable` once true. Same treatment for any other domain `List` field fed to composables.

**Confidence rationale.** Same mechanism as Finding 3; bounded impact because `GameScreen` is mostly static once data has loaded.

---

## Notes on detectors checked and not flagged

- **D1** (side effect outside effect handler), **D2** (`LaunchedEffect(Unit/true)`), **D5** (`collectAsState()` rather than `…WithLifecycle`), **D6** (state mutation during composition), **D8** (`rememberCoroutineScope` misuse), **D11** (`MutableState` exposed from VM), **D12** (`DisposableEffect` without `onDispose`), **D13** (`produceState` without initial value), **D14/D15** — no instances in `*/src/main/`.
- `feature/webview/src/main/java/pm/bam/gamedeals/feature/webview/ui/WebView.kt` reviewed end-to-end. The `loading` and `lastLoadedUrl` State writes occur in event callbacks / `AndroidView`'s apply phase, not during composition. Recent destroy fix (PR #67) correctly uses `AndroidView.onRelease`. Nothing to add.
- Snackbar `LaunchedEffect(snackbarHostState)` patterns in `HomeScreen.kt:294`, `GameScreen.kt:345`, `GiveawaysScreen.kt:197`, `SearchScreen.kt:207` correctly enter/leave composition with the error branch and use `rememberUpdatedState(onRetry/onReload)` for the captured callback. Correct.
- Screen-state migration to `ImmutableList` (PR #70) verified — no leftover `List<>` / `MutableList<>` driving screen state.
- `SearchScreen.Filters` (`feature/search/.../SearchScreen.kt:340`) `existingHighest = existingSearchParameters.upperPrice?.toFloat() ?: priceLowest` looks like a logic bug (falls back to the *lower* bound when `upperPrice` is null). Not a Compose-correctness defect; out of scope.