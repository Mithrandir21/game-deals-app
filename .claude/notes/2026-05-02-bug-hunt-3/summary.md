# Campaign summary — 2026-05-02-bug-hunt-3

**Status:** complete · **Started:** 2026-05-02 · **Completed:** 2026-05-03
**Labels:** `bug-hunt`
**Waves:** 2 · **Issues attempted:** 6 · **Succeeded:** 6 · **Failed:** 0 · **Success rate:** 100%

All six open `bug-hunt` issues were resolved across two waves. No failures, no manual carry-overs, no unresolved findings.

## All PRs (merged)

| Issue | Severity | PR | Title |
|---|---|---|---|
| #96  | high   | [#105](https://github.com/Mithrandir21/game-deals-android-app/pull/105) | `fix(#96): handle missing release game via terminal .catch + null-as-not-found` |
| #97  | high   | [#106](https://github.com/Mithrandir21/game-deals-android-app/pull/106) | `fix(#97): move .catch inside flatMapLatest/combine to keep collector alive` |
| #98  | medium | [#103](https://github.com/Mithrandir21/game-deals-android-app/pull/103) | `fix(#98): annotate Giveaway @Immutable to match sibling domain models` |
| #99  | medium | [#107](https://github.com/Mithrandir21/game-deals-android-app/pull/107) | `fix(#99): wrap loadTopStoreDataFlow async fan-out in coroutineScope` |
| #100 | medium | [#104](https://github.com/Mithrandir21/game-deals-android-app/pull/104) | `fix(#100): wrap StoreScreen onBack in rememberUpdatedState` |
| #101 | low    | [#108](https://github.com/Mithrandir21/game-deals-android-app/pull/108) | `fix(#101): clear WebView loading on main-frame errors` |

## Wave breakdown

### Wave 1 (2026-05-02) — 4 issues
- **#96, #97, #98, #100** — both severity-high issues prioritized; the two medium-severity items rounded out the wave cap (4).
- Conflict deferral: **#99** held for wave 2 because it shares `HomeViewModel.kt` with #96 (different functions, but same file at planner level).
- Cap deferral: **#101** held for wave 2 — no file conflict, just the wave cap.
- Surprise: the wave 1 worker for #96 mis-edited the parent repo path before reverting and re-applying inside the worktree. Self-recovered, but motivated explicit pwd-confirmation reminders in wave 2 prompts.

See [`wave-1.md`](./wave-1.md).

### Wave 2 (2026-05-03) — 2 issues
- **#99, #101** — the two deferred issues. Per L-2026-05-02-10, both findings were re-verified against `origin/dev` HEAD before dispatch (the wave 1 #105 had merged in between, modifying the same `HomeViewModel.kt` that #99 targets, but a different function).
- Both PRs landed cleanly. Wave 2's pwd-confirmation reminder appears to have worked — neither agent left stray edits in the parent repo.
- Surprises: #108 expanded scope to add `feature/webview/build.gradle.kts` (the `ui-test-manifest` dep) so existing Compose tests in that module could run at all. Pre-existing instrumentation issue (Android 14 deprecation dialog vs `targetSdk=26`) blocked `connectedDebugAndroidTest`; environmental, not a regression.

See [`wave-2.md`](./wave-2.md).

## Lessons promoted to project-wide

- **L-2026-05-03-01** — Module that opts out of the convention plugin silently loses inherited test-runtime deps. Discovered when `:feature:webview:connectedDebugAndroidTest` failed with `No compose hierarchies found` due to a missing `ui-test-manifest` dep. Drafted from #101 / PR #108.

## Lessons kept campaign-only

See [`lessons.md`](./lessons.md):
- Wave 1, #97 — routing inner-flow throws through an existing trigger sentinel (Giveaways)
- Wave 1, #98 — intra-file `@Immutable` inconsistency as an audit signal
- Wave 1, #96 — worker agent mis-edit of parent repo from inside a worktree (mitigated in wave 2)
- Wave 2, #99 — `CompletableDeferred` + `AtomicInteger` in try/finally as a structured-cancellation regression test pattern
- Wave 2, #101 — the convention-plugin / test-infra drift observation (the promoted L-2026-05-03-01 is the generalized version)

## Operational notes

- **Sub-agent failure rate:** 0/6.
- **Sanity-check failures:** 0. All 6 PRs verified open against `dev` with ≥1 commit ahead at sanity-check time; all 6 ultimately merged.
- **Re-entries:** 2 (one between waves, one for wrap-up).
- **Parallel concurrency:** wave 1 ran 4 worktree agents concurrently; wave 2 ran 2.
- **Drift between filing and execution:** PR #105 (closes #96) modified `HomeViewModel.kt` between wave-1 dispatch of #96 and wave-2 dispatch of #99. The L-2026-05-02-10 re-verification step caught this as a non-issue (different function), but the discipline mattered.
