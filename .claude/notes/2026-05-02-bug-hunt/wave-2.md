# Wave 2 — 2026-05-02

**Summary:** 3 issues attempted, 2 PRs opened (#75/#77 bundled), 0 failed.

## Pre-wave verification

Wave-1 PR #86 (issue #72) merged. The deferred issues #75 and #77 had their bodies state "Fix #72 first; this issue closes as a consequence" and "Treat this issue as the follow-up notes for #72/#75" respectively — i.e. both were predicted to be auto-resolved by #72's fix.

**Verification step before queuing:** read post-#86 `GiveawaysViewModel.kt` from `origin/dev`. Found the init collector is still active and still emits SUCCESS on every Room invalidation. Both #75 and #77 still describe live, post-#86 bugs:
- #75: a failed `reloadGiveaways()` sets ERROR via `_uiState.update {}`, but any subsequent Room invalidation flips it back to SUCCESS via the init collector.
- #77: `reloadGiveaways()` has no SUCCESS emission of its own — relies entirely on Room invalidation propagating from the refresh write. If the refresh succeeds but Room doesn't invalidate (or the write is silently a no-op), the UI gets stuck on LOADING.

Both bugs share the same root cause and the same architectural fix (combine a "refresh outcome" signal into the source-of-truth flow). Bundled into one implementer agent / one PR closing both — deviation from "one PR per issue" but justified by file-conflict and same-fix shape.

## Issues

### #75 + #77 → PR #89 — `fix(#75, #77): preserve refresh ERROR across Room invalidations`
- Branch: `wave/2026-05-02-bug-hunt/issue-75-77-giveaways-refresh-outcome`
- Files (2):
  - `feature/giveaways/.../GiveawaysViewModel.kt` — sealed `RefreshOutcome { Idle, Error }`, `MutableStateFlow<RefreshOutcome>(Idle)`, `combine(giveawaysFlow, refreshOutcomeFlow)` in source-of-truth init collector. Mapper produces `ERROR` when outcome is `Error`, else `SUCCESS`. `reloadGiveaways()` order: reset to `Idle` → `LOADING` → refresh; `.catch` sets `Error`.
  - `feature/giveaways/.../GiveawaysViewModelTest.kt` — 3 new tests:
    - `failed reload sets ERROR`
    - `ERROR from failed reload survives subsequent Room invalidation`
    - `successful reload after a failure flips ERROR back to SUCCESS`
- Tests: 3 added. New tests use `MutableSharedFlow` for hot Room sources per L-2026-05-02-05.
- Surprises:
  - **Ordering inside `reloadGiveaways()` is load-bearing.** Setting `refreshOutcomeFlow = Idle` *before* `_uiState.update { LOADING }` is intentional. If outcome was previously `Error`, the `Idle` write triggers `combine` to re-emit SUCCESS first; then the LOADING update overwrites it. With the opposite order, the LOADING update is itself overwritten by combine's SUCCESS emission. **Worth a code-comment-as-doc** if a future reader is tempted to reorder.
  - The pre-existing terminal `.catch` on the source-of-truth flow is kept — it still handles synchronous throws from `observeGiveaways()` construction (per L-2026-04-30-05). Refresh failures now flow through `refreshOutcomeFlow` instead of that catch.
  - Predictive issue bodies turned out wrong: `#72`'s author said #75 would "close as a consequence." It didn't, because the init collector remained alive in the merged version. **Heuristic:** when an issue body claims "this auto-resolves once #N merges," verify against the post-merge file before closing — don't trust the prediction.

### #76 → PR #88 — `fix(#76): restore SearchParameters data-class equals; fix StateFlow conflation in SearchViewModel`
- Branch: `wave/2026-05-02-bug-hunt/issue-76-search-parameters-equals`
- Files (2):
  - `domain/.../Search.kt` — removed `override fun equals(other: Any?): Boolean = false` and its KDoc.
  - `feature/search/.../SearchViewModel.kt` — `MutableStateFlow<SearchParameters?>(null)` → `MutableSharedFlow<SearchParameters?>(replay = 1, onBufferOverflow = DROP_OLDEST)`. Imports added: `BufferOverflow`, `MutableSharedFlow`.
- Tests: none modified. Existing `SearchViewModelTest` passes — `flatMapLatest` still triggers on each emission since SharedFlow does not conflate by structural equality. `_resultState` is still independently initialized to `SearchData.Empty`.
- Surprises:
  - **Initial state semantics differ slightly.** Old: `MutableStateFlow(null)` emits `null` on collection start. New: `MutableSharedFlow(replay = 1)` has empty replay until first emit. Acceptable here because `_resultState` is independent — the upstream chain just produces zero emissions until the user searches, which is fine. Reviewer should still confirm there's no startup code path that depended on the synthetic `null` first emission.
  - Did NOT add `@Immutable` to `SearchParameters` — all fields are scalar/nullable primitives + enum, Compose stability inference handles it. Optional belt-and-braces; matched planner judgement.
  - Verified other consumers (`GamesRepository`, `DealsRepository`, `DealsMediator`, `CheapsharkSourceImpl`, `SearchMappers`) only use `SearchParameters.asMap()` — none relied on the broken `equals == false` semantics.
  - Planner correctly avoided the L-2026-05-02-03 trap: SearchParameters has no List fields, so no ImmutableList migration, no cross-module Gradle dep changes. (Past planner under-reports were on List-retyping issues; this one is pure-scalar.)

## Sanity-check results

| PR  | state | base  | head                                            | commits ahead of dev | mergeable | closes      |
|-----|-------|-------|-------------------------------------------------|----------------------|-----------|-------------|
| #88 | OPEN  | `dev` | issue-76-search-parameters-equals               | 1                    | yes       | #76         |
| #89 | OPEN  | `dev` | issue-75-77-giveaways-refresh-outcome           | 1                    | yes       | #75, #77    |

Pass: `done` × 2 (3 issues addressed).

## Cwd-shift mitigation

Wave 2 used `git -C /Users/bam/REPO/PRIVATE/game-deals-android-app …` for all orchestrator git commands. No cwd drift observed.

## Worktree gotchas (carry-over from wave 1)

Both wave-2 agents needed `local.properties` in the worktree root to satisfy Gradle's `sdk.dir` resolution. Same observation as wave-1's #74 implementer. **Pattern:** future implementer-agent prompts should pre-instruct `cp ../../local.properties .` (or equivalent) on worktree entry to skip this discovery step. (Tracked via wave-1 `lessons.md` campaign note; not yet promoted.)
