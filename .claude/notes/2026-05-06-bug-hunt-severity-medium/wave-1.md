# Wave 1 — campaign 2026-05-06-bug-hunt-severity-medium

**Issues attempted:** 2 · **Succeeded:** 2 · **Failed:** 0

Both severity:medium issues from this morning's bug hunt resolved in parallel. File sets fully disjoint (`:app` vs `:feature:store`), so the two worktree workers ran without coordination.

## Issues

### #123 — Lazy Koin first-access opens SQLite on Main — ✅ open

- **PR:** [#133](https://github.com/Mithrandir21/game-deals-android-app/pull/133) — `fix(#123): warm DomainDatabase off-main in Application.onCreate`
- **Branch:** `wave/2026-05-06-bug-hunt-severity-medium/issue-123-warm-database-on-startup` (base: `dev`, 1 commit ahead)
- **Files:**
  - `app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt` — added a `warmDomainDatabase()` helper that runs `getKoin().get<DomainDatabase>()` on `CoroutineScope(SupervisorJob() + Dispatchers.IO)` immediately after `startKoin {}`. Wrapped in try/catch with `CancellationException` rethrow per L-2026-05-02-04; failures log via project `Logger`.
- **Worker compromise (worth surfacing for the reviewer):** the issue body recommended also calling `db.openHelper.writableDatabase`, but `:app` doesn't have `androidx.room` on its compile classpath (`:domain` declares Room as `implementation`, not `api`), so accessing `RoomDatabase.openHelper` is unreachable from `GameDealsApplication` without widening `:app/build.gradle.kts` (out of scope for a one-file additive fix). Worker took the smaller path of just resolving the singleton. Effect: `Room.Builder.build()` (the actual cost the issue identifies) runs off-main; the SQLite file is then opened lazily on the first DAO query, which is already on a coroutine dispatcher in this codebase. The PR body documents this trade-off explicitly.

### #124 — `StoreViewModel.deals` has no `.catch` — ✅ open

- **PR:** [#132](https://github.com/Mithrandir21/game-deals-android-app/pull/132) — `fix(#124): catch deals refresh failures so StateFlow doesn't tombstone`
- **Branch:** `wave/2026-05-06-bug-hunt-severity-medium/issue-124-store-deals-catch` (base: `dev`, 1 commit ahead)
- **Files:**
  - `feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt` — added `.catch { emit(persistentListOf()) }` between `.logFlow(logger)` and `.stateIn(...)` on the `deals` chain. Did NOT touch the sibling `pagedDeals` Paging chain (closed #46 settled that one).
  - `feature/store/src/commonTest/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModelTest.kt` — new test `deals_StateFlow_does_not_tombstone_when_refresh_fails`. Mocks `dealsRepository.observeStoreDeals` to return `flow { throw RuntimeException("boom") }` (mimics post-`refreshDeals`-failure path), asserts `viewModel.deals.value == persistentListOf()` and `uiState` still produces `Data(store)`. Worker did inverted-control verification: temporarily reverted the `.catch` line, confirmed the test fails as expected; restored, confirmed it passes.
- **Worker correction:** the issue body and orchestrator prompt said "use MockK". The project actually uses **Mokkery** (KMP-compatible mocking library); MockK is JVM-only and incompatible with `commonTest`. Worker correctly matched the existing `StoreViewModelTest`'s Mokkery patterns (`everySuspend` / `returns` / `throws`). See lessons.md for promotion candidate.

## Conflicts deferred from this wave

None. Two-issue wave with disjoint file sets.

## Sanity-check results

- Both branches present on origin: ✅
- Both PRs open against `dev`, head matches expected branch, both 1 commit ahead: ✅
- Both PR titles match `fix(#NNN): …` convention: ✅
- Worker for #123 self-reported a typo in its title (`fix(#133)` instead of `fix(#123)`), but verified the actual title on GitHub is correct (`fix(#123)`). The typo was only in the worker's text response, not in the PR.
- Transient `gh pr create` 504 hit by the #124 worker on first attempt; immediate retry succeeded — same GitHub-side blip seen in wave 1 of the high-severity campaign. Not a sub-agent failure.

## Notes for reviewer

- **#133 is a partial fix** that addresses the actual cost (the synchronous `Room.Builder.build()`) but leaves the SQLite file open lazy. That's likely fine — first DAO access is always async via Room's dispatcher in this codebase — but if you want full warm-up, consider either (1) widening `:app` to depend on `androidx.room.runtime`, or (2) exposing a `suspend fun warm()` on `DomainDatabase` so `:app` can call it without Room on its classpath. Either is out of scope for this PR but a clean follow-up.
- **#132's regression test deliberately uses the simplest exception shape** (`RuntimeException`) rather than a `RemoteHttpException` from the remote module, because the test only needs to prove that *any* upstream throw doesn't tombstone the StateFlow. That's the clearest regression guard.
