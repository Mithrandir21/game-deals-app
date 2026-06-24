# GitHub Sync Plan — 2026-05-06

Source report: `.claude/notes/bug-hunt-2026-05-06/android-bug-hunt-report.md`
Merge target verified against: `origin/dev` at `9783972`
Existing issues fetched: 0 open, 38 closed (label `bug-hunt`)

## Summary

- Findings: 10
- Already fixed on merge target (will be skipped): 0
- Auto-skip (open dup): 0 — no open `bug-hunt` issues exist
- Regression candidates (closed match): 1 — BUG-008
- Ambiguous (need a call): 2 — BUG-003, BUG-007
- New (will be filed on approval): 7
- File-missing on merge target: 0
- Parse warnings: 0
- Label warnings: 0

All 10 findings' Evidence signature lines were located on `origin/dev` at approximately the same line numbers cited — every finding is still real on the merge target.

---

## Already fixed on merge target (will be skipped)

*(none)*

## File missing on merge target (need a call)

*(none)*

## Auto-skipped (open duplicates)

*(none — there are no open `bug-hunt` issues; the previous 38 are all closed COMPLETED)*

## Regression candidates (closed match — confirm before reopen)

### BUG-008 — Room `setQueryCallback` allocates per-query strings unconditionally in release → closed **#92** (`COMPLETED`, closed 2026-05-02)

