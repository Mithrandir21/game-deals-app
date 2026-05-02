# Wave 2 — 2026-05-02

**Summary:** 1 issue attempted, 1 PR opened, 0 failed.

## Issues

### #80 → PR #83 — `fix(#80): annotate GameDetails @Immutable + migrate deals to ImmutableList`
- Branch: `wave/2026-05-02-bug-hunt-severity-low/issue-80-gamedetails-immutable`
- Files (7):
  - `domain/.../Game.kt` — `@Immutable`, `deals: ImmutableList<GameDeal>`
  - `remote/cheapshark/.../GameMappers.kt` — `.toImmutableList()` in `RemoteGameDetails → GameDetails` mapper
  - `remote/cheapshark/build.gradle.kts` — **scope expansion:** added `implementation(libs.kotlinx.collections.immutable)` (see surprise below)
  - `common/ui/.../PreviewData.kt` — `persistentListOf(PreviewGameDeal)` for previews
  - `common/ui/build.gradle.kts` — added `implementation(libs.kotlinx.collections.immutable)`
  - `feature/game/test/.../GameViewModelTest.kt` — mocked deals list switched to `persistentListOf`
  - `feature/game/androidTest/.../GameScreenTest.kt` — same
- Tests: 2 existing tests updated for the new constructor type; no new tests.
- Surprises:
  - **Pre-wave plan said `domain/build.gradle.kts` would need touching, but #82 (issue #79) had merged just before — confirmed dep was already there, agent correctly skipped it.**
  - **Pre-wave plan did NOT include `remote/cheapshark/build.gradle.kts`. Required because `GameMappers.kt` imports `kotlinx.collections.immutable.toImmutableList`, and Gradle `implementation` from `:domain` does NOT propagate transitively to consumer modules' compile classpaths.** This is the cross-module-extension-function gotcha — worth promoting to a project-wide lesson.
  - Verified locally: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL under JBR 21; all four module test runs green.

## Sanity-check results

| PR  | state | base  | head                              | commits ahead of dev |
|-----|-------|-------|-----------------------------------|----------------------|
| #83 | OPEN  | `dev` | issue-80-gamedetails-immutable    | 1                    |

Pass: `done`.

## Cwd-shift mitigation

This wave used `git -C /Users/bam/REPO/PRIVATE/game-deals-android-app …` for all orchestrator git commands, sidestepping the cwd-into-worktree drift seen in wave 1. Worked cleanly.
