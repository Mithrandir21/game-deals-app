# GitHub Sync Plan — 2026-05-14

Source report: `.claude/bug-hunt-workspace/android-bug-hunt-report.md` (copy of `/tmp/bug-hunt-game-deals/android-bug-hunt-report.md`)
Merge target verified against: `origin/dev` @ `9783ad4` (HEAD == origin/dev, clean working tree — bug hunt ran on merge target itself).
Existing issues fetched: 0 open, 47 closed (label `bug-hunt`).

## Summary
- Findings: 15
- Already fixed on merge target (will be skipped): 0
- Auto-skip (open dup): 0 (no open bug-hunt issues exist)
- Regression candidates (closed match): 4
- Ambiguous (need a call): 2
- New (will be filed on approval): 9
- File-missing on merge target: 0
- Parse warnings: 0
- Label warnings: see footer

## Already fixed on merge target (will be skipped)
*(none — all 15 findings verified present on origin/dev)*

## File missing on merge target (need a call)
*(none)*

## Auto-skipped (open duplicates)
*(none — there are zero OPEN bug-hunt issues; all 47 prior issues are closed/completed)*

## Regression candidates (closed match — confirm before action)

- **BUG-003** — `Set<Int>` favourite-ids parameter is unstable and forces over-recomposition → closed **#38** (`completed`, score 8: shared paths `HomeViewModel.kt` + `SearchScreen.kt`, area:compose, severity:medium)
  - **Reality check:** #38 fixed *screen state container* classes (`HomeScreenData`, `GiveawaysScreenData`, `SearchData.SearchResults`) by adding `@Immutable` / `ImmutableList`. This finding is about a different parameter (`favouriteIds: Set<Int>` passed to `*Content` composables) that was missed. **Same class-of-bug, distinct fix scope** — not a regression of #38's fix, just a sibling defect that the prior hunt didn't catch.
  - **Default if you don't override below: comment-only (no new issue).** Comment on #38 with the new evidence.
  - **Suggested alternative:** move to `## New` — this is a distinct fix (introduce `ImmutableSet<Int>` to the project), the prior hunt missed it, and `Set` doesn't appear anywhere in #38's body. Filing as a new issue is cleaner than reopening the conversation on a 6-month-old closed thread.

- **BUG-006** — `SharedPreferencesBackend.readString`/`contains` missing `@WorkerThread` parity → closed **#128** (`completed`, score 5: shared path `SharedPreferencesBackend.kt`, area:storage, severity:low)
  - **Reality check:** #128's recommended fix was "Annotate `KeyValueBackend` with `@WorkerThread`". The fix landed *only on the write side* (`writeString`, `remove`); the symmetric reads were missed. This is a partial-fix follow-up — the closer to a "true" regression candidate in this batch.
  - **Default if you don't override below: comment-only (no new issue).** Comment on #128 noting the asymmetric outcome and asking for the symmetric annotation.
  - **Suggested alternative:** also acceptable to file as a new issue with a `> Possible regression of #128 (closed completed). The original fix only annotated writes; reads still lack `@WorkerThread`.` prefix.

- **BUG-011** — `rememberSaveable` re-keys on rebuilt range, causing post-release slider snap → closed **#130** (`completed`, score 7: shared path `SearchScreen.kt`, title token `rememberSaveable`, area:compose, severity:low)
  - **Reality check:** **Direct regression** — #130's accepted fix was to add `existingPriceRange` as the `rememberSaveable` key (currently at `SearchScreen.kt:390`). That added key now invalidates on every `onValueChangeFinished` round-trip and causes the slider thumb to snap. The fix produced the visual glitch.
  - **Default if you don't override below: comment-only (no new issue).** Comment on #130 noting the regression and proposing either (a) drop the key, or (b) accept the snap behaviour as intentional and document.
  - **Suggested alternative:** file new with `> Possible regression of #130 (closed completed). The accepted re-keying fix is now causing observable slider snap on release.` prefix — recommended because the original issue's resolution is what introduced the new defect.

- **BUG-014** — `WebView` constructed with Activity `Context` (teardown is correct) → closed **#30** (`completed`, score 6 with module-rename fallback: original path `feature/webview/src/main/java/.../WebView.kt` renamed to `WebView.android.kt` during KMP migration; title token `WebView`, area:webview)
  - **Reality check:** #30 was "WebView never destroyed" and its accepted fix added the exact `onRelease { ... destroy() }` block I verified at `WebView.android.kt:81-88`. The fix landed. BUG-014 is a different, lower-grade concern (the Activity Context being captured by `WebView(context)` in the factory) and my own recommended fix in the report is **"None required"**.
  - **Default if you don't override below: SKIP silently (no new issue, no comment).** I recommend not filing this — the report itself says no fix is required and the related/prior fix is in place.
  - **Suggested alternative:** if you want a tracking issue for the latent concern, move under `## New` and accept the noise.

## Ambiguous (need a call)

