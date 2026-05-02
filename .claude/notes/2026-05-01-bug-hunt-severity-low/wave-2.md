# Wave 2 — 2026-05-01

## Summary

- Issues attempted: 2
- PRs opened: 2
- Failures: 0
- Wave 1 issues (#43, #46, #47, #48) merged before this wave executed.

## Issues

### #44 — Theme `SideEffect` → `LaunchedEffect` ✓
- PR: [#58](https://github.com/Mithrandir21/game-deals-android-app/pull/58) (open)
- Branch: `wave/2026-05-01-bug-hunt-severity-low/issue-44-theme-launchedeffect`
- Diff: +2 / -1 in `common/ui/.../Theme.kt`, 2 commits ahead of dev
- Fix: replaced `SideEffect { window... }` with `LaunchedEffect(colorScheme.primary, darkTheme) { window... }` so the Activity window mutation only re-fires when its inputs actually change. The deeper "double-wrapped `GameDealsTheme` per screen" concern is left out of scope per the issue.
- Tests: `./gradlew :common:ui:test` BUILD SUCCESSFUL (module has no unit tests).

### #45 — `*DelayAtLeast` Flow operators virtual-time fix ✓
- PR: [#59](https://github.com/Mithrandir21/game-deals-android-app/pull/59) (open)
- Branch: `wave/2026-05-01-bug-hunt-severity-low/issue-45-delayatleast-virtual-time`
- Diff: +150 / -46 across 3 files, 1 commit ahead of dev
- Fix: refactored all three operators (`mapDelayAtLeast`, `flatMapLatestDelayAtLeast`, `latestDelayAtLeast`) to use `coroutineScope { async(work); launch(delay); await; join }`. Removed all `System.currentTimeMillis` calls. Added `FlowExtensionsTest` with 6 tests covering virtual-time behavior via `runTest` + `testScheduler.currentTime`.
- Tests: `./gradlew :common:test` BUILD SUCCESSFUL, 6/6 passing in both `testDebugUnitTest` and `testReleaseUnitTest`.
- **Scope expansion:** agent added `testImplementation(libs.coroutines.testing)` to `common/build.gradle.kts` because the module had no prior JVM unit tests and lacked the test dependency. This was outside the pre-cleared file set but unavoidable given the issue mandates `runTest`-based tests. Agent flagged it transparently in its return — desired behavior.

## Sanity check

| PR | state | base | additions | deletions | commits ahead of dev |
|---|---|---|---|---|---|
| #58 | OPEN | dev | 2 | 1 | 2 |
| #59 | OPEN | dev | 150 | 46 | 1 |

Both pass.

## Campaign status

Wave 2 was the last planned wave for this campaign. Once #58 and #59 merge, all 6 originally-selected `bug-hunt`/`severity:low` issues will be resolved. The campaign will be marked complete on next re-invocation.
