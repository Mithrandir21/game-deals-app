# Wave 2 — campaign 2026-05-02-bug-hunt-3

**Date:** 2026-05-03
**Labels:** `bug-hunt`
**Attempted:** 2 · **Succeeded:** 2 · **Failed:** 0

Wave 2 cleans up the two issues deferred from wave 1 (#99 conflicted on `HomeViewModel.kt` with the now-merged #105; #101 was held back by the wave cap).

Per L-2026-05-02-10, both findings were re-verified against `origin/dev` HEAD by planner agents before dispatch. Both antipatterns were still real.

## Issues

### #99 [severity:medium] — HomeViewModel.loadTopStoreDataFlow orphans viewModelScope.async children on cancel
- **PR:** [#107](https://github.com/Mithrandir21/game-deals-android-app/pull/107) — `fix(#99): wrap loadTopStoreDataFlow async fan-out in coroutineScope`
- **Branch:** `wave/2026-05-02-bug-hunt-3/issue-99-loadtopstoredataflow-orphan-async`
- **Fix:** Replaced `viewModelScope.async { ... }` inside `.map { ... }` with `coroutineScope { async { ... } }`. Added `kotlinx.coroutines.coroutineScope` import.
- **Tests added:** `loadTopStoresDeals cancellation propagates to in-flight per-store deal fetches` — uses `CompletableDeferred` + `AtomicInteger` increment/decrement in try/finally to prove the per-store fetches unwind through `finally` when the parent flow is cancelled. Agent verified the test FAILS against the buggy `viewModelScope.async` shape and PASSES against the fix — discriminating coverage, not a vacuous green.
- **Verification:** `:feature:home:testDebugUnitTest` — 8/8 pass.
- **Surprises:** None. The pre-existing `loadTopStoresDeals cancels prior collector before relaunching` test covers a different concern (upstream subscription churn); this new test covers the structured-concurrency contract on the fan-out itself.

### #101 [severity:low] — WebView loading spinner can wedge when no onPageFinished fires
- **PR:** [#108](https://github.com/Mithrandir21/game-deals-android-app/pull/108) — `fix(#101): clear WebView loading on main-frame errors`
- **Branch:** `wave/2026-05-02-bug-hunt-3/issue-101-webview-spinner-wedge`
- **Fix:** Added `onReceivedError` and `onReceivedHttpError` overrides on the inline `WebViewClient`. Each clears `loading = false` only when `request?.isForMainFrame == true` (subframe failures don't kill the spinner).
- **Tests added (3):** `webView_clearsLoadingOnMainFrameReceivedError`, `webView_clearsLoadingOnMainFrameReceivedHttpError`, `webView_keepsLoadingOnSubFrameReceivedError`. All three drive the `WebViewClient` callbacks via an Espresso `ViewAction` that pulls `webView.webViewClient` from the hosted view and invokes the override on the main thread. Spinner state asserted via `SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)`. No `@VisibleForTesting` seam in production (per `feedback_no_test_only_production_seams.md`).

#### Scope expansion (worth flagging to reviewer)
The agent modified `feature/webview/build.gradle.kts` — outside the planner's declared file set — to add `debugImplementation(libs.androidx.compose.test)`. Justification: `feature:webview` does not apply the `gamedeals.android.feature` convention plugin (no Hilt/Paging/Coil), so it does not inherit the `ui-test-manifest` debug dep. Without it, `createComposeRule()` has no `ComponentActivity` declared in the test manifest and **all** WebView Compose tests — including the two pre-existing tests added in commit `57c876a` — cannot run. So the dep wasn't strictly added "for the new tests" — it was added to make the existing tests work too. Reasonable, but reviewer should confirm this is the right module-build-script pattern (sibling features inherit it via the convention plugin; webview is the lone holdout that doesn't).

#### Pre-existing instrumentation issue (NOT caused by this PR)
The agent could not get `:feature:webview:connectedDebugAndroidTest` to pass on the connected Pixel 5 / Android 14 device. All 5 WebView tests (3 new + 2 pre-existing) fail with `IllegalStateException: No compose hierarchies found in the app`. Logcat shows the root cause: Android 14 raises a `DeprecatedTargetSdkVersionDialog` for the test apk (the test apk inherits `targetSdk = 26` per `AndroidCommon.kt`) and on a fresh-install run that dialog captures focus before the test rule's `ComponentActivity` is displayed. `:feature:search` instrumentation tests have the same dialog but happen to run long enough that it dismisses naturally. This is environmental and pre-existing. Compilation, lint, and assemble are all green.

## Conflicts deferred from this wave

None. This finishes the campaign's working set.

## Sanity-check results

| PR | State | Base | Commits ahead of dev |
|---|---|---|---|
| #107 | OPEN | dev | 1 |
| #108 | OPEN | dev | 1 |

All checks pass. Parent working tree is clean — no stray edits from either agent (the explicit pwd-confirmation reminder added to wave-2 prompts after the wave-1 incident appears to have helped).

## Notes for reviewer

- **#108 build script change:** the most reviewable line in this PR isn't in WebView.kt — it's `debugImplementation(libs.androidx.compose.test)` in `feature/webview/build.gradle.kts`. Decide whether `feature:webview` should adopt the `gamedeals.android.feature` convention plugin (and inherit this dep + others) or keep the explicit one-line addition.
- **#108 instrumentation test runs:** if you have a device that doesn't show the deprecation dialog (or you bump `targetSdk`), the new tests should run. Otherwise unit-level coverage is the closest backstop, and this module has no JVM unit-test source set today.
- **Campaign close-out:** after merging both PRs, re-invoke `/github-issue-waves` to mark the campaign `complete` and write `summary.md`. Step 1 will return zero open `bug-hunt` issues, triggering Step 8's wrap-up branch.
