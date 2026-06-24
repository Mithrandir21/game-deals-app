# Campaign Summary — bug-hunt severity:low

Campaign slug: `2026-05-15-bug-hunt-severity-low`
Created: 2026-05-15
Completed: 2026-05-15
Status: **complete**

## Totals

- **Waves:** 2
- **Issues attempted:** 6 (#147, #149, #150, #151, #152, #153)
- **Issues resolved via PR:** 6
- **Issues failed:** 0
- **Issues relabeled out of campaign:** 1 (#148 → `tech-debt` / `area:kmp`)
- **Success rate:** 6 / 6 = 100%

## PRs (all merged into `dev`)

| Wave | Issue | PR | Title |
|---|---|---|---|
| 1 | #147 | [#163](https://github.com/Mithrandir21/game-deals-android-app/pull/163) | `fix(compose-stability): replace Pair<Store, …> with @Immutable wrappers` |
| 1 | #149 | [#162](https://github.com/Mithrandir21/game-deals-android-app/pull/162) | `refactor(giveaways): use WhileSubscribed(5_000) for uiState stateIn` |
| 1 | #150 | [#161](https://github.com/Mithrandir21/game-deals-android-app/pull/161) | `fix(domain): atomic toggleFavourite via DAO @Transaction` |
| 1 | #153 | [#160](https://github.com/Mithrandir21/game-deals-android-app/pull/160) | `refactor(app): hoist application-scoped CoroutineScope for cold-start work` |
| 2 | #151 | [#164](https://github.com/Mithrandir21/game-deals-android-app/pull/164) | `refactor(app): dispatch Sentry.init off Main via applicationScope` |
| 2 | #152 | [#165](https://github.com/Mithrandir21/game-deals-android-app/pull/165) | `feat(remote): add parameterless httpClient() overload for Swift interop` |

## Per-wave notes

- **Wave 1** (4 issues, all disjoint modules): #147 was the biggest fix — 8 modified files + 2 new `@Immutable` wrapper data classes across `:feature:game` and `:common:ui`. The other three were single-file or two-file changes. Notable: #149's `Eagerly`→`WhileSubscribed` swap broke 5 of 11 existing tests (captured as `L-2026-05-15-04`).
- **Wave 2** (2 issues, deferred from wave 1 because of file-conflict + dependency): #151 depended on #153 to land first. #152 was independent. Both small (1 file each). No surprises — both fixes used patterns already captured in lessons.

## Relabeled out

- **#148** Sealed `Destination` loses Swift exhaustiveness (no SKIE) → moved to `tech-debt` / `area:kmp`. Recommended fix was "Adopt SKIE" — a project-wide infrastructure decision, not a PR-sized fix. Watchlist tracker; will be revisited when/if SKIE is adopted.

## Lessons promoted to project-wide `.claude/lessons.md`

Three lessons drafted from this campaign were promoted (Step 7 confirm gate):

- `L-2026-05-15-04` — `Eagerly`→`WhileSubscribed(5_000)` is not test-transparent (5 test patterns to expect to update). Drafted from #149/#162.
- `L-2026-05-15-05` — `Pair<A, B>` is unstable in Compose regardless of `ImmutableList` wrapping; refines `L-2026-05-02-02`. Drafted from #147/#163.
- `L-2026-05-15-06` — Room `@Transaction suspend fun` default methods on `interface` DAOs work cleanly on Room 2.8.x KMP for both Android and iOS KSP. Drafted from #150/#161.

## Open follow-ups

- **#148** remains open in the `tech-debt` lane — revisit when SKIE adoption is on the table.
- Lingering worktrees in `.claude/worktrees/agent-*/` are locked because the sub-agents made commits. Now that all PRs are merged, they can be removed with `git worktree remove <path>` per agent. There are 10 across all three campaigns from today (high + medium + low).

## No unresolved failures

Zero issues failed in either wave. Zero sub-agent failure reports recorded.
