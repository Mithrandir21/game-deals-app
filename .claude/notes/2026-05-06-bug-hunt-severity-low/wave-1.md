# Wave 1 — campaign 2026-05-06-bug-hunt-severity-low

**Issues attempted:** 6 · **Succeeded:** 6 · **Failed:** 0 · **Shape:** batched-PR (single branch, 6 commits, 1 PR)

User explicitly requested a batched-PR shape rather than the standard PR-per-issue. All six low-severity findings from this morning's bug hunt fixed in commit-per-issue order on `bug-hunt/2026-05-06-severity-low-batch`. PR [#134](https://github.com/Mithrandir21/game-deals-android-app/pull/134) closes all 6 issues on merge.

## Per-commit log (oldest → newest)

| SHA | Issue | Title |
|---|---|---|
| `c8d6f20` | #128 | `fix(#128): annotate KeyValueBackend.writeString as @WorkerThread` |
| `414cc20` | #125 | `fix(#125): memoize TopAppBar pinnedScrollBehavior` |
| `f27d522` | #126 | `fix(#126): construct NSDateFormatter per-call to avoid shared mutable state` |
| `1caf566` | #129 | `fix(#129): switch searchParametersFlow to MutableStateFlow with atomic update` |
| `e1488e1` | #127 | `fix(#127): document LoggerImpl listener-registration contract in KDoc` |
| `d400f5f` | #130 | `fix(#130): re-key Filters rememberSaveable on existingSearchParameters` |

Net diff: +49 / −24 lines across 8 files. Each commit compiles independently.

## Worker findings worth surfacing

### 1. Issue #125's recommended fix didn't compile — actual cause was different

The bug-hunt finding for #125 said: "wrap `TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())` in `remember(topAppBarState)` to avoid per-recomposition allocation." But `TopAppBarDefaults.pinnedScrollBehavior` is itself `@Composable`, so wrapping it in `remember { … }` fails with "Composable invocations can only happen from the context of a Composable function."

Worker investigated Material3 source and found the actual cause: `pinnedScrollBehavior` already does `remember(state, canScroll) { PinnedScrollBehavior(...) }` internally. The cause of per-recomposition allocation is that the **default `canScroll = { true }` parameter is a fresh lambda each recomposition**, which invalidates the internal `remember` key.

Real fix: hoist `canScroll` into a stable `remember { { true } }` lambda (or pass `canScroll = remember { { true } }` inline). This achieves the goal — single allocation across recompositions — via a different mechanism than the issue body suggested. Documented in the commit body. **See promotion candidate.**

This is the most interesting finding of the wave. The original bug-hunt finding was directionally correct (allocation is wasteful) but misidentified the mechanism.

### 2. Issue #128's @WorkerThread annotation lives on the Android impl, not commonMain

`androidx.annotation` isn't on `:common`'s commonMain classpath today (no version-catalog entry; commonMain only carries kotlinx libs). Worker annotated only the Android impl (`SharedPreferencesBackend.kt`). This is the correct scope anyway — the Main-thread hazard is specifically about `SharedPreferences.commit()` on Android; iOS NSUserDefaults doesn't share that contract.

Could later be widened to commonMain if `androidx.annotation:annotation` (KMP) is added to the catalog, but that's over-investment for this issue.

### 3. Issue #129's StateFlow conflation changes a downstream semantic

With the prior `MutableSharedFlow` + `BufferOverflow.DROP_OLDEST`, re-emitting the same `SearchParameters` would still fire `flatMapLatest` and restart the query (re-tap-search behavior). With `MutableStateFlow`, equal values are conflated. Today's single call site only triggers on actual param changes, so this is a net positive — but a future "re-tap to retry the same search" UX would not work without an extra trigger nudge. Flagged in the commit body and the PR description.

### 4. Issue #127's KDoc-only fix vs atomicfu

Per playbook guidance, picked option 2 (KDoc) over option 1 (atomicfu copy-on-write). atomicfu isn't in `gradle/libs.versions.toml`; adding a build dependency for a Low/Low-confidence latent issue with no current race is over-investment. The KDoc explicitly states: "listener registration is NOT thread-safe; callers must register during DI bootstrap before any consumer calls log()."

When real Sentry on iOS lands and adds a runtime listener (anticipated by commit `42c57f4`), the contract will be visible in the IDE — at that point the maintainer can decide whether to take the atomicfu fix.

### 5. Issue #130's UX trade-off

Re-keying `rememberSaveable` slots changes the behavior from "sheet slot persists across show→hide→show" to "slot resets when parent params change." Since the parent doesn't update params while the sheet is closed today, visible behavior is identical. But if the project later adds a "Reset filters" or "Apply preset" affordance while the sheet is closed, the new behavior is the correct one. Flagged in commit body.

## Conflicts deferred from this wave

None. Single-batch shape; all 6 issues handled in one branch.

## Sanity-check results

- Branch present on origin: ✅ (`bug-hunt/2026-05-06-severity-low-batch`)
- Commits ahead of `origin/dev`: 6
- PR #134: state=OPEN, base=`dev`, head matches, title matches plural-fix convention
- All 6 `Closes #NNN` lines present in PR body: ✅
- Build verification (per worker report): all touched modules compile; existing `:feature:search:testDebugUnitTest` still passes (4/4)

## Notes for reviewer

- **#125 deserves a careful read.** The fix mechanism differs from what the issue body proposed (different cause, different fix). The PR body's commit message explains.
- **#129 changes a Flow semantic** that's a net positive today but a future-UX consideration.
- **#130's UX trade-off** is real but invisible today.
- **#127 and #130 were judgment calls** between option 1 (more invasive fix) and option 2 (lighter-touch). Picked option 2 for #127 (atomicfu unavailable in catalog) and option 1 for #130 (user explicitly authorized "fix all").

## Operational notes

- **Sub-agent failure rate:** 0/6 commits.
- **Sanity-check failures:** 0.
- **Re-entries:** 0 — single-shot worker dispatch.
- **JDK setup:** worker required `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` per existing reference memory; system Java 17 insufficient. Same as prior waves.
- **Shape deviation:** This is the first batched-PR shape in the campaign history (`/github-issue-waves` defaults to PR-per-issue). Worth considering whether this shape should be a first-class option in the skill for "fix all the latent foot-guns at once" sweeps.
