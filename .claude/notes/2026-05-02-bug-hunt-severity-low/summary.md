# Campaign summary — 2026-05-02-bug-hunt-severity-low

**Labels:** `bug-hunt` + `severity:low`
**Created:** 2026-05-02
**Completed:** 2026-05-02 (same-day)
**Status:** complete

## Numbers

| Metric             | Value |
|--------------------|-------|
| Waves              | 2     |
| Issues attempted   | 3     |
| Issues succeeded   | 3     |
| Issues failed      | 0     |
| PRs opened         | 3     |
| PRs merged         | 3     |
| Success rate       | 100%  |
| Lessons promoted   | 3     |

## Issues and PRs

| Issue | PR | Title | Wave |
|-------|----|-------|------|
| [#78](https://github.com/Mithrandir21/game-deals-android-app/issues/78) | [#81](https://github.com/Mithrandir21/game-deals-android-app/pull/81) | Several ViewModels read-modify-write `_uiState.value.copy(...)` instead of `update {}` | 1 |
| [#79](https://github.com/Mithrandir21/game-deals-android-app/issues/79) | [#82](https://github.com/Mithrandir21/game-deals-android-app/pull/82) | `GiveawaySearchParameters` fields `List<Pair<...>>` defeat Compose skipping | 1 |
| [#80](https://github.com/Mithrandir21/game-deals-android-app/issues/80) | [#83](https://github.com/Mithrandir21/game-deals-android-app/pull/83) | `GameDetails.deals: List<GameDeal>` is unstable | 2 |

## Wave shape

- **Wave 1 (2 issues, 0 deferred-after):** #78 + #79 dispatched in parallel. Conflict graph deferred #80 because both #79 and #80 added `kotlinx-collections-immutable` to `domain/build.gradle.kts`.
- **Wave 2 (1 issue):** #80 alone, after #79 (PR #82) merged. Pre-wave plan correctly skipped `domain/build.gradle.kts` since the dep was already present.

## Lessons promoted to `.claude/lessons.md`

- **L-2026-05-02-01** — `MutableStateFlow.update { it.copy(...) }` for field-level merges; `emit(...)` only for full-state replacements (from #78)
- **L-2026-05-02-02** — `@Immutable` + `ImmutableList<…>` on every domain model used as a composable parameter (from #79; subsumes #38, pending #80 at the time)
- **L-2026-05-02-03** — Gradle `implementation` is non-transitive on the compile classpath; consumer modules need the dep added explicitly when they import extensions on a `:domain` type that adopts a new third-party type (from #80)

## Operational notes

- **Wave 1: cwd-shift artefact.** The orchestrator's bash cwd drifted into one of the spawned worktrees mid-wave (the `agent-ab705b51` worktree for #79). All file writes used absolute paths so nothing was misplaced; main repo working tree was intact post-recovery. **Mitigation applied in wave 2:** all orchestrator `git` commands invoked with `git -C /Users/bam/REPO/PRIVATE/game-deals-android-app …`. Worked cleanly. **Recommendation for skill update:** consider adding a "always use `git -C <main-repo-abs-path>`" note to the SKILL.md, or have the orchestrator capture-and-restore the cwd around `isolation: "worktree"` agent dispatches.

- **Wave 1: implementer agent worktree mishap.** The #79 implementer agent self-reported that its first round of `Edit` calls accidentally landed in the main repo (still on `dev`, no commits) and were reverted via `git checkout --` before re-applying inside the worktree. Recovered cleanly, but a `git checkout --` against unrelated user files is a near-miss class of action. The agents' system prompts may want a stronger cwd-check / "verify you are inside `.claude/worktrees/agent-*` before any `Edit`" guard.

- **Wave 2: planner under-report.** The wave-1 planner for #80 listed 5 files; the actual fix needed 7 (added `remote/cheapshark/build.gradle.kts` for the cross-module dep, and 2 test files for `persistentListOf` constructor updates). This was the source of `L-2026-05-02-03`. **Heuristic for future planners:** for any retype that introduces a third-party-typed field on a `:domain` data class, the planner should walk every dependent module's import-graph for extensions on that type *before* committing to a file list.

- **Sanity-check process performed exactly as spec'd:** branch existence on origin, commits ahead of `dev`, PR open against `dev` — all 3 PRs passed cleanly.

## What next

- All three PRs merged. No follow-up work in this label set.
- Open `bug-hunt` issues at higher severities (`severity:medium`, `severity:high`) remain candidates for the next campaign — invoke `/github-issue-waves bug-hunt severity:medium` (or just `bug-hunt` for a broader sweep) when ready.
