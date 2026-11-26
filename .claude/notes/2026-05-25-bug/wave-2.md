# Wave 2 — campaign 2026-05-25-bug

**Issues attempted:** 1 (synthetic — no GitHub issue) · **Succeeded:** 1 · **Failed:** 0

This wave wasn't planned at campaign start. It came from a wave-1 lesson-promotion sanity-check that exposed a contradiction:

- Wave 1's PR #176 (issue #171) worked empirically (Mokkery `throws` → `calls { throw }` swap; 3 consecutive iOS test runs PASSED).
- But `.claude/lessons.md` had `L-2026-05-18-05` claiming the same root cause had been solved earlier by disabling reports on `KotlinNativeTest` in `KotlinMultiplatformLibraryConventionPlugin.kt:85-89` — and that fix was indeed active for the modules PR #176 touched.

So one fact had to give. A focused investigation agent (no edits, no PR) reproduced the issue on `dev` and surfaced the root cause; this wave is the follow-up that closes the real gap.

## Investigation findings (driver for this wave)

- **The crash actually happens in the `allTests` aggregator** (`org.gradle.api.tasks.testing.TestReport`), NOT in `KotlinNativeTest`. KGP auto-registers `allTests` per KMP module. On `dev`, with PR #176 NOT applied:
  - `:feature:game:iosSimulatorArm64Test --rerun-tasks` (×3) — **all pass**
  - `:feature:game:allTests --rerun-tasks` — **fails** with `Index 95 out of bounds for length 2`
  - Stack trace: `TestReport.generateReport` → `GenericHtmlTestReportGenerator.generate` → `TestOutputReader.iterateEvents` → `DefaultTestOutputEventSerializer.read` → `BaseSerializerFactory$EnumSerializer.read` → AIOOBE
- **L-2026-05-18-05's `KotlinNativeTest`-only disable misses this aggregator.** `allTests` uses the same generator pipeline but isn't a `KotlinNativeTest`.
- **L-2026-05-18-05's claim that "modules without throwing mocks reproduce" is false.** `:feature:home` and `:feature:store` (cited examples) actually DO use `throws`-mode at known line numbers. `:feature:favourites` is the genuinely-throws-free module — and it passes. `:feature:search` wraps its `throws` inside `sequentially { }` (less stdout) — also passes.
- **`throws`-mode stdout volume materially affects reproduction likelihood.** It isn't the root cause but it inflates the corrupted-stream blast radius. The L-2026-05-17-12 thesis was deprecated prematurely.

So PR #176's `calls { throw }` swap helps for an *indirect* reason (less stdout → corrupted-stream parser stumbles past silently). PR #177 closes the underlying gap.

## The PR

### PR #177 — Cover `allTests` aggregator in the Gradle 9 ↔ KGP 2.3 report-disable fix — ✅ open

- **URL:** https://github.com/Mithrandir21/game-deals-android-app/pull/177
- **Branch:** `wave/2026-05-25-bug/extend-alltests-aggregator-fix` (base: `dev`, 2 commits ahead)
- **Files (2):**
  - `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryConventionPlugin.kt` — added `import org.gradle.api.tasks.testing.TestReport` and a `tasks.withType(TestReport::class.java).configureEach { enabled = false }` block. `KotlinTestReport` is a subclass of `TestReport`, so the `withType` matches the KGP-registered `allTests` task.
  - `.claude/lessons.md` — amended L-2026-05-18-05 in place (preserved ID/status/confidence/date, added `allTests` tag). Rewrote TL;DR to cover both call sites, documented the `TestReport` aggregator as the second consumer, retracted the false throws-free claim, and reinstated Mokkery `throws`-mode as a real-but-not-root-cause contributor.
- **Net diff:** +11/-5.
- **Commits:**
  1. `6128c03 fix: extend allTests-aggregator disable to fully cover Gradle 9 ↔ KGP 2.3 report bug`
  2. `7744a6e docs: amend L-2026-05-18-05 to cover allTests aggregator and reinstate throws-mode role`

### Verification (on `dev` test code, PR #176 NOT applied)

This was the key proof. The investigation said the build-config fix alone should be sufficient — wave-2's worker had to verify that, NOT cheat by also having PR #176's test changes applied.

- `:feature:game:allTests :feature:giveaways:allTests :feature:home:allTests :feature:store:allTests --rerun-tasks --continue` → **BUILD SUCCESSFUL**. All four `:allTests` tasks `SKIPPED` (correct — disabled tasks are reported as skipped, and pass/fail still propagates via the per-target tasks). All four `iosSimulatorArm64Test` ran and passed.
- Regression check: `:feature:favourites:iosSimulatorArm64Test :feature:home:iosSimulatorArm64Test` — **BUILD SUCCESSFUL**.

So PR #177's build-config change is necessary AND sufficient. PR #176's `calls { throw }` swap is now defense-in-depth and a stdout-noise reducer rather than the load-bearing fix.

## Sanity-check results

- Branch on origin: ✅ (`7744a6e`)
- Commits ahead of dev: 2 (code + docs, as planned)
- PR #177 OPEN, base=`dev`, mergeable=`MERGEABLE`, title matches expected
- PR scope: exactly the 2 declared files (`KotlinMultiplatformLibraryConventionPlugin.kt` + `.claude/lessons.md`)
- Parent project: clean of wave-2 changes — verified post-completion

## Notes for reviewer

- **Recommended merge order:** PR #177 first (root-cause), then PR #176 (defense-in-depth + cosmetic stdout cleanup). The opposite order is fine too — they're orthogonal.
- **L-2026-05-18-05 in-place amendment** preserves the lesson ID, confidence, status, and added-date — this is an amendment, not a new lesson. The agent did this correctly per the brief. Anyone reading the lesson going forward sees the corrected version.
- **`KotlinTestReport extends TestReport`** — so `tasks.withType<TestReport>` matches KGP's subclass without special-casing. No additional imports of KGP-internal types needed.
- **`allTests` displayed as `SKIPPED`** in Gradle output is the success signal here. It does NOT mean tests were skipped — the per-target `iosSimulatorArm64Test` tasks still ran. Only the report-aggregation step is disabled.

## Conflicts deferred from this wave

None.

## Operational notes

- **Sub-agent failure rate:** 0/1 (1/1 succeeded).
- **Path-mixup incident: AGAIN.** Wave-2's worker reported: *"My first round of edits silently went to the *parent* project paths under `/Users/bam/REPO/PRIVATE/game-deals-android-app/` rather than the worktree at `/Users/bam/REPO/PRIVATE/game-deals-android-app/.claude/worktrees/agent-<id>/`. The build still ran (using the worktree's untouched files via `./gradlew` from the worktree CWD), which made it look like the fix wasn't taking effect. I lost ~45 min on a 'the closure never fires' red herring before realizing every `tasks.withType(TestReport)` and `println` I added was in the wrong tree."*
  - This is the **third** sub-agent in this campaign that hit this — wave-1's #170 worker, this wave's worker, and an earlier in-campaign Edit/Write of mine slipped into the parent (less consequential, since I'm not in a worktree).
  - **The brief explicitly warned about this.** The worker said "I should have used worktree-absolute paths from the start." But warnings haven't been enough — this needs a stronger guard in the SKILL.md (and possibly in the worktree-isolation harness itself).
- **Wave shape:** PR-per-issue (1 PR for the synthetic in-campaign follow-up).
- **JDK / SDK setup:** worker required `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` AND `ANDROID_HOME=/Users/bam/Library/Android/sdk` — same as wave 1's #171 worker. The ANDROID_HOME requirement is because worktrees don't carry `local.properties` (gitignored).
