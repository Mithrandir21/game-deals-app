# Wave 3 — 2026-05-01

## Summary

- Issues attempted: 4 (#30, #32, #33, #36)
- PRs opened: 3 (#67, #68, #69)
- Closed without PR: 1 (#32 — already fixed by an earlier campaign)
- Failures: 0
- Wave 2 PRs (#64, #65, #66) merged before this wave dispatched.

## Issues

### #30 — WebView destroyed on composable disposal ✓ (merged)
- PR: [#67](https://github.com/Mithrandir21/game-deals-android-app/pull/67) — **MERGED**
- Branch: `wave/2026-05-01-bug-hunt/issue-30-webview-destroy`
- Diff: +30 / -17 in `feature/webview/.../WebView.kt`, 1 commit
- Fix: hoisted the `WebViewClient` out of the `factory` lambda into a `remember { }` so it survives recomposition; wired up `AndroidView`'s `onRelease` callback with the standard teardown sequence — `stopLoading()`, clear the WebChromeClient/WebViewClient, `loadUrl("about:blank")`, detach from parent (`(parent as? ViewGroup)?.removeView(it)`), and `destroy()`.
- Tests: `:feature:webview:test` BUILD SUCCESSFUL (module has no JVM tests; verified via compile only).
- Scope: stayed inside cleared file set.

### #32 — ViewModel mutators called from composable body (Store/Game) ✓ (closed without PR)
- Closed manually with reason: already fixed by **PR #50** (typed Compose Navigation routes, `refactor(#23)`).
- Verification: `Grep` for `setStoreId`, `loadGameDetails`, `LaunchedEffect(storeId)`, `LaunchedEffect(gameId)` — all gone. The `storeId` / `gameId` are now seeded into the VM via `SavedStateHandle` at construction, so the composable no longer triggers the load. The bug class described in the issue body cannot reproduce against current code.
- This is a stale-issue artifact from the bug-hunt sweep that opened the issue before PR #50 landed. State.yaml status set to `closed_already_fixed`.

### #33 — `HomeViewModel.loadTopStoresDeals` no longer stacks duplicate Room collectors ✓
- PR: [#68](https://github.com/Mithrandir21/game-deals-android-app/pull/68) (open, rebased onto current dev)
- Branch: `wave/2026-05-01-bug-hunt/issue-33-homevm-job-cancellation`
- Diff: +38 / -7, 1 commit
- Fix: introduced `private var loadJob: Job?`. Restructured `init { }` to call `loadTopStoresDeals()` (single source of truth). `loadTopStoresDeals()` now cancels `loadJob` before relaunching, so each retry replaces the prior collector instead of stacking on top of it. Each new launch re-emits the `LOADING` status via `.onStart { }` for visible feedback.
- Tests added: `loadTopStoresDeals cancels prior collector before relaunching` — uses `MutableSharedFlow` with `subscriptionCount` to assert the prior collector was cancelled (subscriptionCount drops then climbs back to 1 across retry).
- Tests: `:feature:home:test` BUILD SUCCESSFUL after rebase.
- **Note (small regression in agent's choice, not the rebase):** when unifying the old `init { collect }` with the old `loadTopStoresDeals { logFlow + onStart + collect }` into one launcher, the agent kept `.onStart { }` but dropped `.logFlow(logger)`. Pre-rebase commit also lacked it — this is the agent's call, not rebase fallout. The retry path used to log flow events; it no longer does. Trivially restorable in a follow-up if you want it back.

### #36 — `GiveawaysViewModel.reloadGiveaways` emits LOADING before refresh ✓
- PR: [#69](https://github.com/Mithrandir21/game-deals-android-app/pull/69) (open, rebased onto current dev)
- Branch: `wave/2026-05-01-bug-hunt/issue-36-giveaways-loading-order`
- Diff: +20 / -3, 1 commit
- Fix: replaced `flow { emit(LOADING) }.onStart { refreshGiveaways() }` (operator-order bug — `onStart` runs *before* the flow body, so refresh blocks before LOADING reaches the UI) with `flow { emit(LOADING); refreshGiveaways() }`. Removed unused `onStart` import.
- Tests added: `reload Giveaways emits LOADING before refresh completes` — uses `CompletableDeferred` to gate `refreshGiveaways()` and asserts LOADING is observable while the refresh is still suspending.
- Tests: `:feature:giveaways:test` BUILD SUCCESSFUL after rebase.

## Sanity check (post-rebase)

| PR  | state  | base | additions | deletions | commits |
|-----|--------|------|-----------|-----------|---------|
| #67 | MERGED | dev  | 30        | 17        | 1       |
| #68 | OPEN   | dev  | 38        | 7         | 1       |
| #69 | OPEN   | dev  | 20        | 3         | 1       |

All three pass.

## Notes

- **Stale-base regression in agent worktrees.** Wave-3 agents forked from `4088c86`/`3b42ee4` (last local `origin/dev`) instead of the *remote* `origin/dev = c0123ad` post-#66. The orchestrator (me) didn't `git fetch origin dev` between observing wave-2 PRs merge and dispatching wave-3 agents. Result: PR #68 and PR #69's diffs against current dev showed reverts of #66's `asStateFlow()`/size==1 changes alongside the agents' real contributions. Resolution: rebased both branches onto current `origin/dev` post-hoc — #68 was a clean rebase (the agent's commit didn't touch #66's hunks), #69 had a single import-block conflict (`onStart` vs `stateIn`, both unused after rebase — both dropped). Strong promotion candidate for the github-issue-waves skill: orchestrator must `git fetch origin <base>` *immediately before* dispatching each wave.
- **#32 is the first closed-already-fixed issue across the campaign.** Worth recording: a planner agent verifying current code against the issue body (rather than blindly accepting the issue's claim) saved a wave slot. The campaign now has the precedent of "before dispatching, planner can decline an issue as stale". The orchestrator should normalize this — when planner returns `approach: "already fixed by PR #N, close as duplicate"`, the orchestrator closes the issue and writes `status: closed_already_fixed` rather than dispatching.
- **PR #66 cleanup happened post-merge.** Sub-agent had inserted ~28 lines of explanatory comments at every assertion site in the updated tests (referencing #37). Removed in a follow-up commit on dev. The user feedback is now memorialized in `feedback_no_inline_issue_comments.md`. Wave-3 agent prompts explicitly cited the AGENTS.md no-comments rule; spot-check of #67/#68/#69 diffs shows compliance — no inline comments referencing issue numbers.

## Campaign status

After this wave merges:
- Resolved: 12/13 (#31, #34, #35, #37, #39, #40, #41, #42 from waves 1–2; #30, #32, #33, #36 from wave 3).
- Open: 1/13 (#38).
- Wave 4 candidate: {#38} — alone. With #33 and #36 wrapping up, the previous conflict cluster on Home/Giveaways VMs is gone; #38 also touches DealBottomSheet (`common/ui`) which no other open issue claims.