- **Score**: title-tokens "Room", "setQueryCallback", "release" (+2); area:storage match (+1); severity:low match (+1); no path overlap because the file was moved during the KMP migration from `domain/src/main/java/.../DomainModule.kt` (the path #92 cited) to `domain/src/androidMain/kotlin/.../DomainAndroidModule.kt` (this finding's path).
- **Diagnosis**: this is the **same antipattern**. #92's recommended fix was to gate the `setQueryCallback` registration on `RemoteBuildType.DEBUG`. The current `origin/dev:domain/src/androidMain/kotlin/pm/bam/gamedeals/domain/di/DomainAndroidModule.kt:19-22` registers the callback unconditionally — the gate either never landed, or landed in the pre-KMP file and was dropped during the rewrite.
- **Why surfaced**: high token overlap with #92's title; identical concept; file rename during KMP migration would explain the lost fix.
- **Suggested action**: comment on closed #92 with the new evidence ("rediscovered post-KMP-migration; fix didn't survive the file move"), OR file new with body prefix `> Possible regression of #92.`
- **Default if you don't override below: comment-only on #92** (no new issue)

## Ambiguous (need a call)

### BUG-003 — `StoreViewModel.deals` has no `.catch`; one refresh failure permanently empties the StateFlow

- **Candidate**: closed **#46** "StoreViewModel `.catch` after `.cachedIn` swallows Paging construction errors" (`COMPLETED`)
  - **Score**: full path match `feature/store/.../StoreViewModel.kt` (+3); title token "StoreViewModel" + "catch" (+2); area + severity matches (+2). Total ≈ 7 — *strong* by metric, but **semantically different**: #46 was about a `.catch` placed *after* `.cachedIn` on the Paging flow (`pagedDeals`), this finding is about the *absence* of `.catch` on a different StateFlow (`deals`) in the same ViewModel. Two different chains in the same file.
- **Why surfaced**: same file, related operator. The metric-strong match is a false positive of the path+title weighting — the ViewModel has multiple Flow chains and #46 dealt with a different one.
- **Suggested action**: file new (this is a distinct bug in a different chain); reference #46 in the body for context.
- **Default if you don't override below: file new**

### BUG-007 — `KeyValueBackend.commit()` thread contract carried only in a code comment

- **Candidate**: closed **#42** "Storage interface is non-suspending and uses `SharedPreferences.commit()`" (`COMPLETED`)
  - **Score**: title-tokens "SharedPreferences", "commit", "Storage" (+2); area:storage (+1); no path overlap (#42 was about the `Storage` interface; this finding is about the `SharedPreferencesBackend` impl). Total ≈ 3.
- **Why surfaced**: token overlap; #42's fix made `Storage` suspending and the only caller of the backend (`StorageImpl`) wraps in `withContext(ioDispatcher)`. This finding is the *next layer down* — the backend itself still uses `commit()` and relies on a code comment to communicate the off-thread contract.
- **Suggested action**: file new — distinct concern from #42, lower severity.
- **Default if you don't override below: file new**

## New (will be filed on approval)

### BUG-001 — `reloadGiveaways` swallows `CancellationException` via bare `catch (_: Throwable)`

- **Severity:** High · **Effort:** Trivial · **Confidence:** High
- **Labels:** `bug`, `bug-hunt`, `severity:high`, `effort:trivial`, `area:coroutines`
- **Tagline:** Direct violation of L-2026-05-02-04 in a third file (after the same fix was applied in `DealDetailsController` per #71 and `DealsMediator` per #31). Trivial fix.

### BUG-002 — Lazy Koin first-access opens SQLite on Main during first composition

- **Severity:** Medium · **Effort:** Small · **Confidence:** Medium
- **Labels:** `bug`, `bug-hunt`, `severity:medium`, `effort:small`, `area:storage`
- **Tagline:** `koinViewModel()` resolution from `HomeScreen` triggers Room `.build()` on Main; cold-start ANR risk on schema bumps. Verify with StrictMode in debug before sizing the fix.

### BUG-003 — `StoreViewModel.deals` has no `.catch`; one refresh failure permanently empties the StateFlow

- **Severity:** Medium · **Effort:** Trivial · **Confidence:** Medium
- **Labels:** `bug`, `bug-hunt`, `severity:medium`, `effort:trivial`, `area:coroutines`
- **Tagline:** Asymmetric with the sibling `uiState` chain that does catch. Body should reference #46 for context (different chain in same VM).

### BUG-004 — `TopAppBarDefaults.pinnedScrollBehavior(...)` reallocated on every recomposition

- **Severity:** Low · **Effort:** Trivial · **Confidence:** Medium
- **Labels:** `bug`, `bug-hunt`, `severity:low`, `effort:trivial`, `area:compose`
- **Tagline:** Two locations — `GameScreen.kt:301`, `StoreScreen.kt:219`. Inner state is preserved; outer wrapper reallocated each frame. Latent until behavior is swapped.

### BUG-005 — Shared `NSDateFormatter` at module scope on iOS — safe today, foot-gun if reconfigured

- **Severity:** Low · **Effort:** Trivial · **Confidence:** Low
- **Labels:** `bug`, `bug-hunt`, `severity:low`, `effort:trivial` (no specific area:* — KMP/iOS native; consider creating `area:kmp` if more iOS findings accrue)
- **Tagline:** Safe per Apple's documented thread-safety contract while configuration is immutable; flagged because the pattern is famously hazardous and the codebase will eventually want runtime locale switching.

### BUG-006 — `LoggerImpl.loggers` is an unsynchronized `MutableSet` with public `add/remove` mutators

- **Severity:** Low · **Effort:** Trivial · **Confidence:** Low
- **Labels:** `bug`, `bug-hunt`, `severity:low`, `effort:trivial` (no area:logging label exists; will omit area:* — consider creating `area:logging`)
- **Tagline:** Latent today (no callers mutate the set), but commit `42c57f4` explicitly anticipates a future iOS Sentry add — at that point this activates.

### BUG-007 — `KeyValueBackend.commit()` thread contract carried only in a code comment

- **Severity:** Low · **Effort:** Trivial · **Confidence:** Low
- **Labels:** `bug`, `bug-hunt`, `severity:low`, `effort:trivial`, `area:storage`
- **Tagline:** Annotate `@WorkerThread` on the backend interface so Lint flags any future Main caller. Body should reference #42 (Storage made suspending) for context.

### BUG-009 — `SearchViewModel.searchGames` per-field merge against `replayCache` is racy by design

- **Severity:** Low · **Effort:** Trivial · **Confidence:** Low
- **Labels:** `bug`, `bug-hunt`, `severity:low`, `effort:trivial`, `area:coroutines`
- **Tagline:** Latent — the single existing call site supplies all parameters; race only manifests if a future caller forwards partial fields. Recommended fix: switch to `MutableStateFlow<SearchParameters>` + `update {}` per L-2026-05-02-01.

### BUG-010 — `Filters` `rememberSaveable` initial-value not re-keyed on parent change

- **Severity:** Low · **Effort:** Small · **Confidence:** Low
- **Labels:** `bug`, `bug-hunt`, `severity:low`, `effort:small`, `area:compose`
- **Tagline:** Latent — current behavior may be intentional UX (slot persists across show→hide→show). Flagged for product input on whether to add a Reset/Apply-preset affordance later.

---

## Parse / label warnings

*(none — all findings parsed cleanly; all required labels exist; no severity downgrades)*

---

## How to override

Edit this file, then re-invoke the skill (Step 5 reads this file fresh):

- To **skip** an item, move it to a `## User-skipped` section (any heading containing "skip" works).
- To **promote** the regression candidate (BUG-008 → comment on #92) to a new issue instead, move it under `## New` and optionally add `prefix-with-regression-note: yes` on the next line.
- To **reclassify** an ambiguous to skip, move it under `## User-skipped` with a one-line reason.
- Any item left in its original section is treated per its default action above.

## Lessons consultation (per Step 5)

Scanned `.claude/lessons.md` Active section against each finding's tags. **No active lesson contradicts any of the recommended fixes.** Several fixes explicitly mirror existing lessons:

- BUG-001 fix mirrors L-2026-05-02-04 exactly.
- BUG-008 fix mirrors L-2026-05-01-08 (`ApplicationInfo.FLAG_DEBUGGABLE`).
- BUG-009 fix mirrors L-2026-05-02-01 (`MutableStateFlow.update`).

These references will be inlined as `> Note:` blockquotes in the issue bodies.
