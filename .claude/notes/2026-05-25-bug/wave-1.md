# Wave 1 — campaign 2026-05-25-bug

**Issues attempted:** 2 · **Succeeded:** 2 · **Failed:** 0

Two `bug`-labeled issues handled in parallel worktree-isolated sub-agents. File sets fully disjoint (`:remote`+`:app`+`:iosApp` vs `:feature:game`/`:feature:giveaways` commonTest), so the two ran without coordination.

## Issues

### #170 — Inject build type into `:remote` from `:app` via Koin — ✅ open

- **PR:** [#175](https://github.com/Mithrandir21/game-deals-android-app/pull/175) — `fix(#170): inject build type into :remote from :app via Koin`
- **Branch:** `wave/2026-05-25-bug/issue-170-inject-build-type` (commit `15a1fe6`)
- **Files (9):**
  - `remote/src/commonMain/.../logic/RemoteBuildUtil.kt` — `RemoteBuildUtilImpl` ctor takes `RemoteBuildType`; `expect fun currentBuildType()` removed
  - `remote/src/commonMain/.../di/RemoteModule.kt` — `remoteModule` is now `fun remoteModule(buildType: RemoteBuildType): Module`
  - `remote/src/androidMain/.../RemoteBuildUtil.android.kt` — deleted
  - `remote/src/iosMain/.../RemoteBuildUtil.ios.kt` — deleted
  - `app/build.gradle.kts` — re-enabled `buildFeatures.buildConfig = true` (not in planner scope but necessary corollary for reading `BuildConfig.BUILD_TYPE`)
  - `app/src/main/.../GameDealsApplication.kt` — calls `remoteModule(currentRemoteBuildType())` where the helper maps `BuildConfig.BUILD_TYPE` → `RemoteBuildType`
  - `iosApp/src/iosMain/.../MainViewController.kt` — same pattern using `Platform.isDebugBinary`
  - `app/src/androidTest/.../TestGameDealsApplication.kt` — passes `RemoteBuildType.RELEASE` to `remoteModule(...)`
  - `app/src/androidTest/.../TestNetworkOverridesModule.kt` — switched from local `val` to publishing `single<RemoteBuildUtil>` so anything resolving via Koin also gets RELEASE
- **Verification:** `:app:assembleDebug` ✅, `:remote:testAndroidHostTest` ✅, `:iosApp:compileKotlinIosSimulatorArm64` ✅. `:app:assembleRelease` compiled cleanly through `compileReleaseKotlin`/`processReleaseResources`/`compileReleaseJavaWithJavac`; failed at `validateSigningRelease` only because `upload_keystore.jks` isn't checked in (pre-existing on `dev`).
- **RELEASE Ktor-Logging regression resolved at the source level:** worker traced the call chain end-to-end (`BuildConfig.BUILD_TYPE` → `currentRemoteBuildType()` → ctor-injected `RemoteBuildType` → `GameDealsHttpClient`'s `when` → `RELEASE → Unit`). Cannot runtime-verify without a signed APK on a device.
- **Worker operational note:** had a path-mixup at the start — initial `Edit`/`Write` calls with absolute `/Users/bam/REPO/PRIVATE/game-deals-android-app/...` paths landed in the parent project instead of the worktree. Worker noticed via `git diff` showing no worktree changes, re-applied edits using worktree-absolute paths, and reverted the parent-project files. **Process implication: sub-agent prompts in worktree-isolation mode should use worktree-relative paths to avoid this trap.** I verified the parent project ended clean of #170-related changes before continuing.

### #171 — Mokkery `throws` → K/N test-reporter crash workaround — ✅ open

- **PR:** [#176](https://github.com/Mithrandir21/game-deals-android-app/pull/176) — `fix(#171): route Mokkery throws via calls{} to dodge K/N test-reporter crash`
- **Branch:** `wave/2026-05-25-bug/issue-171-mokkery-throws-workaround` (commit `520951f`)
- **Files (2 — commonTest only):**
  - `feature/game/src/commonTest/.../GameViewModelTest.kt` — 3 setups (`error_state`, `toggleFavourite_while_uiState_is_Error_does_not_call_repository`, `reload_after_initial_failure_flips_Error_back_to_Data`). Added `dev.mokkery.answering.calls` import; dropped `throws`.
  - `feature/giveaways/src/commonTest/.../GiveawaysViewModelTest.kt` — 6 setups including the `CancellationException("scope cleared")` case. Added `kotlinx.coroutines.flow.flow`; dropped `throws`. (`calls` was already imported.)
- **Pattern applied:**
  - Suspend functions returning a value: `everySuspend { … } calls { throw Exception() }`
  - Non-suspend functions returning `Flow<…>`: `every { … } returns flow { throw Exception() }`
  - Inside `sequentially { }`: `calls { throw Exception() }` (works the same)
- **Verification (the important part):** `:feature:game:iosSimulatorArm64Test --rerun-tasks` — **before** FAILED with `Index N out of bounds for length 2`; **after** BUILD SUCCESSFUL reproduced over **three consecutive runs**. Same outcome on `:feature:giveaways:iosSimulatorArm64Test`. Android-side `testAndroidHostTest` still PASSES (14/14 game, 13/13 giveaways) so semantics are unchanged.
- **Cosmetic side-effect:** worker noted the K/N runtime still prints `at N test.kexe …` frames to stdout (the throws now happens inside the answer block, not via Mokkery's throws-mode, but K/N still surfaces uncaught throws). The Gradle reporter now logs a non-fatal `IllegalStateException: Received output for test that is not running` at `--info` level — task succeeds, exits 0. Cosmetic, not a regression.
- **Worker corrections worth surfacing:**
  - Task name `:feature:game:testDebugUnitTest` from the issue body doesn't exist under the AGP 9 KMP-library plugin — correct task is `testAndroidHostTest`.
  - Worktree lacked `local.properties` (gitignored), so Gradle's Android plugin demanded `ANDROID_HOME`. Worker passed `ANDROID_HOME=/Users/bam/Library/Android/sdk JAVA_HOME=…/JBR/Contents/Home` for every Gradle invocation.
- **Follow-up surfaced:** same throws pattern exists in `:feature:home` / `:feature:search` / `:feature:store` — flagged in PR body as a clean follow-up, not changed in this PR.

## Conflicts deferred from this wave

None. Two-issue wave with fully disjoint file sets (different modules, different source sets).

`#148` (sealed `Destination` / SKIE adoption) was excluded from the candidate pool *before* wave planning — it's informational rather than a localized fix, and was re-labeled in GitHub (removed `bug`, kept `tech-debt` + `area:kmp`).

## Sanity-check results

- Both branches present on `origin`: ✅
- Both PRs OPEN, base=`dev`, head matches expected branch, mergeable=MERGEABLE: ✅
- Both PR titles match `fix(#NNN): …` convention: ✅
- Both `Closes #NNN` lines in PR bodies: ✅
- **PR #175 merge contents:** exactly 9 files (planner-declared scope), +26/-41
- **PR #176 merge contents:** exactly 2 files (planner-declared scope), +11/-11

**Note on the PR #176 UI display:** during sanity check, GitHub's PR view briefly showed 11 files/89 additions because user pushed local-`dev` (3 unpushed commits) to `origin/dev` mid-flight, and the PR's recorded `baseRefOid` didn't immediately re-compute. The true merge introduces only the 2 declared test files — verified via local `git merge-base` + `git diff`. GitHub UI will refresh on next PR event.

## Notes for reviewer

- **#170 — verify Release behavior end-to-end.** Worker confirmed via source-trace that RELEASE no longer installs Ktor Logging, but couldn't run a signed APK to runtime-verify. Reviewer should either (a) confirm the source chain matches expectations or (b) sanity-check with `assembleRelease` + an APT logcat scan on a device.
- **#170 — `buildFeatures.buildConfig = true`** was set inline in `app/build.gradle.kts` rather than in the convention plugin, so it stays scoped to `:app` where it's actually needed.
- **#171 — workaround, not root cause.** The PR body explicitly calls this out. Upstream tracking: Mokkery (https://github.com/Lupuuss/Mokkery) and Gradle K/N test runner TeamCity parsing.
- **#171 — follow-up scope.** Same Mokkery `throws` pattern lives in `:feature:home` / `:feature:search` / `:feature:store`. Worth a future single-PR sweep if iOS local-dev becomes routine.

## Operational notes

- **Sub-agent failure rate:** 0/2.
- **Sanity-check failures:** 0.
- **Re-entries:** 0.
- **Path-mixup incident:** 1 (the #170 worker). Recovered cleanly; parent project verified clean. Suggests sub-agent prompts could explicitly say "use worktree-relative paths" or be given the worktree path as the only acceptable absolute prefix.
- **JDK setup:** both workers required Android Studio's bundled JBR 21 (`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`). Worker for #171 also needed `ANDROID_HOME` because worktrees don't carry `local.properties`.
- **Shape:** PR-per-issue (default), not batched.
