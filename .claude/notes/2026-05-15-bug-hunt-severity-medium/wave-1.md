# Wave 1 — 2026-05-15

Campaign: `2026-05-15-bug-hunt-severity-medium` (labels: `bug-hunt`, `severity:medium`)
Base branch: `dev`

## Summary
- Attempted: 2
- Succeeded: 2
- Failed: 0

## Per-issue

### #145 — Snackbar "Retry" action handler calls onBack(), no retry function exists
- **Status:** open (PR #159)
- **Branch:** `wave/2026-05-15-bug-hunt-severity-medium/issue-145-snackbar-retry-action`
- **PR:** https://github.com/Mithrandir21/game-deals-android-app/pull/159
- **Title:** `fix(store,favourites): wire snackbar Retry action to a real retry()`
- **Files changed (6):**
  - `feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt`
  - `feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt`
  - `feature/favourites/src/commonMain/kotlin/pm/bam/gamedeals/feature/favourites/ui/FavouritesViewModel.kt`
  - `feature/favourites/src/commonMain/kotlin/pm/bam/gamedeals/feature/favourites/ui/FavouritesScreen.kt`
  - `feature/store/src/commonTest/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModelTest.kt`
  - `feature/favourites/src/commonTest/kotlin/pm/bam/gamedeals/feature/favourites/ui/FavouritesViewModelTest.kt`
- **Approach:** Added a private `MutableStateFlow<Int>` retry counter in each VM, incremented via `update { it + 1 }` (per `L-2026-05-02-01`). Counter is combined into the upstream load via `flatMapLatest`, which cancels the in-flight inner flow and re-runs the data fetch. In `StoreViewModel`, retry counter combined with `storeIdFlow` so both `uiState` and `deals` re-run; `deals` keeps `distinctUntilChanged()` so orientation change is a no-op. In `FavouritesViewModel`, the entire `observeFavourites()` chain is wrapped in `retryTrigger.flatMapLatest { ... }`, so each retry re-subscribes from scratch. Snackbar `errorRetry` action in both screens calls `viewModel.retry()` instead of `currentOnBack()`. `FavouritesScreenContent` gained a hoisted `onRetry: () -> Unit` parameter wrapped via `rememberUpdatedState` (per `L-2026-05-02-06`).
- **Tests added (5):** `StoreViewModelTest` — `retry_after_failed_store_details_load_flips_Error_back_to_Data`, `retry_resubscribes_deals_hot_source`, `retry_emits_Loading_before_re_running_failed_store_details_load`. `FavouritesViewModelTest` — `retry_resubscribes_to_observeFavourites`, `retry_after_failed_load_flips_ERROR_back_to_SUCCESS_when_repository_recovers`.
- **Validation:** `:feature:store:testDebugUnitTest`, `:feature:favourites:testDebugUnitTest`, `compileKotlinIosSimulatorArm64`, `compileTestKotlinIosSimulatorArm64` all green.
- **Reviewer notes:** existing instrumented `StoreScreenTest` does not assert the snackbar's `errorRetry` action; `FavouritesScreen` has no instrumented test at all — no instrumented-test changes required. `StoreScreen` no longer needs `currentOnBack`/`rememberUpdatedState` for the retry path because VM identity is stable; `L-2026-05-02-06` is specifically about caller-provided lambdas.

### #146 — Room builder does not call setQueryCoroutineContext on either platform
- **Status:** open (PR #158)
- **Branch:** `wave/2026-05-15-bug-hunt-severity-medium/issue-146-room-set-query-coroutine-context`
- **PR:** https://github.com/Mithrandir21/game-deals-android-app/pull/158
- **Title:** `fix(domain): pin Room query dispatcher via setQueryCoroutineContext`
- **Files changed (2):**
  - `domain/src/androidMain/kotlin/pm/bam/gamedeals/domain/di/DomainAndroidModule.kt`
  - `domain/src/iosMain/kotlin/pm/bam/gamedeals/domain/di/DomainIosModule.kt`
- **Approach:** Added `.setQueryCoroutineContext(Dispatchers.IO)` to Android's Room builder and `.setQueryCoroutineContext(Dispatchers.Default)` to iOS's (see surprise below).
- **Validation:** `:domain:compileDebugKotlinAndroid`, `:domain:compileKotlinIosSimulatorArm64`, `:domain:testDebugUnitTest` all green.
- **Reviewer notes / surprising bits:** the issue body claimed `Dispatchers.IO` would resolve from `iosMain` directly. **It does not** — the symbol is `internal` in `kotlinx.coroutines.Dispatchers` on Kotlin/Native regardless of source set (commonMain *or* iosMain), not just commonMain. First compile failed with `Cannot access 'val IO: CoroutineDispatcher': it is internal`. This refines existing lesson `L-2026-05-06-03` which can be read to imply `IO` works in `iosMain`; it doesn't. Sub-agent used `Dispatchers.Default` on iOS instead — same runtime behavior since K/N's `IO` aliases to `Default` anyway, and consistent with the existing `Dispatchers.Default` use in `common/src/iosMain/.../CommonIosModule.kt`. No injected `ioDispatcher` exists in `:common` DI; sub-agent did not introduce one.

## Conflicts deferred
None. Only 2 candidate issues matched the label filter; their file sets were fully disjoint (different modules — `:feature:store` + `:feature:favourites` vs `:domain`).

## Sanity-check results
- #145: branch present on origin (✓), 1 commit ahead of dev (✓), PR OPEN with base=dev (✓).
- #146: branch present on origin (✓), 1 commit ahead of dev (✓), PR OPEN with base=dev (✓).
