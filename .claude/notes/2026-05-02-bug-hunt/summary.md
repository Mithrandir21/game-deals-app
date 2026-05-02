# Campaign summary — 2026-05-02-bug-hunt

**Labels:** `bug-hunt` (single label, all severities)
**Created:** 2026-05-02
**Completed:** 2026-05-02 (same-day)
**Status:** complete

## Numbers

| Metric             | Value |
|--------------------|-------|
| Waves              | 2     |
| Issues attempted   | 7     |
| Issues succeeded   | 7     |
| Issues failed      | 0     |
| PRs opened         | 6     |
| PRs merged         | 6     |
| Success rate       | 100%  |
| Lessons promoted   | 5     |

PR count is one fewer than issue count because **#75 + #77 were bundled into a single PR (#89)** — same root cause, same architectural fix, same file. Surfaced deviation from the skill's "one PR per issue" default; user-confirmed at the wave-2 proposal gate.

## Issues and PRs

| Issue | PR | Title | Wave |
|-------|----|-------|------|
| [#71](https://github.com/Mithrandir21/game-deals-android-app/issues/71) | [#84](https://github.com/Mithrandir21/game-deals-android-app/pull/84) | DealDetailsController.load swallows CancellationException | 1 |
| [#72](https://github.com/Mithrandir21/game-deals-android-app/issues/72) | [#86](https://github.com/Mithrandir21/game-deals-android-app/pull/86) | GiveawaysViewModel.loadGiveaway leaks long-lived Room collector | 1 |
| [#73](https://github.com/Mithrandir21/game-deals-android-app/issues/73) | [#87](https://github.com/Mithrandir21/game-deals-android-app/pull/87) | GameViewModel.reloadGameDetails launches parallel collector | 1 |
| [#74](https://github.com/Mithrandir21/game-deals-android-app/issues/74) | [#85](https://github.com/Mithrandir21/game-deals-android-app/pull/85) | SingleEventEffect captures lambda without rememberUpdatedState | 1 |
| [#75](https://github.com/Mithrandir21/game-deals-android-app/issues/75) | [#89](https://github.com/Mithrandir21/game-deals-android-app/pull/89) | reloadGiveaways races with init Room collector on _uiState (bundled) | 2 |
| [#76](https://github.com/Mithrandir21/game-deals-android-app/issues/76) | [#88](https://github.com/Mithrandir21/game-deals-android-app/pull/88) | SearchParameters.equals overridden defeats Compose skipping | 2 |
| [#77](https://github.com/Mithrandir21/game-deals-android-app/issues/77) | [#89](https://github.com/Mithrandir21/game-deals-android-app/pull/89) | reloadGiveaways flow has no SUCCESS emission of its own (bundled with #75) | 2 |

## Wave shape

- **Wave 1 (4 issues, 1 deferred-after, 2 deferred-by-dependency):** #71 + #72 + #73 + #74 dispatched in parallel. All 4 file-disjoint. #76 deferred (wave cap of 4); #75 + #77 deferred (logical dep on #72).
- **Wave 2 (3 issues / 2 PRs):** #75 + #77 bundled into one PR (#89) since the post-#86 verification confirmed both still described live bugs and shared the same fix on the same file. #76 in parallel as PR #88.

## Lessons promoted to `.claude/lessons.md`

Five wave-1+wave-2 lessons promoted (running ID series from the prior `2026-05-02-bug-hunt-severity-low` campaign continued):

- **L-2026-05-02-04** — `try { … } catch (Throwable)` blocks containing suspending work must rethrow `CancellationException` first (from #71)
- **L-2026-05-02-05** — Hot-source races need `MutableSharedFlow` in tests, not `flowOf(...)` — `flowOf` completes after one emission and masks second-emission bugs (from #72)
- **L-2026-05-02-06** — Compose `LaunchedEffect` capturing a caller-provided lambda must wrap it in `rememberUpdatedState` (from #74)
- **L-2026-05-02-07** — `StateFlow` conflation of identical-equals emissions: fix at the flow boundary (`MutableSharedFlow(replay=1, DROP_OLDEST)`), not by breaking `equals` (from #76)
- **L-2026-05-02-08** — `combine`-with-trigger flows: reason explicitly about write-order between trigger updates and downstream `_uiState` mutations (from #75/#77)

Two campaign-only lessons declined at promotion (orchestration/process insights, not project patterns):
- Predictive issue bodies don't auto-close issues — verify post-merge.
- Bundling architecturally-identical issues into one PR is acceptable when file-set conflicts would otherwise serialize them across waves.

## Operational notes

- **Wave-1 cwd-shift mitigation worked.** Continued the `git -C /Users/bam/REPO/PRIVATE/game-deals-android-app …` pattern from the prior campaign across all 6 worktree dispatches. No cwd drift.

- **Worktree `local.properties` recurrence.** Three of 6 implementer agents (#74, #75/#77, #76) needed to create or copy `local.properties` into the worktree root for Gradle's `sdk.dir`. Pattern is now consistent enough to bake into the implementer-agent prompt template — a one-line `cp ../../local.properties .` (or equivalent gitignored copy) in the agent's pre-flight checklist would skip three rounds of discovery. **Recommendation for skill update:** add to the per-issue sub-agent prompt template under Step 4: "On worktree entry, copy `local.properties` from the parent checkout if it exists — Gradle resolves `sdk.dir` relative to the worktree root, not the main repo."

- **Pre-wave verification of "auto-resolved" deferred issues paid off.** Wave 1 deferred #75 and #77 because their bodies claimed they'd auto-resolve once #72 merged. After PR #86 (issue #72) merged, the wave-2 orchestrator re-read the post-merge `GiveawaysViewModel.kt` from `origin/dev` *before* re-queuing them, and confirmed both still described live bugs in a different shape. This avoided closing them prematurely and surfaced the campaign-only lesson "predictive issue bodies don't auto-close — verify post-merge." **Worth folding into the SKILL.md** as a re-entry checklist item: when a deferred issue's body predicted auto-closure, verify against post-merge code before deciding.

- **Bundling deviation worked cleanly.** #75 + #77 → one PR (#89) with `Closes #75. Closes #77.` was justified by (a) same file (would have forced two waves), (b) same architectural fix (would have forced rework if split). User-gated at the proposal step. Resulted in 6 PRs for 7 issues with no churn. Skill spec doesn't formally support bundling but this case was the right call.

- **Sanity-check process performed exactly as spec'd:** branch existence on origin, commits ahead of `dev`, PR open against `dev` — all 6 PRs passed cleanly across both waves.

- **Planner under-report watch held.** Issue #76 was a high-risk candidate for the L-2026-05-02-03 trap (planner under-reports cross-module dep additions on `:domain` retypes). The wave-2 planner explicitly checked: SearchParameters has no List fields, so no ImmutableList migration was needed and no cross-module `build.gradle.kts` changes. Implementer confirmed. Heuristic continues to work when planners are explicitly prompted with the past trap as context.

## What next

- All issues resolved. No follow-up work in the `bug-hunt` label.
- Across both same-day campaigns (`2026-05-02-bug-hunt-severity-low` + `2026-05-02-bug-hunt`), 10 issues fixed via 9 PRs. The audit-skill backlog is now empty.
- If the `android-bug-hunt` audit skill is re-run and surfaces new issues under `bug-hunt`, those will form the next campaign.
