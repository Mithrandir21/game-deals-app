# Campaign lessons — 2026-05-01-bug-hunt

## Campaign lessons

- (wave 4) **Stale-base guard worked.** Wave-3's lesson was applied: orchestrator ran `git fetch origin dev` immediately before dispatching wave 4, and the per-agent prompt named the expected base SHA (`2a9e5fe`). Result: PR #70's diff sits cleanly on current dev with no replayed/reverted prior-PR hunks. The fix is simple and reproducible — every wave dispatch should re-fetch first.
- (wave 4) **Inline-comment prompt addition is holding.** Two consecutive waves (3 and 4) of agents respected the no-comments rule after the orchestrator prompt was updated to cite AGENTS.md explicitly. Spot-check via `grep -E '^\+.*//' | grep -iE '#NN|stability|<rationale-keyword>'` returned zero hits across 4 PRs (#67, #68, #69, #70). Promote the prompt phrasing as the default.
- (wave 4) **`.claude/CLAUDE.md` imports a non-existent `@AGENTS.md`.** The agent flagged this; confirmed via `find . -iname AGENTS.md` (zero matches). Project conventions have been propagating via `.claude/lessons.md`, per-prompt instructions, and the user's auto-memory — *not* via the project-instructions channel. Worth restoring AGENTS.md or fixing the import. Not a campaign blocker but a real finding for the user.

- (wave 3) **Stale-base regression in agent worktrees.** Wave-3 agents forked from a stale local `origin/dev` because the orchestrator (me) didn't `git fetch origin dev` between observing wave-2 PRs merge and dispatching wave-3 agents. Result: PR #68 and PR #69's diffs against current dev showed reverts of #66's `asStateFlow()`/size==1 changes alongside the agents' real contributions. Resolution: rebased both branches post-hoc — #68 was clean, #69 had a single import-block conflict. **Orchestrator fix:** must `git fetch origin <base>` *immediately before* dispatching each wave, and it's worth adding a one-line check that the worktree's parent commit matches `origin/<base>` before handing off to the agent. This is the strongest orchestrator-level lesson of the campaign.
- (wave 3) **Planner can decline an issue as already-fixed.** #32 was opened during the bug-hunt sweep before PR #50 (typed Compose Navigation) landed — that PR's `SavedStateHandle` seeding eliminated the bug class entirely. The planner verified this by grepping for the named APIs (`setStoreId`, `loadGameDetails`, `LaunchedEffect(storeId)`) and reporting all gone. The orchestrator closed the issue without dispatching. **Skill enhancement:** when planner returns `approach: "already fixed by PR #N, close as duplicate"`, the orchestrator should short-circuit dispatch and write `status: closed_already_fixed`. Saves a wave slot and produces a cleaner audit trail than "PR opened that does nothing."
- (wave 3) **Sub-agent prompt + AGENTS.md citation suppressed the comment-spam regression.** Wave-2 PR #66's agent had inserted ~28 lines of `// per #37` style comments at every assertion site (cleaned up post-merge). Wave-3 prompts explicitly cited the AGENTS.md no-comments rule. Spot-check of #67/#68/#69: zero inline comments referencing issue numbers. Worth keeping the prompt addition permanently — a small phrase ("Do NOT add comments referencing the issue number; the *why* belongs in the PR body, not the source") plus the AGENTS.md citation appears to be sufficient.

- (wave 2) **Existing ViewModel tests positively asserted the buggy emission shape.** Three of six VM test files (`HomeViewModelTest`, `GiveawaysViewModelTest`, `StoreViewModelTest`) were checking `size == 2, [initialValue, currentValue]` because under `UnconfinedTestDispatcher` the spurious initial-flash from the `stateIn(WhileSubscribed, initial)` wrapper was the *only* thing producing a second emission. Removing the wrapper exposed that the tests were anchoring on the bug. Lesson: when fixing a state-emission shape across many files, expect the existing tests to be calibrated to the bug, not to the correct behavior. Update the assertions and add an inline comment referencing the issue so regressions don't slip back in.
- (wave 2) **Worktree env quirk is now reproducible (6/7 agents across two waves).** Every agent has had to copy `local.properties` from the main checkout and override `JAVA_HOME` to Android Studio's bundled JBR 21 (system default Java 17 fails on `:build-logic:convention`). This is no longer "watch for it"; this is "fix it once at the orchestrator level." Candidate orchestrator improvement: pre-seed `local.properties` in the worktree and either set `org.gradle.java.home` or document the JAVA_HOME requirement upfront in the agent prompt.
- (wave 2) **Issue #34 incidentally closes #40.** Confirmed the planning-time prediction that #34's recommended fix (remove `loading = true` from `update`) entirely subsumes #40's recommended fix. PR body flagged this so the reviewer can close #40 as duplicate. Orchestrator will detect the closed-no-PR state on re-entry. Lesson: when planners surface "issue X is a strict subset of issue Y", schedule Y first and explicitly flag the subsumption in Y's PR — saves a wave for X.

- (wave 1) **Worktree missing `local.properties` + non-executable `gradlew` recurred across 3/4 agents.** Each had to copy `local.properties` from the main checkout and invoke `bash gradlew`. The prior campaign saw 1 instance of related worktree friction (#47, pruning); this is a stronger signal. Candidate orchestrator improvement: pre-seed `local.properties` and ensure `gradlew` is executable when creating the worktree, or add a one-time setup step to the per-agent prompt.
- (wave 1) **Scope-expansion to `build.gradle.kts` is now precedented enough to expect.** Three of four campaigns so far (this wave: #41, #42; prior: #45) needed a `build.gradle.kts` dependency add to compile or test the fix. Worth pre-emptively allowlisting `<module>/build.gradle.kts` for the module being touched in agent prompts, while still requiring the agent to flag it in the report.
- (wave 1) **Same-fix-multiple-files pattern (#42) worked cleanly when zero production callers existed.** Agent verified non-breakage via grep before changing the interface signature, then made the suspend conversion. Lesson reinforces "verify caller surface before signature changes" — already standard practice.

## Promotion candidates (project-wide)

- ~~**`flow { emit(LOADING); doRefresh() }` vs `flow { emit(LOADING) }.onStart { doRefresh() }`.**~~
  — drafted from #36. Declined: generic Flow operator semantics; `onStart` runs *before* the
  flow body is well-documented in coroutines docs. No project-specific gotcha.

- ~~**Cancel-and-replace job pattern for retry-able coroutine launches.**~~
  — drafted from #33. Declined: standard coroutine pattern, well-covered in
  Kotlin/Android docs. No project-specific gotcha beyond what the PR encodes.

- [ ] **Belt-and-suspenders for Compose stability of screen-state containers:
  annotate with `@Immutable` *and* migrate `List<X>` → `ImmutableList<X>`.**
  Either alone works for stability inference (Compose Compiler reads the field
  type *or* honors the annotation), but doing both gives complementary
  guarantees: `ImmutableList` is the type-system promise to callers (no one can
  assign a `MutableList`), `@Immutable` is the explicit promise to the Compose
  Compiler. Use `persistentListOf()` for empty defaults and `.toImmutableList()`
  at the producer boundary. New catalog entry needed:
  `kotlinx-collections-immutable = "0.4.0"` (latest stable, verified via Maven
  Central). Each consuming module gets the dep added to `build.gradle.kts`.
  Keep nested unstable types (e.g. `Pair<...>` inside the list) as a separate
  follow-up — migrating them would cascade into the screen layer.
  — drafted from #38.

- [ ] **`.claude/CLAUDE.md` imports `@AGENTS.md` which does not exist in the
  repo.** Conventions are loading via `.claude/lessons.md` and per-prompt
  text instead. Either restore AGENTS.md or change CLAUDE.md to import the
  actual conventions file. Not a campaign-coding lesson but a project-config
  finding worth surfacing.
  — drafted from #38. (Project-config issue, not a coding pattern — likely
  declined for `.claude/lessons.md` but valuable for the user to know.)

- [x] **`AndroidView` lifecycle: hoist clients out of `factory`, wire `onRelease` for teardown.** `factory` runs once when the View first enters composition, but it doesn't survive recomposition the way you'd expect — and `update` runs every recomposition. For a `WebView`, this means a fresh `WebViewClient`/`WebChromeClient` instance per recomposition unless you `remember { }` them outside. More importantly, `AndroidView` does *not* call `onDetachedFromWindow` on the underlying view when the composable leaves composition — the view sticks around until GC, holding a process-wide WebView session, JS engine, network stack, and listeners. The fix is `AndroidView(factory = ..., update = ..., onRelease = { it.stopLoading(); it.webViewClient = ...; it.loadUrl("about:blank"); (it.parent as? ViewGroup)?.removeView(it); it.destroy() })`. The `loadUrl("about:blank")` matters — it cancels in-flight network and JS before destroy.
  — drafted from #30. **Promoted as L-2026-05-01-09.**

- [ ] **`CancellationException` must be re-thrown when caught by a generic
  `catch (e: Exception)` inside coroutine code.** Otherwise structured
  concurrency breaks: parent cancellation appears to "succeed" while the child
  silently converts the cancel into a fake `Error` value, the database/DB
  transaction completes anyway, and the logger fires `fatalThrowable` for what
  was not actually a fatal. Pattern: either add a dedicated
  `catch (e: CancellationException) { throw e }` block before the generic one,
  or call `coroutineContext.ensureActive()` at the top of the catch.
  — drafted from #31.

- [ ] **`LazyColumn.items(count)` without a `key` is wrong any time the list
  contents can reorder, filter, or shrink.** Without keys, Compose treats each
  index as a fresh slot — animations re-fire, scroll position resets after
  filtering, and `rememberSaveable` state inside the item leaks across what
  should be different items. Use a stable identity field (Room `@PrimaryKey`
  works). For sealed-list mixes where two variants might share the same numeric
  id, prefix the key with a discriminator (`"store-$id"`, `"deal-$id"`).
  Caveat: enabling keys retroactively can crash `@Preview` if the sample data
  has duplicate ids — fix the preview alongside the screen.
  — drafted from #35.

- [ ] **`SharedPreferences.commit()` and `.apply()` both block.** `apply()`
  schedules disk-write asynchronously but the in-memory mutation is
  synchronous, and Android's `StrictMode` flags both as disk-write-on-main.
  The fix isn't picking one over the other — it's running the call inside
  `withContext(ioDispatcher)` so the wider call site is suspending. Inject
  the dispatcher (`@Inject CoroutineDispatcher = Dispatchers.IO`) so tests
  can pass `StandardTestDispatcher`. Pair the change with
  `StrictMode.ThreadPolicy.detectAll().penaltyLog()` in DEBUG so future
  regressions surface in logcat.
  — drafted from #42.

- [x] **`view.context as Activity` will crash in `@Preview` and any non-Activity
  Compose host.** Use `androidx.activity.compose.LocalActivity.current`
  (Activity 1.10+, null-safe — returns null in previews and tests). If the
  surrounding module doesn't already depend on `activity-compose`, add it —
  it's a cheap dependency and unlocks the safer API everywhere.
  — drafted from #41. **Promoted as L-2026-05-01-06**, framed around the
  project-specific `:common:ui` dependency-add.

- ~~**`CancellationException` must be re-thrown when caught by a generic
  `catch (e: Exception)` inside coroutine code.**~~ — drafted from #31.
  Declined: Kotlin/coroutines best-practice already in widely-known docs;
  no project-specific gotcha.

- ~~**`LazyColumn.items(count)` without a `key` is wrong any time the list
  contents can reorder, filter, or shrink.**~~ — drafted from #35. Declined:
  generic Compose advice, well-covered in official docs.

- ~~**`SharedPreferences.commit()` and `.apply()` both block.**~~ — drafted
  from #42. Declined: standard Android knowledge, no project-specific
  pattern beyond what the PR already encodes.

- [x] **`UnconfinedTestDispatcher` + `init {}` block ⇒ a single conflated
  emission is the correct expectation for these ViewModels.** Multiple
  emissions in a `flow.toList()` test usually mean either (a) a real bug
  with a derived StateFlow / `WhileSubscribed` initial-flash, or (b) a test
  racing the dispatcher. Default to asserting `size == 1` for the steady-state
  value; only assert larger sequences when the test deliberately exercises a
  state transition (e.g. `LOADING` → `DATA`). Calibrate against the *correct*
  emission shape, not whatever the code currently produces — three out of six
  VM tests in this project were anchored to a bug for months.
  — drafted from #37. **Promoted as L-2026-05-01-07.**

- ~~**When a fix in issue Y strictly subsumes the fix in issue X, schedule
  Y first and flag the subsumption in Y's PR body.**~~ — drafted from wave 2
  #34/#40 interaction. Declined: orchestrator-level guidance for the
  github-issue-waves skill, not a project-level coding lesson. Lives in this
  campaign's notes for the next time the skill is invoked.
