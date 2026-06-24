# Wave 1 — campaign 2026-05-02-bug-hunt-3

**Date:** 2026-05-02
**Labels:** `bug-hunt`
**Attempted:** 4 · **Succeeded:** 4 · **Failed:** 0

## Issues

### #97 [severity:high] — Outer .catch terminates upstream collector in SearchViewModel and GiveawaysViewModel
- **PR:** [#106](https://github.com/Mithrandir21/game-deals-android-app/pull/106) — `fix(#97): move .catch inside flatMapLatest/combine to keep collector alive`
- **Branch:** `wave/2026-05-02-bug-hunt-3/issue-97-outer-catch-terminates-collector`
- **Fix:** Moved `SearchViewModel.catch` from outer chain into the `else ->` branch of `flatMapLatest`. In `GiveawaysViewModel`, the inner `giveawaysFlow` catches its own failure, sets `refreshOutcomeFlow.value = Error`, and emits `emptyList()` so `combine` re-fires the existing `Error → ERROR` mapper.
- **Tests added:** "subsequent search after error still produces results" (Search); "upstream collector survives an observeGiveaways failure and processes later emissions" (Giveaways). Both fail under the old code.
- **Surprises:** The Giveaways fix conflates an `observeGiveaways()` throw with `RefreshOutcome.Error`. Small semantic shift, but matches the existing single `ERROR` UI surface and avoids adding a new sentinel/flow. Reviewer should validate this is acceptable vs. a parallel error channel.

### #96 [severity:high] — HomeViewModel.onReleaseGame: rethrowing onError crashes the app on missing game
- **PR:** [#105](https://github.com/Mithrandir21/game-deals-android-app/pull/105) — `fix(#96): handle missing release game via terminal .catch + null-as-not-found`
- **Branch:** `wave/2026-05-02-bug-hunt-3/issue-96-onreleasegame-crash-fix`
- **Fix:** Per L-2026-04-30-04 (Flow-shaped). Removed throw-on-null `.map`; kept `.onError { fatal(…) }` for logging; added terminal `.catch { _uiState.update { ERROR } }` that consumes the rethrow; moved success/null discrimination into `.collect { gameId -> if null → ERROR else → SUCCESS + emit event }`. No `runCatching`.
- **Tests modified/added:** `onReleaseGame title exception surfaces ERROR without crashing` (rewritten — dropped `expected = Exception::class`, asserts ERROR last emission and no event). New `onReleaseGame missing game surfaces ERROR without crashing`.
- **Surprises:** Agent initially Edit'd the parent repo path by mistake before reverting and applying to the worktree. Final parent-repo working tree is clean (verified during sanity-check). Worth keeping in mind: worktree-isolated agents can still resolve paths to the parent if they `cd` or open absolute paths. Removed orphaned `onCompletion` import and `testing.utils.second` test import after the rewrite.

### #100 [severity:medium] — StoreScreen error-snackbar LaunchedEffect captures stale onBack
- **PR:** [#104](https://github.com/Mithrandir21/game-deals-android-app/pull/104) — `fix(#100): wrap StoreScreen onBack in rememberUpdatedState`
- **Branch:** `wave/2026-05-02-bug-hunt-3/issue-100-storescreen-stale-onback`
- **Fix:** Added `import androidx.compose.runtime.rememberUpdatedState`; declared `val currentOnBack by rememberUpdatedState(onBack)`; replaced the `onBack()` call in the `LaunchedEffect` with `currentOnBack()`. Pattern matches sibling screens.
- **Tests added/modified:** None. Sibling screens with the same pattern likewise have no dedicated stale-callback regression tests; followed precedent.
- **Verification:** `:feature:store:testDebugUnitTest` and `:feature:store:lintDebug` both green.

### #98 [severity:medium] — Giveaway data class missing @Immutable forces giveaway-row recomposition
- **PR:** [#103](https://github.com/Mithrandir21/game-deals-android-app/pull/103) — `fix(#98): annotate Giveaway @Immutable to match sibling domain models`
- **Branch:** `wave/2026-05-02-bug-hunt-3/issue-98-giveaway-immutable`
- **Fix:** Single-line `@Immutable` annotation on the `Giveaway` data class. Import was already present (used by `GiveawaySearchParameters` further down in the same file).
- **Tests added/modified:** None. Pure Compose stability hint with no behavior change.
- **Surprises:** None. The same file already declared `@Immutable` on `GiveawaySearchParameters` further down — making `Giveaway` itself the lone holdout was even more conspicuous than the issue body suggested.

## Conflicts deferred from this wave

- **#99** (severity:medium) — `loadTopStoreDataFlow` async-orphan fix shares `feature/home/.../HomeViewModel.kt` with #96. Queued for wave 2.
- **#101** (severity:low) — no file conflict; deferred only because the wave cap is 4 and the two `severity:high` issues took priority.

## Sanity-check results

| PR | State | Base | Commits ahead of dev |
|---|---|---|---|
| #103 | OPEN | dev | 1 |
| #104 | OPEN | dev | 1 |
| #105 | OPEN | dev | 1 |
| #106 | OPEN | dev | 1 |

All 4 PRs verified open against `dev` with the expected branch heads. Parent working tree is clean — the #96 agent's reported parent-path mis-edit was reverted cleanly.

## Notes for reviewer

- #105 (#96 fix) and the upcoming #99 fix both touch `HomeViewModel.kt` in different functions. Merging order doesn't matter, but the second one will need a quick rebase if the first lands first.
- #106 (#97 fix in `GiveawaysViewModel`) routes upstream-flow throws through the existing `refreshOutcomeFlow = Error` trigger. If a parallel error channel is preferred, this is the place to ask for it.
