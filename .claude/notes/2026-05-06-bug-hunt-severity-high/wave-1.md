# Wave 1 — campaign 2026-05-06-bug-hunt-severity-high

**Issues attempted:** 1 · **Succeeded:** 1 · **Failed:** 0

Single-issue wave. Only one open `bug-hunt`+`severity:high` issue existed (the other 8 from this morning's bug hunt are all severity:medium or severity:low).

## Issues

### #122 — `reloadGiveaways` swallows `CancellationException` — ✅ open

- **PR:** [#131](https://github.com/Mithrandir21/game-deals-android-app/pull/131) — `fix(#122): rethrow CancellationException in GiveawaysViewModel.reloadGiveaways`
- **Branch:** `wave/2026-05-06-bug-hunt-severity-high/issue-122-reloadgiveaways-rethrow-ce` (base: `dev`, 1 commit ahead)
- **Files:**
  - `feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt` — added `CancellationException` import and `catch (e: CancellationException) { throw e }` clause before the existing bare catch.
  - `feature/giveaways/src/commonTest/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModelTest.kt` — added `cancelled_reload_does_not_set_ERROR` test that mocks `refreshGiveaways()` to throw `CancellationException` and asserts the surviving `uiState` status is not `ERROR`.
- **Verification:** `:feature:giveaways:compileDebugKotlinAndroid` and `:feature:giveaways:testDebugUnitTest` both BUILD SUCCESSFUL (11/11 tests). Worker confirmed the new test fails against the unfixed source and passes with the rethrow in place.

## Conflicts deferred from this wave

None. Single-issue wave.

## Sanity-check results

- Branch present on origin: ✅
- Commits ahead of `origin/dev`: 1
- PR #131 state: OPEN · base=`dev` · head matches expected branch · title matches `fix(#NNN): …` convention.

(Note: `gh pr view` returned a transient `504 Gateway Timeout` on first attempt; immediate retry succeeded. Not a sub-agent failure — GitHub-side blip.)

## Notes for reviewer

- The fix mirrors L-2026-05-02-04 exactly; precedents are #71 (DealDetailsController) and #31 (DealsMediator). After this lands, every `catch (Throwable)` that wraps suspending work in this codebase rethrows `CancellationException` first.
- Worker required `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` (per the user's existing reference memory `reference_local_jdk_for_app_assemble.md`); system default JDK 17 isn't sufficient for the wrapper. Worth surfacing once if a fresh worker session ever hits this without context.
