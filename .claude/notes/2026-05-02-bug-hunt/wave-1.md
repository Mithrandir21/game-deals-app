# Wave 1 — 2026-05-02

**Summary:** 4 issues attempted, 4 PRs opened, 0 failed.

## Issues

### #71 → PR #84 — `fix(#71): rethrow CancellationException in DealDetailsController.load`
- Branch: `wave/2026-05-02-bug-hunt/issue-71-deal-details-cancellation`
- Files (1):
  - `common/ui/.../DealDetailsController.kt` — `catch (CancellationException) { throw it }` ahead of both outer + inner fallback `catch (Throwable)` blocks; matches the established discipline in `DealsMediator.kt:76-81`.
- Tests: none added. `:common:ui` has no unit-test source set (only an `androidTest` for the bottom sheet); spinning one up to test a 3-line rethrow would be over-investment.
- Surprises: none.

### #72 → PR #86 — `fix(#72): drive GiveawaysViewModel via flatMapLatest on parametersFlow`
- Branch: `wave/2026-05-02-bug-hunt/issue-72-giveaways-flatmaplatest`
- Files (2):
  - `feature/giveaways/.../GiveawaysViewModel.kt` — `MutableStateFlow<GiveawaySearchParameters?>` driven by `flatMapLatest`. `loadGiveaway()` becomes a one-line setter — no own `.catch` since the single init-time collector's `.catch` covers both branches. `@OptIn(ExperimentalCoroutinesApi::class)` at class level (matches `HomeViewModel`/`StoreViewModel` convention).
  - `feature/giveaways/.../GiveawaysViewModelTest.kt` — added `load Giveaways cancels prior unfiltered collector when filter applied`. Models the hot-Room flow with `MutableSharedFlow` rather than `flowOf(...)` (which completes immediately, defeating the race repro).
- Tests: 1 added.
- Surprises:
  - The existing test fixture's use of `flowOf(...)` is what masked the bug end-to-end — `flowOf` completes after one emission, so the parallel-collector race never materialized in test. **Worth a heuristic:** when verifying a Room-shaped `Flow`, default to `MutableSharedFlow` in tests so the test can model second emissions.
  - `loadGiveaway()` losing its own `.catch` is intentional — error handling consolidates in the single source-of-truth `.catch`. Reviewer should verify the consolidated error handling covers both filtered and unfiltered branches (it does, because both go through the same downstream `.catch`).

### #73 → PR #87 — `fix(#73): drive GameViewModel reload via reloadTrigger combined into source flow`
- Branch: `wave/2026-05-02-bug-hunt/issue-73-game-reload-trigger`
- Files (2):
  - `feature/game/.../GameViewModel.kt` — `reloadTrigger: MutableSharedFlow<Unit>` `combine`d into the existing source-of-truth flow keyed off `gameIdFlow`; `flatMapLatest` now cancels prior loads. `reloadGameDetails()` is now `reloadTrigger.tryEmit(Unit)`.
  - `feature/game/.../GameViewModelTest.kt` — extended the existing reload test to advance virtual time past `delayOnStart` before calling `reloadGameDetails()`. Old test passed because the parallel-launch path bypassed `delayOnStart`.
- Tests: 1 modified.
- Surprises:
  - Public API of `GameViewModel` is unchanged, so `GameScreen.kt` and the `GameScreenTest` androidTest mock-driver need no updates.
  - `delayOnStart` only fires on initial subscription of the upstream — subsequent reload-triggered re-runs are not re-delayed, matching prior user-perceived latency.
  - **Pattern divergence from #33:** issue #33 was fixed via `loadJob?.cancel()` in `HomeViewModel`, but #73 takes the SharedFlow-trigger route per the issue body's recommendation. Both are valid; this one is more Flow-shaped (avoids mutable `Job?` field). Reviewer may want to flag whether the project should standardize on one or the other going forward.

### #74 → PR #85 — `fix(#74): wrap SingleEventEffect collector in rememberUpdatedState`
- Branch: `wave/2026-05-02-bug-hunt/issue-74-single-event-rememberupdated`
- Files (1):
  - `common/.../CommonFlowExtensions.kt` — `val currentCollector by rememberUpdatedState(collector)` + `LaunchedEffect(sideEffectFlow, lifecycleOwner, lifeCycleState)`. KDoc paragraph added documenting the safety guarantee for callers closing over screen-local state.
- Tests: none added. No existing tests cover `SingleEventEffect`; meaningful coverage requires Compose UI test infrastructure that isn't wired up for `:common`.
- Surprises:
  - Worktree-local `local.properties` had to be created (gitignored, points at user's Android SDK) — Gradle resolves it relative to the worktree root, not the main checkout. Worth noting for future implementer agents.
  - Gradle wrapper lost its executable bit in this worktree; agent invoked `sh ./gradlew` to work around (sandbox blocked `chmod`). Not in the diff.

## Sanity-check results

| PR  | state | base  | head                                         | commits ahead of dev | mergeable |
|-----|-------|-------|----------------------------------------------|----------------------|-----------|
| #84 | OPEN  | `dev` | issue-71-deal-details-cancellation           | 1                    | yes       |
| #86 | OPEN  | `dev` | issue-72-giveaways-flatmaplatest             | 1                    | yes       |
| #87 | OPEN  | `dev` | issue-73-game-reload-trigger                 | 1                    | yes       |
| #85 | OPEN  | `dev` | issue-74-single-event-rememberupdated        | 1                    | yes       |

Pass: `done` × 4.

## Cwd-shift mitigation

This wave dispatched 4 worktree-isolated agents in parallel and used `git -C /Users/bam/REPO/PRIVATE/game-deals-android-app …` for all orchestrator git commands. No cwd drift observed.

## Deferred to wave 2 / later

- **#75** — depends_on #72 (issue body: "Fix #72 first; this issue closes as a consequence."). Possible side-effect closure once #86 merges; verify before re-queuing.
- **#77** — depends_on #72/#75. Same as above; verify after #86 merges whether this still reproduces.
- **#76** — wave cap reached. Re-plan in wave 2 with a sharper file-walk: confirm whether `List<…>` fields on `SearchParameters` should also retype to `ImmutableList`, and walk consumer modules for the dep-add per L-2026-05-02-03.
