# Campaign summary — 2026-05-01-bug-hunt

**Status:** complete · **Created:** 2026-05-01 · **Completed:** 2026-05-01
**Labels:** `bug-hunt`

## Headline

13 issues handled, 12 PRs merged, 1 closed-as-duplicate (#40, subsumed by #34), 1 closed-already-fixed (#32, fixed by earlier PR #50). Zero failed sub-agents. Campaign ran across 4 waves on a single day.

## Waves

| Wave | Issues attempted | PRs opened | Merged | Other |
|------|------------------|------------|--------|-------|
| 1    | 4 (#31, #35, #41, #42)              | #60, #62, #61, #63 | 4/4 |   |
| 2    | 3 (#34, #37, #39)                   | #64, #66, #65      | 3/3 | #40 closed as duplicate via #34's `Closes #40` |
| 3    | 4 (#30, #32, #33, #36)              | #67, #68, #69      | 3/3 | #32 closed without PR — already fixed by PR #50 (typed Compose Navigation) |
| 4    | 1 (#38)                             | #70                | 1/1 |   |
| **Total** | **13** | **11 PRs** | **11 merged** | 2 closed without PR |

## All PRs (chronological)

| PR | Issue | Title | Merged |
|----|-------|-------|--------|
| #60 | #31 | `fix(#31): rethrow CancellationException in DealsMediator.load`               | 17:58 |
| #62 | #35 | `fix(#35): add stable keys to Home and Giveaways LazyColumn items`            | 18:00 |
| #61 | #41 | `fix(#41): use LocalActivity in GameDealsTheme to avoid ClassCastException`   | 18:01 |
| #63 | #42 | `fix(#42): make Storage suspend and run prefs I/O off main thread`            | 21:11 |
| #64 | #34 | `fix(#34): load URL in factory and guard WebView.update against reloads`      | 21:30 |
| #65 | #39 | `fix(#39): prevent stale snackbar retry/reload lambdas in LaunchedEffect`     | 21:40 |
| #66 | #37 | `fix(#37): replace _uiState.stateIn(WhileSubscribed) with asStateFlow()`      | 21:54 |
| #67 | #30 | `fix(#30): destroy WebView on composable disposal`                            | 22:10 |
| #68 | #33 | `fix(#33): cancel prior collector before relaunching loadTopStoresDeals`      | 22:23 |
| #69 | #36 | `fix(#36): emit LOADING before awaiting refreshGiveaways`                     | 22:23 |
| #70 | #38 | `fix(#38): annotate + migrate screen state to ImmutableList`                  | 22:48 |

## Lessons promoted to project-wide `.claude/lessons.md`

- **L-2026-05-01-06** · `LocalActivity.current` (null-safe) over `view.context as Activity` in `:common:ui` — from #41
- **L-2026-05-01-07** · `UnconfinedTestDispatcher` + `init {}` ⇒ expect `size == 1` for ViewModel emission tests — from #37
- **L-2026-05-01-08** · `ApplicationInfo.FLAG_DEBUGGABLE` over `BuildConfig.DEBUG` for one-off debug gates — from #42
- **L-2026-05-01-09** · `AndroidView` lifecycle: hoist clients via `remember`, wire `onRelease` for true teardown — from #30

(L-2026-05-01-05 on virtual-time `delay()` was promoted via the parallel `2026-05-01-bug-hunt-severity-low` campaign, not this one.)

## Notable findings (non-promoted)

- **Stale-base regression** in wave-3 dispatch (PR #68 and #69) — orchestrator did not `git fetch origin dev` between observing wave-2 PRs merge and dispatching agents. Fixed via post-hoc rebase. Wave-4 dispatch applied the lesson; no recurrence. *Skill enhancement: orchestrator should fetch the base branch immediately before each wave dispatch.*
- **Inline-comment regression** in wave-2 PR #66 — sub-agent inserted ~28 lines of `// per #37`-style comments at every test assertion site. Cleaned up post-merge. Wave-3/4 prompts explicitly cited the AGENTS.md no-comments rule and zero comments slipped through across 4 PRs (#67–#70). *User feedback memory created: `feedback_no_inline_issue_comments.md`.*
- **Planner-time issue triage worked once.** #32 was opened during the bug-hunt sweep before PR #50 (typed Compose Navigation) landed. Planner verified via grep that the named APIs (`setStoreId`, `loadGameDetails`, `LaunchedEffect(storeId)`) are all gone — closed without dispatching. *Skill enhancement: when planner reports `already fixed by PR #N, close as duplicate`, orchestrator should short-circuit dispatch and write `status: closed_already_fixed`.*
- **`.claude/CLAUDE.md` imports `@AGENTS.md` which does not exist.** Confirmed via `find . -iname AGENTS.md` (zero matches). Project conventions have been propagating via `.claude/lessons.md`, per-prompt instructions, and user auto-memory. Surfaced for the user to address; not a campaign-coding lesson.
- **Worktree env quirks** (missing `local.properties`, non-executable `gradlew`, system Java 17 incompatible with `:build-logic:convention`) recurred across **every** sub-agent (8/8 across this campaign). Each had to run the same three-step setup (copy local.properties, `bash gradlew`, set JAVA_HOME to Android Studio's JBR 21). *Skill enhancement: pre-seed the worktree before handing off, or document the requirement in the per-agent prompt.*

## Skill-improvement candidates (drafted from this campaign — review separately)

1. **Pre-fetch the base branch before each wave dispatch** to prevent stale-base agents.
2. **Pre-seed worktree env** (`local.properties`, executable `gradlew`, JAVA_HOME hint) so sub-agents don't burn cycles re-discovering it.
3. **Honor planner "already fixed" verdicts** with a short-circuit close-no-PR path.
4. **Hardcode the no-inline-comment rule** in the per-agent prompt template, not just upstream in AGENTS.md/feedback memory.

## Closing

Campaign moved from "13 open `bug-hunt` issues" to zero in a single day across four orchestrated waves. The skill's wave-then-merge-then-rewave cadence held up; conflict detection was correct (no inter-PR merge conflicts at merge time); and four durable project-wide lessons (L-…06 through L-…09) were promoted to `.claude/lessons.md` for future sessions.
