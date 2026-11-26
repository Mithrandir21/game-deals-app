# Wave 2 — 2026-05-01

## Summary

- Issues attempted: 3
- PRs opened: 3
- Failures: 0
- Wave 1 issues (#31, #35, #41, #42) merged before this wave executed.

## Issues

### #34 — `WebView.update` no longer reloads the original URL ✓
- PR: [#64](https://github.com/Mithrandir21/game-deals-android-app/pull/64) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-34-webview-update-loadurl`
- Diff: +12 / -2 in `feature/webview/.../WebView.kt`, 1 commit
- Fix: added remembered `lastLoadedUrl: String?` next to the `AndroidView`. `factory` calls `loadUrl(url)` once on creation and seeds `lastLoadedUrl = url`. `update` only calls `loadUrl(url)` when `lastLoadedUrl != url` (preserves intra-WebView navigation while still handling a changed `url` argument). Dropped the `loading = true` write from `update` (subsumes #40 — `onPageStarted` already sets it).
- Tests: `:feature:webview:test` BUILD SUCCESSFUL (module has no JVM tests; verified via compile only).
- Scope: stayed inside cleared set.
- **Subsumes #40.** PR body explicitly notes this so the reviewer can close #40 as duplicate after merge.

### #39 — Snackbar retry/reload lambdas wrapped with `rememberUpdatedState` ✓
- PR: [#65](https://github.com/Mithrandir21/game-deals-android-app/pull/65) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-39-launchedeffect-stale-captures`
- Diff: +14 / -4 across 4 files, 1 commit
- Fix: each of HomeScreen, GameScreen, SearchScreen, GiveawaysScreen gained `rememberUpdatedState` (and `getValue` where missing) imports, declared `val currentOnRetry by rememberUpdatedState(onRetry)` (or `currentOnReload` for giveaways) at the top of the relevant Screen/ScreenScaffold composable, and replaced the `onRetry()`/`onReload()` invocation inside the `LaunchedEffect(snackbarHostState)` block with the wrapped reference. Exactly one `LaunchedEffect` occurrence per file, four total.
- Tests: `:feature:{home,game,search,giveaways}:test` BUILD SUCCESSFUL.
- Scope: stayed inside cleared set; did not disturb the wave-1 stable-key changes.

### #37 — Six ViewModels: `_uiState.stateIn(WhileSubscribed, initial)` → `_uiState.asStateFlow()` ✓
- PR: [#66](https://github.com/Mithrandir21/game-deals-android-app/pull/66) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-37-viewmodel-statein`
- Diff: +65 / -74 across 9 files, 1 commit
- Fix: each of Home/Store/Giveaways/Search/Game/DealDetails ViewModels — replaced `_uiState.stateIn(viewModelScope, WhileSubscribed(5000), initial)` with `_uiState.asStateFlow()`; dropped now-unused `SharingStarted` and `stateIn` imports.
- Tests: 3 existing test files updated:
  - `HomeViewModelTest` (5 tests; `third` import dropped)
  - `GiveawaysViewModelTest` (3 tests)
  - `StoreViewModelTest` (1 test; `second` import dropped)
  - `SearchViewModelTest`, `GameViewModelTest`, `DealDetailsViewModelTest` were already shaped correctly and needed no edits.
- **Notable finding:** the existing HomeVM / GiveawaysVM / StoreVM tests were asserting the *buggy* shape — `size=2, [initialValue, currentValue]` — because under `UnconfinedTestDispatcher`, `init { }` drains synchronously and the only thing producing a second emission was the spurious initial-flash from the now-removed `stateIn` wrapper. Tests were updated to assert the correct conflated semantics (`size=1, [currentValue]`), with inline comments referencing #37 to deter regression.
- Verification: `./gradlew :feature:{home,store,giveaways,search,game,deal}:test` BUILD SUCCESSFUL across debug and release variants.

## Sanity check

| PR | state | base | additions | deletions | commits |
|---|---|---|---|---|---|
| #64 | OPEN | dev | 12 | 2 | 1 |
| #65 | OPEN | dev | 14 | 4 | 1 |
| #66 | OPEN | dev | 65 | 74 | 1 |

All three pass.

## Notes

- **Worktree env quirk recurred for all three agents.** Each had to copy `local.properties` from the main checkout and override `JAVA_HOME` to Android Studio's bundled JBR 21 (system default Java 17 fails on `:build-logic:convention`). With wave-1 (3/4) and wave-2 (3/3), this is consistent enough to be a real ergonomic problem. Strong candidate for orchestrator pre-seeding in a future skill revision.
- **#37's test discovery is the most interesting finding of the wave.** Three of six VM test files were not just under-asserting — they were positively asserting the buggy emission shape. The agent caught and corrected this. Any future refactor of these ViewModels' init flow should remember that `UnconfinedTestDispatcher` makes `init {}` block drain synchronously, so a single test emission is the correct expectation; multiple emissions indicate either a real bug or a test that's racing with the dispatcher.
- **#34 incidentally closes #40.** The `loading = true` removal from `update` is the entirety of #40's recommended fix. Reviewer should close #40 after #34 merges; orchestrator will detect the closed-no-PR state on next re-entry and update accordingly.

## Campaign status

After this wave merges:
- Resolved: 9/13 (#31, #35, #41, #42 from wave 1; #34, #37, #39 from wave 2; plus #40 incidentally via #34).
- Open: 4/13 (#30, #32, #33, #36, #38) — wait, that's 5. Note: #40 will be closed-as-dup but #38 remains.
- Likely wave 3: {#33, #36, #30, #32} — 4 issues, all mutually independent.
- Likely wave 4: {#38} — alone (conflicts with #33/#36 are now gone, but its other conflicts dictate scheduling).
