# Campaign summary — 2026-05-06-bug-hunt-severity-medium

**Labels:** `bug-hunt` + `severity:medium` · **Status:** complete · **Created:** 2026-05-06 · **Closed:** 2026-05-06

## Totals

- Waves: 1
- Issues attempted: 2
- Succeeded: 2
- Failed: 0
- Success rate: 100%
- PRs opened: 2
- PRs merged: 2

## PRs

| PR | Branch | Status | Issue |
|---|---|---|---|
| [#133](https://github.com/Mithrandir21/game-deals-android-app/pull/133) | `wave/2026-05-06-bug-hunt-severity-medium/issue-123-warm-database-on-startup` | merged 2026-05-06 | #123 |
| [#132](https://github.com/Mithrandir21/game-deals-android-app/pull/132) | `wave/2026-05-06-bug-hunt-severity-medium/issue-124-store-deals-catch` | merged 2026-05-06 | #124 |

## Per-issue outcomes

| # | Title | Status |
|---|---|---|
| 123 | Lazy Koin first-access opens SQLite on Main during first composition | merged (partial fix — see notes) |
| 124 | StoreViewModel.deals has no .catch; one refresh failure permanently empties the StateFlow | merged |

## Lesson promotion

- **L-2026-05-06-04** — Test mocking library is Mokkery, not MockK (KMP `commonTest` needs a multiplatform mocker; `docs/patterns/testing.md` is stale). Promoted to `.claude/lessons.md` on 2026-05-06.

Process tips about issue-body-vs-build-classpath checks were kept campaign-only — they're workflow corollaries rather than fresh project-wide lessons.

## Notes

- **#123 is a partial fix.** Worker took the smaller path (resolve the Koin singleton off-main, forcing `Room.Builder.build()` off-main) rather than the issue body's recommended `db.openHelper.writableDatabase` call — the latter wasn't reachable from `:app` because `:domain` declares Room as `implementation` rather than `api`. The SQLite file is still opened lazily on first DAO query, but first DAO access in this codebase is always on a coroutine dispatcher, so the originally-identified Main-thread cost is gone. Two clean follow-ups would expose a `suspend fun warm()` on `DomainDatabase` or widen `:app` to depend on `androidx.room.runtime`.
- **#124's regression test** was the source of L-2026-05-06-04 — worker noticed the issue body recommended MockK but the codebase uses Mokkery in `commonTest`.
- No sub-agent failures, no sanity-check failures. One transient `gh pr create` 504 hit the #124 worker, retry succeeded — same blip as a prior wave.

## Unresolved

None — both PRs merged, no follow-ups currently in scope.
