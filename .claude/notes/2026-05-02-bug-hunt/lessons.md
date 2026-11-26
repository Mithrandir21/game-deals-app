# Campaign lessons — 2026-05-02-bug-hunt

## Campaign lessons

- **(wave 1) Issue #71 — `CancellationException` rethrow:** the `DealsMediator.kt:76-81` pattern (rethrow `CancellationException`, then fatal-log everything else) is the canonical structured-concurrency discipline in this repo. When a `try { … } catch (Throwable)` block contains a suspending emit (e.g. `_state.emit(...)`), it MUST rethrow `CancellationException` first or it will swallow scope cancellation and (worse) log scope cancellation as a Crashlytics fatal.

- **(wave 1) Issue #72 — `flowOf(...)` masks Room-flow races in tests.** The original `GiveawaysViewModelTest` used `flowOf(...)` to stub `observeGiveaways()`, but `flowOf` completes after one emission — the parallel-collector race needed a *second* emission to manifest. Real Room flows never complete. **Heuristic:** when stubbing a hot upstream `Flow` whose race shape depends on second/Nth emissions, use `MutableSharedFlow` (or a custom `flow { while (true) emitAll(channel) }` shape) so the test can drive multiple emissions. `flowOf` is for terminal one-shot sources only.

- **(wave 1) Issue #73 — two valid patterns for "cancel prior load on retry".** This repo now has both in production:
  - **`loadJob?.cancel()` + new launch** — `HomeViewModel` (issue #33). Imperative; mutable `Job?` field on the VM.
  - **`reloadTrigger: MutableSharedFlow<Unit>` combined into source flow** — `GameViewModel` (issue #73). Flow-shaped; no mutable field; relies on `flatMapLatest` for cancellation.
  Both are correct. The trigger-flow pattern composes better with existing source-of-truth flows; the `loadJob` pattern is simpler when there's no existing source flow to combine into. **Reviewers may want to standardize**, but neither is wrong.

- **(wave 1) Issue #74 — Compose `LaunchedEffect` capturing a lambda from a recomposing parent must wrap it in `rememberUpdatedState`.** Otherwise the coroutine permanently holds the lambda captured at first launch, even after the parent recomposes with a closure over different state. Today's only `SingleEventEffect` call site is safe-by-accident because `goToGame` resolves to a `remember(navController)`-stable reference — but that's a fragile property of the call chain, not of the helper. Wrap callers in `rememberUpdatedState` inside the helper, not at every call site.

- **Worktree-isolated agents need `local.properties` in the worktree root.** Gradle resolves it relative to the worktree, not the main checkout. The agent for #74 created a (gitignored) `local.properties` to point at the Android SDK — worth pre-loading in the agent prompt template if Gradle is going to be invoked. Also seen: worktree `gradlew` losing its executable bit, requiring `sh ./gradlew`.

- **(wave 2) Issue #75/#77 — bundled fix.** Both issues described the same residual bug post-#86 and required the same architectural fix on the same file. Bundling into one implementer agent / one PR (with `Closes #75. Closes #77.`) is a justified deviation from the skill's "one PR per issue" pattern when (a) file-set conflict would otherwise serialize them across waves, and (b) the underlying fix is a single architectural change rather than two distinct mechanics.

- **(wave 2) Predictive issue bodies don't auto-close issues.** Issue #75's body claimed "Fix #72 first; this issue closes as a consequence" — but the post-#72 code still had the bug in a different shape. **Heuristic:** when an issue body predicts auto-resolution by another PR, verify against the post-merge file state before deciding whether to re-queue it. Don't trust the prediction; trust the code.

- **(wave 2) `combine`-with-trigger ordering matters.** In `reloadGiveaways()`, resetting `refreshOutcomeFlow = Idle` *before* `_uiState.update { LOADING }` is load-bearing. If the previous refresh set outcome to `Error`, the `Idle` write triggers `combine` to re-emit SUCCESS *first*; the LOADING update then overwrites that synchronously. The opposite order would have LOADING overwritten by combine's emission. Always reason about the order of writes that drive a `combine` upstream relative to direct `_uiState` updates downstream.

- **(wave 2) `MutableStateFlow` → `MutableSharedFlow(replay=1, onBufferOverflow=DROP_OLDEST)` is the right primitive when (a) you need replay-1 for late subscribers and (b) you need identical-equals successive emissions to *still* trigger downstream `flatMapLatest`.** `StateFlow` conflates by structural equality and silently drops re-emissions of identical values; `SharedFlow` doesn't. Don't reach for the broken-`equals` workaround at the type level — fix conflation at the flow boundary.

## Promotion candidates (project-wide)

- [x] **`CancellationException` rethrow discipline in `try/catch (Throwable)` blocks.** — Promoted to `.claude/lessons.md` as L-2026-05-02-04.

- [x] **`flowOf` in test stubs cannot model hot-source races.** — Promoted to `.claude/lessons.md` as L-2026-05-02-05.

- [x] **Compose `LaunchedEffect` lambda capture: wrap caller-provided lambdas in `rememberUpdatedState`.** — Promoted to `.claude/lessons.md` as L-2026-05-02-06.

- [x] **Don't fix `StateFlow`-conflation-of-identical-emits at the type level — fix it at the flow boundary.** — Promoted to `.claude/lessons.md` as L-2026-05-02-07.

- [ ] **Predictive issue bodies don't auto-close issues — verify post-merge.** Left as campaign-only (skipped at promotion gate — meta-process insight, not project-pattern).

- [x] **`combine`-with-trigger flows: reason explicitly about write-order between trigger updates and downstream `_uiState` mutations.** — Promoted to `.claude/lessons.md` as L-2026-05-02-08.

- [ ] **Bundling architecturally-identical issues into one PR is acceptable when file-set conflicts would otherwise serialize them across waves.** Left as campaign-only (skipped at promotion gate — orchestration-process insight, not project-pattern).
