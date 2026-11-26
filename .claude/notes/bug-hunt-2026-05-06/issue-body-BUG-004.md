| Field | Value |
|---|---|
| Severity | Low |
| Category | Compose — missing `remember` on stateful resource |
| Location | `feature/game/src/commonMain/.../GameScreen.kt:301`, `feature/store/src/commonMain/.../StoreScreen.kt:219` |
| Effort | Trivial |
| Confidence | Medium |

**Description.** Both `GameScreen.ScreenScaffold` and `StoreScreen.StoreToolbar` build their scroll behavior with `TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())` directly in the composable body, with no surrounding `remember`. The inner `TopAppBarState` is preserved (via `rememberTopAppBarState()`), so animation/offset state survives — but the outer behavior wrapper (and its `flingAnimationSpec`, `canScroll` predicate) is recreated and re-attached to the `TopAppBar` each frame.

**Impact.** No correctness bug observed today (`pinnedScrollBehavior` delegates almost everything to the inner state, which IS remembered). With richer behaviors (e.g., `enterAlwaysScrollBehavior`) the missing `remember` actually drops mid-animation state. Latent risk if the behavior is later swapped, plus minor allocation churn now.

**Evidence.**
```kotlin
// GameScreen.kt:299–302
private fun ScreenScaffold(...) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val currentOnRetry by rememberUpdatedState(onRetry)
```

**Recommended fix.**
```kotlin
val topAppBarState = rememberTopAppBarState()
val scrollBehavior = remember(topAppBarState) {
    TopAppBarDefaults.pinnedScrollBehavior(topAppBarState)
}
```

**Confidence rationale.** Medium because Material3's official samples wrap with `remember`. Low impact because `pinnedScrollBehavior` delegates to the inner `state` (which IS remembered). Could be safely demoted to "ignore" if zero recomposition cost has been measured here.
