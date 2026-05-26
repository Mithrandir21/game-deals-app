# Campaign lessons — 2026-05-25-bug

## Campaign lessons

### Wave 1 — direct from the two PRs

- **Issue #170 — re-enabling `buildFeatures.buildConfig = true` is the right way to keep `BuildConfig.BUILD_TYPE` readable in `:app` under AGP 9.** The AGP 9 default is `buildConfig = false`. `:app` is `com.android.application` and is the only module that legitimately has per-variant build types, so the flag was set inline in `app/build.gradle.kts` rather than in the shared convention plugin. Library modules (`:remote`, `:domain`, etc.) intentionally don't get this flag — they receive the build type by injection.

- **Issue #170 — DI for build-type-conditional config: ctor-inject the enum, don't switch on it inside the library.** Fix shape: `:remote` exposes `remoteModule(buildType: RemoteBuildType)` as a function. `:app` (Android) computes `BuildConfig.BUILD_TYPE → RemoteBuildType` once and passes it in; `:iosApp` computes from `Platform.isDebugBinary` once and passes it in. This kills the `expect/actual currentBuildType()` accidentally-Android-shaped surface area entirely. Lesson: when a KMP library needs platform-derived configuration, prefer "pass it in from the platform entry point" over `expect/actual` indirection.

- **Issue #171 — AGP 9 KMP-library plugin task names:** the conventional `testDebugUnitTest` task does NOT exist on modules using the AGP 9 KMP-library plugin (single-variant). The actual task is `testAndroidHostTest`. The issue body's command was wrong; worker fixed during verification. **Promotion candidate.**

### Wave 2 — investigation finding (the big one)

