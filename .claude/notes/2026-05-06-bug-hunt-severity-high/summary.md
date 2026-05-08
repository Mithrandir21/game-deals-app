# Campaign summary — 2026-05-06-bug-hunt-severity-high

**Status:** complete · **Started:** 2026-05-06 · **Completed:** 2026-05-06 (same-day)
**Labels:** `bug-hunt`, `severity:high`
**Waves:** 1 · **Issues attempted:** 1 · **Succeeded:** 1 · **Failed:** 0 · **Success rate:** 100%

The single open `bug-hunt`+`severity:high` issue at campaign start was resolved in one same-day wave.

## All PRs (merged)

| Issue | Severity | PR | Title |
|---|---|---|---|
| #122 | high | [#131](https://github.com/Mithrandir21/game-deals-android-app/pull/131) | `fix(#122): rethrow CancellationException in GiveawaysViewModel.reloadGiveaways` |

## Wave breakdown

### Wave 1 (2026-05-06) — 1 issue

- **#122** — sole candidate. Direct application of L-2026-05-02-04 (rethrow `CancellationException` before generic `catch (Throwable)` blocks containing suspending work). Fix is one new catch clause + one new test.
- No conflict deferrals (single-issue wave).
- No surprises. Worker required `JAVA_HOME=Android Studio JBR 21` per existing reference memory; system default JDK 17 is insufficient for the wrapper.
- Sanity-check: PR open against `dev`, 1 commit ahead, base/head correct. Transient `gh pr view` 504 on first attempt, immediate retry succeeded — GitHub API blip, not a sub-agent failure.

See [`wave-1.md`](./wave-1.md).

## Lessons promoted to project-wide

*(none)* — the only insight was that L-2026-05-02-04 had survived to a third file (`GiveawaysViewModel`) after being established by #71 (`DealDetailsController`) and #31 (`DealsMediator`). That confirms the existing lesson; no new lesson to add.

## Lessons kept campaign-only

See [`lessons.md`](./lessons.md):

- A process tip: when a lesson is added based on one or two sites, a project-wide grep for the antipattern is worth doing at lesson-creation time, not just leaving the next bug hunt to find the third occurrence. Recorded as campaign-only because it's a tip about the lessons system itself, not a code/architecture lesson — and a single observation isn't enough to justify a project-wide entry.

## Operational notes

- **Sub-agent failure rate:** 0/1.
- **Sanity-check failures:** 0 (one transient API timeout on first probe; retry succeeded).
- **Re-entries:** 1 (after PR #131 merged, for wrap-up).
- **Parallel concurrency:** wave 1 ran 1 worktree agent (no parallelism needed).
- **Same-day completion:** filing → fix → merge → wrap-up all on 2026-05-06.

## Remaining `bug-hunt` open issues at campaign close

8 issues remain open under `bug-hunt`, none `severity:high`:

| Issue | Severity | Effort | Confidence | Title (truncated) |
|---|---|---|---|---|
| #123 | medium | small | medium | Lazy Koin first-access opens SQLite on Main |
| #124 | medium | trivial | medium | `StoreViewModel.deals` has no `.catch` |
| #125 | low | trivial | medium | `pinnedScrollBehavior` reallocated each recomposition |
| #126 | low | trivial | low | Shared `NSDateFormatter` on iOS |
| #127 | low | trivial | low | `LoggerImpl.loggers` unsynchronized |
| #128 | low | trivial | low | `KeyValueBackend.commit()` contract |
| #129 | low | trivial | low | `SearchViewModel.searchGames` replayCache race |
| #130 | low | small | low | `Filters` `rememberSaveable` not re-keyed |

Natural next campaigns: `bug-hunt,severity:medium` (2 issues) or `bug-hunt,severity:low` (6 issues, all latent foot-guns flagged for awareness).
