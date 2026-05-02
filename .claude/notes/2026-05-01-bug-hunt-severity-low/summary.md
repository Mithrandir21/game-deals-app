# Campaign summary — 2026-05-01-bug-hunt-severity-low

**Status:** complete
**Labels:** `bug-hunt`, `severity:low`
**Created:** 2026-05-01
**Closed:** 2026-05-01

## Totals

- Issues resolved: 6 / 6
- Waves: 2
- PRs opened: 6
- PRs merged: 6
- Failures: 0

## PRs

| PR | Issue | Wave | Status |
|---|---|---|---|
| [#54](https://github.com/Mithrandir21/game-deals-android-app/pull/54) | #43 — WebView `rememberSaveable` → `remember` | 1 | merged |
| [#55](https://github.com/Mithrandir21/game-deals-android-app/pull/55) | #46 — StoreViewModel paging `.catch` removed | 1 | merged |
| [#56](https://github.com/Mithrandir21/game-deals-android-app/pull/56) | #48 — `fullscreenSemiTransparentBackground` memoized | 1 | merged |
| [#57](https://github.com/Mithrandir21/game-deals-android-app/pull/57) | #47 — Coil `Dispatchers.Default` removed | 1 | merged |
| [#58](https://github.com/Mithrandir21/game-deals-android-app/pull/58) | #44 — Theme `SideEffect` → `LaunchedEffect` | 2 | merged |
| [#59](https://github.com/Mithrandir21/game-deals-android-app/pull/59) | #45 — `*DelayAtLeast` virtual-time fix + tests | 2 | merged |

## Lessons promoted to project-wide

- `L-2026-05-01-04` — Don't `.catch` after `.cachedIn` on a Paging Flow (from #46)
- `L-2026-05-01-05` — Don't mix `System.currentTimeMillis()` with `delay()` in Flow operators (from #45)

## Notes

- Wave 1 (#43, #46, #47, #48) ran 4 worktree-isolated agents in parallel; all 4 succeeded with sanity-check passing.
- Wave 2 (#44, #45) ran 2 agents; #45's agent transparently flagged a scope expansion to `common/build.gradle.kts` for an unavoidable test-dep addition.
- One operational hiccup: #47's worktree was pruned mid-task and had to be re-registered. No work lost. Worth watching if this recurs across future campaigns.