- **The Gradle 9 ↔ KGP 2.3 generic-report deserializer bug has TWO call sites in this project, not one.**
  - `KotlinNativeTest` (per-target task) — already covered by `KotlinMultiplatformLibraryConventionPlugin.kt:85-89` via `reports.html.required = false` + `reports.junitXml.required = false` (was: lines 85-89; after PR #177: a slightly larger block).
  - `TestReport` / `allTests` (per-module aggregator KGP auto-registers) — NOT covered until PR #177. Re-enters the same `GenericHtmlTestReportGenerator` → `output-events.bin` deserializer that L-2026-05-18-05 named, and crashes the same way.
  - **L-2026-05-18-05 has been amended in PR #177** to reflect this (TL;DR updated, body documents both call sites, original throws-mock retraction reversed). The lesson ID is preserved; this is an amendment, not a new lesson.

- **The Mokkery `throws`-mode hypothesis (L-2026-05-17-12) was deprecated prematurely.** It's not the root cause, but it materially inflates stdout volume, which makes the corrupted-stream parser fail more reliably. Modules with `throws`-mode (`:feature:game`, `:feature:giveaways`, `:feature:home`, `:feature:store`) reproduced; modules without (`:feature:favourites`) or with throws wrapped in `sequentially { }` (`:feature:search`) did not. So:
  - PR #176's `throws` → `calls { throw }` swap is a real-but-indirect mitigation — it reduces the blast radius rather than fixing the deserializer.
  - PR #177's `TestReport` disable is the root-cause fix — sufficient on its own to make the cited modules' `allTests` pass on `dev` even with the original `throws`-mode tests intact.
  - The two PRs are complementary, not redundant.

- **`KotlinTestReport extends TestReport`**, so `tasks.withType(TestReport::class.java).configureEach { enabled = false }` matches KGP's `allTests` aggregator without special-casing the KGP-internal subtype. No new imports of KGP internals needed.

### Process — sub-agent path-mixup, observed THREE times in this campaign

When sub-agents run in `isolation: "worktree"` and the prompt cites planner-declared paths as parent-project absolutes (`/Users/bam/REPO/PRIVATE/game-deals-android-app/...`), the worker tends to issue its initial `Edit`/`Write` calls against those parent paths rather than the worktree-prefixed paths (`/Users/bam/REPO/PRIVATE/game-deals-android-app/.claude/worktrees/agent-<id>/...`). Failure mode varies:
- **Wave 1 #170 worker:** caught the mistake via `git diff` showing no worktree changes; reverted parent, re-applied to worktree. Cost: minor lost time.
- **Wave 2 #177 worker:** ran the build from the worktree CWD (Gradle correctly used the worktree's unchanged files), so the build behavior didn't expose the mistake — the worker burned ~45 minutes debugging a "the closure never fires" red herring before noticing every edit was in the wrong tree.
- **Me (orchestrator, mid-campaign):** had a CWD drift into a worktree and briefly thought the parent project was on the wave branch.

The brief for both workers explicitly warned about this. Verbal warnings aren't enough. **This needs to be promoted into the `/github-issue-waves` SKILL.md as a structural rule** — e.g., declare paths in worker briefs as worktree-relative (`build-logic/convention/...`) rather than parent-absolute, OR require the worker to print its CWD and verify the prefix before any edit, OR have the harness expose only the worktree path.

## Promotion candidates (project-wide)

- [x] **L-2026-05-18-05 amendment** — already in PR #177 (`.claude/lessons.md`). Once merged, the project-wide lesson reflects the wave-2 finding. No further promotion needed.

- [ ] **New lesson: AGP 9 KMP-library plugin test task is `testAndroidHostTest`, not `testDebugUnitTest`.** Same as the candidate carried over from wave 1's draft:

  ```
  L-2026-05-25-XX · AGP 9 KMP-library test task is `testAndroidHostTest`, not `testDebugUnitTest`
  Status: active · Confidence: confirmed · Tags: gradle, agp9, kmp, testing, task-names
  Applies to: any module using the AGP 9 KMP-library plugin (most :feature:*, :common, :domain, :remote)

  AGP 9 KMP-library modules are single-variant — they have no `testDebugUnitTest` or
  `testReleaseUnitTest`. The Android-host JVM test task is `testAndroidHostTest`. iOS sim
  tests remain `iosSimulatorArm64Test`. Verify by `./gradlew :feature:foo:tasks` before
  assuming a debug-variant task name exists.

  Source: 2026-05-25-bug wave 1 (PR #176 / issue #171). Issue body cited a non-existent
  task; worker fixed during verification.
  ```

  Recommend promoting. It's a small, factual lesson with high blast radius — any future bug-hunt finding or runbook that wants to "run the tests for module X" will hit this.

- [ ] **Worktree sub-agent path-mixup pattern.** A process tip about the `/github-issue-waves` skill — not a project-code lesson. Recommend updating the SKILL.md (in the "Per-issue sub-agent prompt template" section) to require worktree-relative paths for the `## Files you said you would touch` block, and to instruct the worker to `pwd` before the first edit. Keep this campaign-only as a *finding*; promote the SKILL.md change separately.

- [ ] **Worktrees don't carry `local.properties` (gitignored), so any Gradle invocation in a worktree needs `ANDROID_HOME` passed inline.** Same category as the path-mixup tip — a process tip for `/github-issue-waves` SKILL.md, alongside the existing `JAVA_HOME=…/JBR/Contents/Home` reference memory. Recommend adding to the SKILL.md's "Per-issue sub-agent prompt template" boilerplate.

- [ ] **KMP build-type derivation: prefer constructor injection from the platform entry point over `expect/actual`.** A KMP-architecture idiom drawn from PR #175 (issue #170). Could be a project-wide lesson if you want to make it normative. Possible body:

  ```
  L-2026-05-25-YY · Prefer ctor-inject build-type / platform-derived config over expect/actual
  Status: active · Confidence: confirmed · Tags: kmp, di, koin, platform-config, agp9

  When a KMP library needs platform-derived configuration (build type, locale-from-system, OS-build-version, etc.), prefer to express the API as a function or class
  that *takes* the config as a parameter, then have each platform entry point (`:app`'s Application.onCreate, `:iosApp`'s MainViewController) compute and pass it in.
  Avoid `expect fun currentX()` actuals when the platform value isn't truly platform-API-shaped — it ossifies one Android-vs-iOS asymmetry into the API surface
  that's easy to get wrong (see PR #175 / issue #170 — Android single-variant under AGP 9 silently broke an actual that read `BuildConfig.BUILD_TYPE`).

  Source: 2026-05-25-bug wave 1 (PR #175 / issue #170).
  ```

  Recommend keeping campaign-only unless the codebase grows another instance of the pattern — one example is borderline for a normative lesson.
