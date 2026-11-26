# Campaign summary — 2026-05-06-bug-hunt-severity-low

**Labels:** `bug-hunt` + `severity:low` · **Status:** complete · **Created:** 2026-05-06 · **Closed:** 2026-05-07

## Totals

- Waves: 1
- Issues attempted: 6
- Succeeded: 6
- Failed: 0
- Success rate: 100%
- PRs opened: 1 (batched)
- PRs merged: 1

## Shape

Single-branch / commit-per-issue / one PR — explicitly requested by the user instead of the default PR-per-issue. The six findings were all Low / Low-confidence latent foot-guns where six separate PRs would have been wrong on cost/value.

## PRs

| PR | Branch | Status | Issues closed |
|---|---|---|---|
| [#134](https://github.com/Mithrandir21/game-deals-android-app/pull/134) | `bug-hunt/2026-05-06-severity-low-batch` | merged 2026-05-07 | #125, #126, #127, #128, #129, #130 |

## Per-issue outcomes

| # | Title | Commit | Status |
|---|---|---|---|
| 125 | TopAppBarDefaults.pinnedScrollBehavior reallocated on every recomposition | `414cc20` | merged |
| 126 | Shared NSDateFormatter at module scope on iOS | `f27d522` | merged |
| 127 | LoggerImpl.loggers is unsynchronized with public mutators | `e1488e1` | merged |
| 128 | KeyValueBackend.commit() thread contract only in a code comment | `c8d6f20` | merged |
| 129 | SearchViewModel.searchGames per-field merge against replayCache is racy | `1caf566` | merged |
| 130 | Filters rememberSaveable initial-value not re-keyed on parent change | `d400f5f` | merged |

## Lesson promotion

- **L-2026-05-06-05** — `TopAppBarDefaults.pinnedScrollBehavior` allocates per recomposition via the default `canScroll` lambda, not the wrapper. Promoted to `.claude/lessons.md` on 2026-05-06.

Weaker candidate (SharedFlow ↔ StateFlow swap semantics) kept campaign-only — it's a corollary of an existing lesson rather than a fresh one.

## Notes

- Most interesting finding: #125's bug-hunt fix description was directionally correct but misidentified the mechanism. Worker dug into Material3 source to find the real cause (default lambda parameter, not the wrapper). This was the source of L-2026-05-06-05.
- The batched-PR shape worked well for this quality tier. Worth promoting to a first-class option in the `/github-issue-waves` skill for similar sweeps.
- No sub-agent failures, no sanity-check failures, no re-entries.

## Unresolved

None.
