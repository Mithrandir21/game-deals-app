# Wave 1 — 2026-05-01

## Summary

- Issues attempted: 4
- PRs opened: 4
- Failures: 0
- All PRs target `dev`, 1 commit each, sanity-checked.

## Issues

### #43 — WebView loading state ✓
- PR: [#54](https://github.com/Mithrandir21/game-deals-android-app/pull/54) (open)
- Branch: `wave/2026-05-01-bug-hunt-severity-low/issue-43-webview-loading`
- Diff: +2 / -2 in `feature/webview/.../WebView.kt`
- Fix: `rememberSaveable` → `remember` for transient WebViewClient loading flag.
- Tests: `./gradlew :feature:webview:test` — BUILD SUCCESSFUL (module has no unit tests).

### #46 — StoreViewModel paging `.catch` ✓
- PR: [#55](https://github.com/Mithrandir21/game-deals-android-app/pull/55) (open)
- Branch: `wave/2026-05-01-bug-hunt-severity-low/issue-46-storeviewmodel-catch`
- Diff: +3 / -1 in `feature/store/.../StoreViewModel.kt`
- Fix: removed `.catch` after `.cachedIn(viewModelScope)` on the deals Paging Flow; preserved unrelated `.catch` on the `storeDetails` init block.
- Tests: `./gradlew :feature:store:test` passed.

### #47 — Coil dispatcher ✓
- PR: [#57](https://github.com/Mithrandir21/game-deals-android-app/pull/57) (open)
- Branch: `wave/2026-05-01-bug-hunt-severity-low/issue-47-coil-dispatcher`
- Diff: +0 / -2 in `app/.../AppModule.kt` (line removed + unused `Dispatchers` import).
- Fix: dropped `.dispatcher(Dispatchers.Default)`; lets Coil default to `Dispatchers.IO`.
- Tests: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL.
- Note: agent's worktree was pruned mid-task and had to be re-registered with git; no work lost. Worth keeping an eye on if this recurs.

### #48 — `fullscreenSemiTransparentBackground` memoization ✓
- PR: [#56](https://github.com/Mithrandir21/game-deals-android-app/pull/56) (open)
- Branch: `wave/2026-05-01-bug-hunt-severity-low/issue-48-fullscreen-bg-remember`
- Diff: +6 / -5 in `common/ui/.../Theme.kt`
- Fix: wrapped `Color` allocation in `remember(isSystemInDarkTheme())`. Did not touch the `SideEffect`/`statusBarColor` block — that's #44's territory in wave 2.
- Tests: `./gradlew :common:ui:test` BUILD SUCCESSFUL (module has no unit tests).

## Deferrals (going to wave 2)

- **#44** — `SideEffect` → `LaunchedEffect` in Theme. Conflicts with #48 on `Theme.kt`. Will rebase on #48's merged change.
- **#45** — `*DelayAtLeast` Flow operators virtual-time. Wave-cap deferral (no conflict with anything in wave 1). Has its own file (`FlowExtensions.kt`) and creates a new test file (`FlowExtensionsTest.kt`).

## Sanity check

| PR | state | base | additions | deletions | commits ahead of dev |
|---|---|---|---|---|---|
| #54 | OPEN | dev | 2 | 2 | 1 |
| #55 | OPEN | dev | 3 | 1 | 1 |
| #56 | OPEN | dev | 6 | 5 | 1 |
| #57 | OPEN | dev | 0 | 2 | 1 |

All four pass.
