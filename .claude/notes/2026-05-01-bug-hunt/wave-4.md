# Wave 4 — 2026-05-01

## Summary

- Issues attempted: 1 (#38)
- PRs opened: 1 (#70)
- Failures: 0
- Wave 3 PRs (#67, #68, #69) all merged before this wave dispatched.
- Stale-base guard from L-wave-3 applied: `git fetch origin dev` *before* dispatch confirmed worktree base = `2a9e5fe`.

## Issues

### #38 — Screen state classes annotated `@Immutable` AND migrated to `ImmutableList` ✓
- PR: [#70](https://github.com/Mithrandir21/game-deals-android-app/pull/70) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-38-immutable-screen-state`
- Diff: +75 / -31 across 20 files, 1 commit
- Approach: **belt-and-suspenders** per user direction. Each affected screen-state container class (`HomeScreenData`, `GiveawaysScreenData`, `SearchData.SearchResults`, `GameScreenData.Data`) gained a `@Immutable` annotation **and** had every `List<X>` field migrated to `ImmutableList<X>`. Empty defaults use `persistentListOf()`; producers convert at the boundary with `.toImmutableList()`.
- Catalog/build: added `kotlinx-collections-immutable = "0.4.0"` to `gradle/libs.versions.toml`, wired the dep into `feature/{home,giveaways,search,game}/build.gradle.kts`. Used 0.4.0 (latest stable) instead of the suggested 0.3.8.
- `GameScreenData.Data.dealDetails` was migrated as `ImmutableList<Pair<Store, GameDeal>>` — kept the inner `Pair` (conservative path; `Pair` itself is unstable but refactoring it would have widened scope into the screen layer). Flagged in the PR body.
- `common/ui/.../DealBottomSheet.kt` already had `@Immutable` and no `List<...>` fields — untouched.
- `feature/search/.../SearchScreen.kt` had no locally-scoped state class (only the preview composable, which was updated as a caller).
- Caller updates: 10 sites across 4 preview composables + 7 test files. Mechanical: `listOf(...)` → `persistentListOf(...)` or `.toImmutableList()` on existing lists. No assertions loosened. The L-2026-05-01-07 `size == 1` emission shape preserved across the affected ViewModelTests.
- Tests: `:feature:{home,giveaways,search,game}:test` BUILD SUCCESSFUL; `:feature:{...}:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL (compile-only validation of the type changes for instrumented tests).
- Inline-comment rule: spot-checked diff with grep — zero `// #38`, zero "for stability/skipping" comments. Wave-3 prompt addition is holding.

## Sanity check

| PR | state | base | additions | deletions | commits |
|---|---|---|---|---|---|
| #70 | OPEN | dev | 75 | 31 | 1 |

Single PR, passes.

## Notes

- **Stale-base regression did NOT recur.** The wave-3 lesson was applied: orchestrator ran `git fetch origin dev` immediately before dispatching the agent, and the per-agent prompt explicitly instructed branching off `origin/dev = 2a9e5fe`. Worktree forked correctly; PR #70's commits sit cleanly on top of current dev with no replayed/reverted prior-PR hunks.
- **Inline-comment cleanup is sticking.** Wave-3 PRs were clean; PR #70 also clean. The orchestrator-prompt addition that explicitly cites the AGENTS.md no-comments rule appears to be sufficient to prevent the `// per #N` regression that surfaced post-PR-#66.
- **Real finding: `.claude/CLAUDE.md` imports `@AGENTS.md` but `AGENTS.md` does not exist anywhere in the repo.** Confirmed via `find . -iname AGENTS.md`. This means the entire CLAUDE.md is effectively empty — none of the project conventions documented in AGENTS.md (if it ever existed) are being loaded into Claude's context via the project-instructions channel. Conventions have been propagated via memory (`feedback_no_inline_issue_comments.md`), per-prompt instructions, and `.claude/lessons.md` instead. The user should restore `AGENTS.md` (or update CLAUDE.md to import the right path) so future sessions get the conventions auto-loaded.
- **Catalog version pick.** Agent chose `kotlinx-collections-immutable = 0.4.0` after verifying it was the latest stable on Maven Central. The orchestrator-suggested 0.3.8 was a stale guess; 0.4.0 is the right call.

## Campaign status

After PR #70 merges:
- Resolved: 13/13 issues (#30, #31, #34, #35, #37, #39, #40, #41, #42 from waves 1–2 + #34 dup; #30, #32, #33, #36 from wave 3; #38 from wave 4).
- Open: 0.
- Campaign expected to be **complete** on next re-entry — `gh issue list --label bug-hunt --state open` should return zero, triggering the skill's wrap-up path (write `summary.md`, mark `status: complete`, run final promotion gate).