- **BUG-004** — Snackbar "Retry" action handler calls `onBack()`, no retry function exists
  - Candidate 1: closed **#100** (score 5 with module-rename fallback: shared file `StoreScreen.kt` after `src/main/java/` → `src/commonMain/kotlin/` rename, area:compose, severity:medium). #100 was a **stale-capture** bug in the same snackbar handler. #100's accepted fix added `currentOnBack by rememberUpdatedState(onBack)`, which is exactly what the current code uses — so #100 is correctly fixed. BUG-004 is a separate semantic issue: the `actionLabel = errorRetry` says "Retry" but the handler still calls `currentOnBack()`. Different defect, same handler block. The Favourites half of the finding has no candidate at all.
  - Candidate 2: closed **#39** ("onRetry/onReload captured stale in LaunchedEffect(snackbarHostState)") — only loosely related, different files cited.
  - **Default if you don't override below: file new** (because the defect class is different: stale-capture vs wrong-handler).

- **BUG-009** — `SharingStarted.Eagerly` keeps Room observation hot for VM lifetime
  - Candidate: closed **#37** (score 4: shared path `GiveawaysViewModel.kt`, area:coroutines). #37 was about `MutableStateFlow.stateIn(WhileSubscribed)` (redundant wrapping); its fix replaced those with `asStateFlow()`. BUG-009 is a different code path in the same file (`combine(...).stateIn(viewModelScope, Eagerly, ...)` at line 65), with the **opposite** fix direction (switch `Eagerly` → `WhileSubscribed`). Different antipattern, no regression of #37's fix.
  - **Default if you don't override below: file new.**

## New (will be filed on approval)

> Note on labels: `area:*` is omitted for findings that don't map to any existing area label. Severity and effort labels are derived from each finding's report fields. Where no area label fits, no new `area:*` label is created automatically — flagged in the parse/label warnings section.

- **BUG-001** — Android `formatLocaleAwareDate` caches locale and timezone at class-load · `severity:high` · `effort:small` · *(no area)*
  - User-facing on Android today: locale/timezone changes don't take effect until process death. iOS sibling lives in closed #126 (NSDateFormatter shared instance) — cross-reference noted in body.

- **BUG-002** — `IosPlatformActions.share` uses deprecated `keyWindow` and crashes on iPad · `severity:high` · `effort:small` · *(no area)*
  - **Ship-blocker for any iOS release.** Silent failure on multi-scene iPhones + `NSInvalidArgumentException` on iPad.

- **BUG-005** — Room builder does not call `setQueryCoroutineContext` on either platform · `severity:medium` · `effort:small` · `area:coroutines`
  - Affects both `DomainAndroidModule.kt` and `DomainIosModule.kt`. Recommended Room-KMP idiom is missing.

- **BUG-007** — `Pair<Store, GameDeal>` makes wrapping list parameter unstable · `severity:low` · `effort:small` · `area:compose`
  - **Cross-references closed #80**, which incorrectly stated that `dealDetails: ImmutableList<Pair<Store, GameDetails.GameDeal>>` was "correctly typed". `kotlin.Pair` is unstable in Compose regardless of generic args — issue body to include this correction note.

- **BUG-008** — Sealed `Destination` loses Swift exhaustiveness (no SKIE) · `severity:low` · *(no effort label — report says "N/A")* · *(no area)*
  - Informational finding; latent until Swift consumers exist.

- **BUG-010** — `FavouritesRepository.toggleFavourite` performs non-atomic read-modify-write (TOCTOU) · `severity:low` · `effort:small` · *(no area — `area:database`/`area:repository` don't exist; could create one)*

- **BUG-012** — `Sentry.init` runs on Main during `Application.onCreate` (currently dormant) · `severity:low` · `effort:small` · *(no area)*
  - Dormant because `SENTRY_DSN = ""`. Worth tracking so the cold-start cost is dealt with before a DSN is wired in.

- **BUG-013** — `httpClient` default-arg on `expect` lost to Swift · `severity:low` · `effort:small` · *(no area)*

- **BUG-015** — Unmanaged `CoroutineScope` for DB warm-up · `severity:low` · `effort:trivial` · `area:coroutines`

## Parse / label warnings
- BUG-008 has no `effort:*` label (report says "N/A (informational)").
- 7 findings have no `area:*` label inferred (BUG-001 KMP/datetime, BUG-002 iOS UIKit, BUG-005 Room KMP — area:coroutines used, BUG-008 KMP nav/Swift, BUG-010 DB/repo, BUG-012 cold-start, BUG-013 KMP HTTP). Consider creating `area:kmp`, `area:database`, or `area:ios` labels if these categories will recur. For this run, those findings are filed with `bug-hunt` + severity + effort labels only.
- No severity downgrades (no `Critical` findings).
- Merge-target verification did NOT skip (origin/dev fetched cleanly; bug hunt ran on the merge target itself, so all signatures verified present in working tree == origin/dev HEAD).

---

## How to override

Edit this file, then re-invoke the skill (Step 5 reads this file fresh):

- To **skip** an item, move it under a `## User-skipped` section (any heading containing "skip" works).
- To **promote** a regression candidate to a new issue, move it under `## New` and optionally append `prefix-with-regression-note: yes` on the next line.
- To **reclassify** an ambiguous to skip, move it under `## User-skipped` with a one-line reason.
- Any item left in its original section is treated per its default action (above).
