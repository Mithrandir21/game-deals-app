# Lessons Learned

Condensed, structured lessons from past development sessions. Claude reads this file at the start of each session (via a separate skill) so it can apply past learnings without re-deriving them.

Each lesson has an immutable ID. When a lesson is superseded or turns out to be wrong, it is moved to `## Archive` with an updated status chip — its content is never rewritten. This preserves the audit trail.

**Claude:** apply lessons from `## Active` only. Consult `## Archive` only if something appears contradictory and you need the history. Ignore `## Index — Active` — it's a generated human-readable TOC, not a source of lesson content.

## Index — Active
<!-- generated; do not hand-edit -->

| ID | TL;DR | Tags |
|---|---|---|
| L-2026-05-27-01 | If `assertHasClickAction` passes but `performClick + verify` fails only on emulator, use `performSemanticsAction(SemanticsActions.OnClick)`. | testing, compose, instrumented, emulator |
| L-2026-05-25-01 | `@Insert(REPLACE)` only refreshes rows whose keys still appear upstream — clear before insert in a `@Transaction` or stale rows accumulate forever. | room, kmp, dao, cache, transaction |
| L-2026-05-22-03 | Kover 0.9.x can't read instrumented `.ec` files (kotlinx-kover#96); add a root `JacocoReport` task for device coverage. | kover, jacoco, android-test, coverage, kmp |
| L-2026-05-22-01 | `@Database` and `@AutoMigration` use `RetentionPolicy.CLASS`; mirror values into a `const val` and a `Set<Pair<Int,Int>>` so tests can see them. | room, kmp, testing, migrations |
| L-2026-05-22-02 | Every visible MigrationTestHelper constructor in androidHostTest requires an Instrumentation — needs Robolectric or skip the runtime test. | room, kmp, testing, migrations, robolectric |
| L-2026-05-18-07 | Default fix for return-value-checker drops on Room write methods is `@IgnorableReturnValue`, not Unit-return. | room, kmp, architecture, reactive |
| L-2026-05-18-06 | `-Xreturn-value-checker=full` is warning-level — add `-Werror` to fail builds; coroutines/stdlib already annotate common drops. | kotlin-2.3, compiler-flags, coroutines, kmp |
| L-2026-05-18-05 | Disable `reports.html.required` and `reports.junitXml.required` on every `KotlinNativeTest` to dodge the Gradle 9 ↔ KGP 2.3 report bug. | gradle-9, kgp, kotlin-native, ios, test-reports, tooling-bug |
| L-2026-05-18-04 | Every Kotlin compilation consuming klibs built with `-Xexplicit-backing-fields` needs the same flag — audit the whole graph. | kotlin-2.3, kmp, gradle, convention-plugins, experimental-features |
| L-2026-05-18-03 | Compose trusts `@Immutable` on a parent as authoritative — nested `data class` fields inside it can drop their own annotation. | compose, stability, kotlin, k2 |
| L-2026-05-18-02 | Don't pass `null` to Coil 3.x `AsyncImage(model = …)` — guard the call site and render a placeholder `Image` instead. | coil, compose, logging, kmp |
| L-2026-05-18-01 | Before adopting a KMP Gradle plugin, read its own `libs.versions.toml` for the `kotlin = "…"` pin — that's the real klib ABI source. | kmp, gradle, klib, dependencies, kotlin |
| L-2026-05-17-16 | After launching an AVD for `connectedAndroidDeviceTest` locally, run `adb shell settings put global mdevx.grpc_guest_port 8554` once. | instrumented-tests, espresso-device, emulator, local-dev, ci-parity |
| L-2026-05-17-15 | Gate `withDeviceTestBuilder { }` on `project.file("src/androidDeviceTest").exists()` so modules without tests skip the device-test pipeline. | agp-9, kmp-library-plugin, convention-plugins, instrumented-tests, conditional-config |
| L-2026-05-17-14 | Set `androidResources { enable = true }` on every `com.android.kotlin.multiplatform.library` target, or Compose Resources fails. | agp-9, kmp-library-plugin, compose-multiplatform, compose-resources, gradle-config |
| L-2026-05-17-13 | Bump `org.gradle.jvmargs` to `-Xmx8192m` — AGP 9 KMP-library dex-merge across many androidDeviceTest APKs OOMs at the 4 GB default. | agp-9, kmp-library-plugin, dex-merge, jvm-heap, gradle-properties, instrumented-tests |
| L-2026-05-17-11 | CMP 1.11 needs Kotlin 2.3 on iOS targets and removes `iosX64()` — drop it from every KMP target list and matching KSP per-target deps. | compose-multiplatform, kotlin-native, ios, kmp, target-removal, upgrade |
| L-2026-05-17-10 | On AGP 9 + KSP 2.3.x, set `android.disallowKotlinSourceSets=false` in `gradle.properties` to unblock KSP's source-set registration. | ksp, agp-9, kotlin-built-in, gradle-properties, workaround |
| L-2026-05-17-09 | AGP 9 KMP-library is single-variant — `debugImplementation`, library `buildTypes.release { proguardFiles }`, and `buildConfig` are gone. | agp-9, kmp-library-plugin, build-variants, compose-tooling, buildconfig, proguard |
| L-2026-05-17-08 | AGP 9 KMP-library renames `androidUnitTest`→`androidHostTest` and `androidInstrumentedTest`→`androidDeviceTest` — update files & CI. | agp-9, kmp-library-plugin, source-sets, gradle-tasks, ci, instrumented-tests |
| L-2026-05-17-07 | Build scripts: `android {}`. Convention plugins: `targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach { … }`. | agp-9, kmp-library-plugin, convention-plugins, dsl, kotlin-multiplatform |
| L-2026-05-17-05 | Before pushing a KMP Gradle plugin, run an iOS-target compile (`:…:compileKotlinIosArm64`) — Android smoke tests miss klib ABI skew. | kmp, gradle, kotlin-native, klib, ci |
| L-2026-05-17-06 | Wire missing `dependsOn(compileXxxKotlin)` in a convention plugin to satisfy Gradle 9's strict task validator for third-party plugins. | gradle, kotlin-compile, task-validation, convention-plugin |
| L-2026-05-17-01 | K2 in Kotlin 2.2.21 doesn't infer cross-module `data class` stability — annotate cross-module domain models with `@Immutable` explicitly. | compose, stability, recomposition, kmp, kotlin-2-2, k2 |
| L-2026-05-17-02 | Prefer `@Immutable` over `stability_config.conf` — only `@Immutable` enables Compose lambda memoization and static-expression detection. | compose, stability, recomposition, lambda-memoization, annotations |
| L-2026-05-17-03 | Verifying a Compose stability fix needs `--rerun-tasks` (or `rm -rf */build/compose-reports/`) — incremental compile skips regen. | compose, stability, gradle, incremental-compile, ci, verification |
| L-2026-05-17-04 | Run `./gradlew compileDebugAndroidTestKotlinAndroid` before signature sweeps — `testDebugUnitTest` skips the instrumented-test source set. | testing, instrumented-tests, gradle, ci, signature-change |
| L-2026-05-15-08 | Before adding retry infrastructure for a bug-hunt finding, check the error path can actually fire — Room observation flows rarely throw. | scope, bug-hunt, error-handling, viewmodel, refactor |
| L-2026-05-15-07 | `Dispatchers.IO` is `internal` on Kotlin/Native — in `iosMain` use `Dispatchers.Default` or inject the dispatcher per platform. | kmp, coroutines, kotlin-native, dispatchers, ios |
| L-2026-05-15-06 | Use `@Transaction suspend fun` default methods on `interface` DAOs for atomic RMW — Room 2.8.x KMP accepts them on Android and iOS. | room, kmp, ksp, dao, transaction, atomicity, sqlite |
| L-2026-05-15-05 | Replace `Pair<A, B>` in any Compose parameter with a small named `@Immutable data class` — `Pair` is unstable even inside `ImmutableList`. | compose, stability, recomposition, immutable, pair, kotlinx-collections-immutable |
| L-2026-05-15-04 | Swapping `Eagerly` for `WhileSubscribed(5_000)` on a VM `uiState` is not test-transparent — emission counts and trigger order shift. | testing, stateflow, sharingstarted, viewmodel, coroutines-test, mokkery, eagerly, whilesubscribed |
| L-2026-05-15-03 | Where one platform's `actual` reads live OS state, the other's must too — default `expect`/`actual` pairs to per-call reads, never cached. | kmp, expect-actual, platform-parity, system-state, locale, timezone |
| L-2026-05-15-02 | Add `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` whenever `iosMain` uses `CGRectMake`/`useContents` or cinterop APIs. | kotlin-native, cinterop, opt-in, ios, uikit |
| L-2026-05-15-01 | ObjC category properties (e.g. `popoverPresentationController`) are bridged as top-level Kotlin extensions — add an explicit `import`. | kotlin-native, ios, uikit, objc-interop, imports |
| L-2026-05-14-02 | In `*ScreenTest.kt`, hoist `stringResource` lookups into a per-class `ScreenSemantics` and wrap `setContent` in `setupCompose(...)`. | testing, compose, instrumented, boilerplate |
| L-2026-05-14-01 | Never use `testTag` in production or tests — find nodes by `onNodeWithText`, `onNodeWithContentDescription`, or role matchers. | testing, compose, instrumented, accessibility, content-description |
| L-2026-05-13-02 | Preview a `ModalBottomSheet`'s body composable directly inside `Surface { … }` — `ModalBottomSheet` itself renders blank in `@Preview`. | compose, preview, material3, modal-bottom-sheet |
| L-2026-05-13-01 | In commonMain previews use the JetBrains `Preview` import + `@file:Suppress("DEPRECATION")` — the AndroidX one won't resolve on iOS. | compose, kmp, preview, jetbrains-compose, deprecation |
| L-2026-05-11-05 | Adding a collected `StateFlow` to a ViewModel breaks strict MockK in `*ScreenTest.kt` — backfill `every { vm.newFlow } returns …` stubs. | testing, mockk, instrumentation, viewmodel, compose |
| L-2026-05-11-04 | To test a Flow consumer's `.catch` recovery, return `flow { throw … }` from the Mokkery stub — not `every { … } throws`. | testing, mokkery, flow, kmp |
| L-2026-05-11-03 | Mokkery's `MockMode.autoUnit` no-ops only Unit returns — stub every non-Unit call with `everySuspend { … } returns <default>`. | testing, mokkery, kmp, commontest |
| L-2026-05-11-02 | Any new Koin module or `CompositionLocalProvider` must be wired in BOTH `:app:MainActivity` AND `:iosApp:MainViewController`. | kmp, compose-multiplatform, koin, composition-local, ios, dual-host |
| L-2026-05-11-01 | Use a no-op singleton default for shared `staticCompositionLocalOf` — `error(...)` defaults crash every `@Preview` that reads the local. | compose, composition-local, preview, kmp |
| L-2026-05-06-05 | Pass `canScroll = remember { { true } }` to `TopAppBarDefaults.pinnedScrollBehavior(...)` — the default lambda re-allocates each frame. | compose, recomposition, top-app-bar, material3, stable-lambda |
| L-2026-05-06-04 | This project uses Mokkery for all `commonTest` mocking — MockK is JVM-only and is not used; the patterns doc on this is stale. | testing, mokkery, mockk, kmp, commontest |
| L-2026-05-06-02 | Keep `compose.materialIconsExtended` — `compose.material3` doesn't pull in `material-icons-core`, so `Icons.*` fails to resolve. | compose, compose-multiplatform, gradle, dependencies |
| L-2026-05-06-01 | Register the cross-module iOS-test-serialization `BuildService` against `KotlinNativeTest`, not `KotlinNativeHostTest` (excludes simulator). | gradle, kmp, kotlin-native, ios, build-service |
| L-2026-05-05-04 | Don't integrate sentry-kotlin-multiplatform via Xcode SPM — K/N tests fail to link. Use the Gradle plugin or CocoaPods instead. | sentry, sentry-kotlin-multiplatform, sentry-cocoa, kmp, kotlin-native, spm, gradle, version-skew |
| L-2026-05-05-01 | Don't put `@BeforeTest`/`@AfterTest` on a KMP fixtures module's commonMain — `kotlin("test")` resolves them only in test source sets. | kmp, kotlin-test, source-sets, testing, fixtures-module |
| L-2026-05-04-07 | Before adding an `org.jetbrains.androidx.*` artifact, read the existing transitive version from `:iosApp:dependencies` and pin to that. | kmp, kotlin-native, jetbrains-androidx-fork, version-skew, gradle |
| L-2026-05-04-06 | From K/N, call `NSLog(line.replace("%","%%"))` — the vararg `%@` form crashes at runtime; `String` doesn't bridge to `NSString *`. | kotlin-native, ios, nslog, foundation, interop |
| L-2026-05-04-05 | Use Koin 4.1.0+ for `koinViewModel()` from iOS Composables — 4.0.0 references `androidx.lifecycle.SavedStateHandle` and crashes on K/N. | koin, kmp, kotlin-native, lifecycle, irlinkageerror, koin-compose-viewmodel |
| L-2026-05-04-04 | Align every Ktor artifact via the catalog BOM — a transitive forcing a higher core version causes Native `IrLinkageError` at first call. | ktor, kmp, kotlin-native, version-skew, irlinkageerror |
| L-2026-05-04-03 | Prefer a commonMain Koin module + a platform module seeding the platform binding over `expect`/`actual` for platform-specific construction. | kmp, koin, di, room |
| L-2026-05-04-02 | For phone-vs-wider in commonMain, wrap in `BoxWithConstraints` + check `maxWidth < 600.dp` — `WindowWidthSizeClass` is Android-only. | kmp, compose-multiplatform, adaptive-layout |
| L-2026-05-04-01 | `paging-common` 3.3+ is KMP; `paging-compose` is Android-only. Put the paging ViewModel in commonMain, the Screen in androidMain. | kmp, paging, compose-multiplatform |
| L-2026-05-03-06 | Same-named top-level `.kt` files in commonMain and androidMain need `@file:JvmName("…")` on one side, else duplicate JVM classes. | kmp, kotlin, jvm, source-sets, file-naming |
| L-2026-05-03-05 | In a convention plugin that calls `pluginManager.apply("third.party")`, declare the upstream plugin as `implementation`, not `compileOnly`. | gradle, build-logic, convention-plugins, kmp, compose-multiplatform |
| L-2026-05-03-04 | When removing Hilt from a module that uses `@Immutable`, explicitly add `implementation(libs.androidx.compose.runtime)` — KSP needs it. | ksp, room, hilt-removal, compose-runtime, transitive-deps, kmp |
| L-2026-05-03-03 | For already-encoded query values use `url { encodedParameters.append(...) }` — `parameter(...)` always re-encodes and produces 404s. | ktor, retrofit-migration, url-encoding, networking, kmp |
| L-2026-05-03-02 | Set Ktor `Logging` to `LogLevel.HEADERS` on the OkHttp engine — `LogLevel.BODY` consumes the one-shot body and hangs `body<T>()`. | ktor, ktor-logging, okhttp-engine, content-negotiation, networking, kmp |
| L-2026-05-03-01 | A module opting out of the convention plugin must declare `debugImplementation(libs.androidx.compose.test)` or `createComposeRule` fails. | gradle, convention-plugins, build-logic, compose-testing, ui-test-manifest, test-infra |
| L-2026-05-02-10 | Re-verify bug-hunt findings against `origin/<merge-target>` HEAD before working them — wave branches drift while `dev` moves on. | workflow, planner-agents, worker-agents, merge-target-drift, github-sync, github-issue-waves |
| L-2026-05-02-09 | Don't mix `currentTimeMillis()` with `delay()` in suspend helpers — `runTest` virtualizes only `delay`, so wall-clock checks break tests. | coroutines, test-virtual-time, runtest, dispatchers, common-helpers |
| L-2026-05-02-08 | When one function writes both upstream of `combine` and into `_uiState`, do the upstream write first — combine propagates synchronously. | stateflow, combine, viewmodel, race-conditions, ordering, source-of-truth |
| L-2026-05-02-07 | Fix StateFlow conflation by swapping to `MutableSharedFlow(replay = 1, DROP_OLDEST)` — never break `equals` on the model type. | stateflow, sharedflow, conflation, compose, stability, equals-override, anti-pattern |
| L-2026-05-02-06 | Wrap caller lambdas in `rememberUpdatedState` before capturing in `LaunchedEffect` — otherwise it keeps firing the launch-time copy. | compose, launchedeffect, rememberupdatedstate, stale-capture, lambda-capture |
| L-2026-05-02-05 | Stub hot Flow sources with `MutableSharedFlow`, not `flowOf(...)` — `flowOf` completes after one emission and hides second-emission races. | testing, coroutines, flow, room, race-conditions, test-fixtures |
| L-2026-05-02-04 | In any `catch (Throwable)` whose body suspends, rethrow `CancellationException` first — otherwise structured cancellation breaks silently. | coroutines, cancellation, structured-concurrency, error-handling, crashlytics |
| L-2026-05-02-03 | When a shared type adopts a third-party type/extension, add the dep to every consumer module — `implementation` doesn't propagate. | gradle, modularization, kotlinx-collections-immutable, dependency-graph, build-config |
| L-2026-05-02-02 | Mark every `:domain` `data class` used as a Composable parameter `@Immutable` and retype its `List<…>` fields to `ImmutableList<…>`. | compose, stability, recomposition, immutable-collections, kotlinx-serialization |
| L-2026-05-02-01 | Use `_uiState.update { it.copy(...) }` for field-level merges; reserve `emit(...)` for full-state replacements driven by an upstream Flow. | viewmodel, stateflow, coroutines, race-conditions, atomicity |
| L-2026-05-01-09 | `remember` clients outside `AndroidView`'s `factory` and wire `onRelease` to tear the native view down (e.g. `WebView.destroy()`). | compose, androidview, webview, lifecycle, memory-leak |
| L-2026-05-01-08 | For one-off debug-build checks, use `(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` — avoids opting into BuildConfig. | android, agp, buildconfig, debug-gate, gradle |
| L-2026-05-01-07 | Under `UnconfinedTestDispatcher`, a correctly-shaped VM emits one steady `uiState` — don't assert `[initial, current]`. | viewmodel, stateflow, coroutines, testing, unconfined-test-dispatcher |
| L-2026-05-01-06 | In `:common:ui`, get the host Activity via nullable `LocalActivity.current` — `view.context as Activity` crashes `@Preview` and tests. | compose, theme, activity, common-ui, preview-safety |
| L-2026-05-01-05 | In Flow operators that may run under `TestDispatcher`, pad via parallel `launch { delay(...) }`/join — never `currentTimeMillis()`. | kotlin, coroutines, flow, testing, virtual-time |
| L-2026-05-01-04 | Don't `.catch` after `.cachedIn(...)` on a Paging Flow — let `LoadState.Error` surface load failures or the Flow pins to its last cache. | paging, coroutines, flow, viewmodel, error-handling |
| L-2026-05-01-01 | Read nav args in VMs via `savedStateHandle.get<Primitive>("propName")` — `toRoute<>()` round-trips through `Bundle` and breaks JVM tests. | navigation, compose, viewmodel, testing |
| L-2026-05-01-02 | Don't add Robolectric — find a JVM-only path or move the test to `androidTest`. Robolectric is rejected for this project. | testing, robolectric, project-policy |
| L-2026-05-01-03 | When dropping a `:domain` interface whose `@Inject` impl took internal Daos, mark the renamed concrete class `@Inject internal constructor`. | hilt, di, kotlin-visibility, multi-module |
| L-2026-04-30-06 | Expose VM `uiState` via `_uiState.asStateFlow()` — `stateIn(WhileSubscribed, …)` wraps produce a spurious LOADING flash on resume. | viewmodel, stateflow, coroutines, compose |
| L-2026-04-30-05 | Keep `flow { emitAll(repo.observeXxx()) }.catch{}` wrappers — they convert sync repo throws into in-stream errors `.catch` can handle. | kotlin, coroutines, flow, repository, error-handling |
| L-2026-04-30-04 | Keep ViewModel handlers Flow-shaped (`launch { someFlow.onStart{…}.catch{…}.collect{…} }`) — don't lower to imperative `try/catch`. | kotlin, coroutines, flow, viewmodel, architecture |
| L-2026-04-30-03 | Put integration tests inside the module that owns the `internal` collaborators — never add a test-only factory in `main/` to expose them. | testing, multi-module, mockwebserver, internal-visibility |
| L-2026-04-30-02 | Set CI's `setup-java` `java-version` ≥ `compileOptions.targetCompatibility` — Hilt's `JavaCompile` doesn't use the Kotlin toolchain. | ci, gradle, hilt, jdk, github-actions |
| L-2026-04-30-01 | When the port lives in `:domain` and the `@Provides` impl in `:remote:*`, `:app` must depend on every `:remote:*` so Hilt sees the modules. | hilt, di, multi-module, ports-and-adapters, architecture |
| L-2026-04-27-01 | In Flow chains, prefer the `catchAndContinue(default, action)` helper over `runCatching` — keeps error handling on the Flow itself. | kotlin, coroutines, flow, error-handling |
| L-2026-04-20-01 | When resolving merge conflicts on a long-running migration branch, map them by *feature* — not file-by-file — and decide per feature. | merge-conflicts, migration, di, architecture |

## Active

### L-2026-05-27-01 · performSemanticsAction beats performClick for emulator-flaky clicks
**TL;DR:** If `assertHasClickAction` passes but `performClick + verify` fails only on emulator, use `performSemanticsAction(SemanticsActions.OnClick)`.
`active` · `confirmed` · 2026-05-27 · `testing` `compose` `instrumented` `emulator`
**Applies to:** Compose device tests that scroll-then-click a LazyRow tile or other merged-semantics clickable and fail only on a specific emulator.

On the PR-CI Pixel 5 API-34 emulator, `performScrollTo().performClick()` on a `LazyRow` tile using `Modifier.clickable {}.semantics(mergeDescendants = true) { contentDescription = ... }` passed `assertHasClickAction()` but the touch dispatch missed the pointer-input layer — the production `clickable {}` lambda never fired and the test failed at `verify`. Local physical Pixel 5 passed every run; `waitForIdle()` and `mockk(relaxed = true)` did not help. Switch to `performSemanticsAction(SemanticsActions.OnClick)` — same path TalkBack uses on double-tap, no coordinate math. Reserve for emulator-flaky clicks; prefer `performClick` everywhere else for fidelity.

**Source:** Stabilizing `GameDetailsScreenTest.tapping_similar_game_tile_invokes_callback_with_igdb_id` on PR #178 after CI failures that didn't reproduce on a physical Pixel 5.

### L-2026-05-25-01 · Mirror rotating remote feeds with clear+insert in a DAO @Transaction
**TL;DR:** `@Insert(REPLACE)` only refreshes rows whose keys still appear upstream — clear before insert in a `@Transaction` or stale rows accumulate forever.
`active` · `confirmed` · 2026-05-25 · `room` `kmp` `dao` `cache` `transaction`
**Applies to:** Room DAOs that mirror a "trending"/"latest N" remote list

For DAOs mirroring a rotating remote list (today's "new releases", current giveaways), naive refresh via `@Insert(REPLACE)` leaks entries that drop off the upstream — `REPLACE` only fires on key collisions, so rotated-out rows are never deleted.

Wrap `clearAll() + addAll(...)` in a `@Transaction` default method on the DAO so observers never see an empty intermediate state. Push the transaction up into the repo only when each row needs per-row computation before insert (e.g. stamping a cache `expires` from a clock) — otherwise the DAO owns its own consistency.

**Source:** `:domain`'s `ReleasesDao` and `GiveawaysDao` — weeks of stale "New Releases" accumulated on the home screen before being noticed.

### L-2026-05-22-03 · Kover 0.9.x ignores instrumented coverage; use JaCoCo task
**TL;DR:** Kover 0.9.x can't read instrumented `.ec` files (kotlinx-kover#96); add a root `JacocoReport` task for device coverage.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-22 · **Tags:** kover, jacoco, android-test, coverage, kmp
**Applies to:** Any KMP/Android project where instrumented test coverage matters.

Kover 0.9.x's `koverHtmlReportAndroid` only collects host-test `.ic` files. `enableCoverage = true` (KMP-library device-test) and `enableAndroidTestCoverage = true` (`com.android.application` debug) make AGP write `coverage.ec` per module — but Kover discards them. Add a root-level `JacocoReport` task that aggregates the `.ec` files into `build/reports/jacoco/androidTest/` alongside the Kover host-test report. Two reports, no merge. Kover (FQN wildcards) and JaCoCo (Ant globs) filter sets must be aligned by hand. Track Kover #96 to retire this.

**Source:** Adding instrumented Compose-UI test coverage when Kover wouldn't accept the AGP outputs.

### L-2026-05-22-01 · Room `@Database` is CLASS-retention — not reflectable at runtime
**TL;DR:** `@Database` and `@AutoMigration` use `RetentionPolicy.CLASS`; mirror values into a `const val` and a `Set<Pair<Int,Int>>` so tests can see them.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-22 · **Tags:** room, kmp, testing, migrations
**Applies to:** Tests or runtime code that need to know the current Room DB version or which @AutoMigration pairs are declared

`DomainDatabase::class.java.getAnnotation(Database::class.java)` returns `null` at runtime because `androidx.room.Database` is declared `@Retention(CLASS)`. Same for `androidx.room.AutoMigration`. Don't try to reflect them. Lift the version into a `const val DOMAIN_DB_VERSION` referenced from `@Database(version = …)`, and mirror auto-migration pairs into a `Set<Pair<Int,Int>>` next to the manual `Migration` registry. The duplication is the price for a runtime-reachable single source of truth.

**Source:** Setting up the Layer B structural guard for forgotten Room migrations.

### L-2026-05-22-02 · Room MigrationTestHelper needs Instrumentation in androidHostTest
**TL;DR:** Every visible MigrationTestHelper constructor in androidHostTest requires an Instrumentation — needs Robolectric or skip the runtime test.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-22 · **Tags:** room, kmp, testing, migrations, robolectric
**Applies to:** Adding MigrationTestHelper-backed tests in a KMP module's androidHostTest source set

In Room 2.8.3 the driver-based KMP `MigrationTestHelper` constructor is not exposed from the Android source set — only the four Instrumentation-based constructors are. Without Robolectric, `MigrationTestHelper` cannot be instantiated in pure-JVM `androidHostTest`. If runtime SQL-validity testing isn't worth a Robolectric dep, fall back to a structural test that walks committed schema JSON plus a manual migration registry — catches "forgot to write a migration" but not "the migration SQL is wrong."

**Source:** Layer B migration guard — tried MigrationTestHelper first, hit the constructor wall, fell back to a structural test.

### L-2026-05-18-07 · Room `@Query` Flow observation makes repository write-method return values structurally redundant
**TL;DR:** Default fix for return-value-checker drops on Room write methods is `@IgnorableReturnValue`, not Unit-return.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-18 · **Tags:** room, kmp, architecture, reactive
**Applies to:** Repository / DAO methods that mutate Room tables whose state is also observed via `@Query` Flows

In this codebase the UI never consumes the Boolean from `FavouritesRepository.toggleFavourite`, the `Job` from `DealDetailsController.load`/`.dismiss`, or similar write-method returns — because every consumer subscribes to a Room `@Query` Flow over the mutated table (`observeFavouriteIds`, `observeIsFavourite`, etc.), and Room's invalidation tracker re-emits the moment the transaction commits. The architecture is "DAO is source of truth; UI observes." When the return-value checker flags a write-method drop, the default fix is `@IgnorableReturnValue` on the declaration (not Unit-return) so that future snackbar / analytics / test callers can still consume the value without forcing every current UI site to. Reserve Unit-return for write methods whose return was never semantically meaningful in the first place.

**Source:** `FavouritesRepository.toggleFavourite` triage during `-Xreturn-value-checker=full` rollout

### L-2026-05-18-06 · Kotlin 2.3 `-Xreturn-value-checker=full` is warning-level; kotlinx.coroutines/stdlib pre-annotate the common drops
**TL;DR:** `-Xreturn-value-checker=full` is warning-level — add `-Werror` to fail builds; coroutines/stdlib already annotate common drops.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-18 · **Tags:** kotlin-2.3, compiler-flags, coroutines, kmp
**Applies to:** Adopting or tuning `-Xreturn-value-checker` in any KMP / Android module

`-Xreturn-value-checker=full` is still warning-level despite the name — `-Werror` is needed to make drops fail the build. `=check` is the conservative subset; `=full` widens to project-owned function returns. Kotlinx.coroutines 1.10.x and stdlib already annotate `launch`, `tryEmit`, `MutableList.add` etc. with `@IgnorableReturnValue`, so noise-surface pre-scans that count those patterns over-predict heavily (this session's 15–20-site prediction collapsed to 3 sites under `=check`, 9 under `=full`). For first-party APIs whose return is structurally redundant in this codebase but semantically meaningful, prefer `@kotlin.IgnorableReturnValue` on the declaration over changing the return type to `Unit` — keeps the value available for future callers.

**Source:** PR adopting `-Xreturn-value-checker=full` (commit 192bf84) and the noise-vs-prediction triage during rollout

### L-2026-05-18-05 · `iosSimulatorArm64Test` `Index N out of bounds for length 2` failures are Gradle 9 ↔ KGP 2.3 generic-report deserialization
**TL;DR:** Disable Gradle 9's report generators on every `KotlinNativeTest` AND on the `TestReport`-typed `allTests` aggregator (KGP's `KotlinTestReport` subclass) — both re-enter the same broken pipeline. Mokkery `throws`-mode stdout inflates reproduction likelihood and is worth shrinking as a complementary mitigation.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-18 · **Tags:** gradle-9, kgp, kotlin-native, ios, test-reports, tooling-bug, allTests
**Applies to:** Any `KotlinNativeTest` task and the per-module `allTests` aggregator (`org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport`, which extends `org.gradle.api.tasks.testing.TestReport`) on Gradle 9.x + KGP 2.3.x

Gradle 9 introduced new "generic" test report infrastructure (`org.gradle.api.internal.tasks.testing.report.generic.*`). Both `GenericHtmlTestReportGenerator` and `Binary2JUnitXmlReportGenerator` consume KGP's `output-events.bin` via `TestOutputReader.iterateEvents` → `DefaultTestOutputEventSerializer.read`, which mis-deserializes the binary stream KGP's `TCServiceMessagesClient` writes — crashing reading the `TestOutputEvent.Destination` enum (length 2, getting ordinals like 101 / 108 / 4097). KGP officially supports Gradle up to 9.3.0 (this repo runs 9.3.1 to stay aligned with AGP 9); no upstream fix in Gradle 9.4 / 9.5 or KGP 2.3.x as of writing. The pipeline has **two independent consumers** that must both be silenced: (a) the per-target `KotlinNativeTest` tasks via `reports.html.required = false` and `reports.junitXml.required = false`, and (b) the per-module `allTests` aggregator (`KotlinTestReport extends org.gradle.api.tasks.testing.TestReport`) via `tasks.withType(TestReport::class.java).configureEach { enabled = false }`. The aggregator was missed in the original fix and re-enters `TestReport.generateReport` → `GenericHtmlTestReportGenerator` independently of the per-target reports. The convention plugin gets both: see `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryConventionPlugin.kt:86-96`. With reports disabled the tasks still propagate pass/fail in-process and stdout shows test output; reports were already local-only (CI runs `ubuntu-latest` and never invokes these tasks). Complementary mitigation: Mokkery `throws`-mode stdout (printed Kotlin/Native stack trace per thrown mock) materially inflates the corrupted-stream blast radius, so swapping `everySuspend { … } throws Exception()` for `everySuspend { … } calls { throw Exception() }` reduces reproduction frequency even though it is not the root cause. The earlier deprecation of **L-2026-05-17-12** ("blamed Mokkery `throws`-mock stdout") was premature: the cited counter-examples `:feature:home` and `:feature:store` do in fact use `throws`-mode; the truly throws-free module `:feature:favourites` passes. So `throws`-mode is a real contributor to reproduction, just not the root cause — L-17-12 should be read as "stdout volume matters" rather than "Mokkery is at fault."

**Source:** Post-Kotlin-2.3 migration follow-up — full diagnosis via `--stacktrace --info` traced to `BaseSerializerFactory$EnumSerializer.read`; original fix covered per-target tasks but missed the `KotlinTestReport`-typed `allTests` aggregator (caught on `:feature:game/giveaways/home/store` `allTests` runs on `dev` post-PR-#176 review)

### L-2026-05-18-04 · Kotlin 2.3 `-Xexplicit-backing-fields` propagates pre-release status — every consumer needs the flag
**TL;DR:** Every Kotlin compilation consuming klibs built with `-Xexplicit-backing-fields` needs the same flag — audit the whole graph.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-18 · **Tags:** kotlin-2.3, kmp, gradle, convention-plugins, experimental-features
**Applies to:** Any opt-in to Kotlin 2.3 experimental language features (KEEP-278 explicit backing fields, and similar future opt-ins) in a multi-module project

Adding `freeCompilerArgs.add("-Xexplicit-backing-fields")` to producer modules stamps their compiled klibs as pre-release. Every Kotlin compilation that consumes those klibs — including downstream consumers that don't themselves use the feature — must also have the flag, or it fails to load them with `Class '...' was compiled by a pre-release version of Kotlin and cannot be loaded by this version of the compiler. Enabled pre-release features: ExplicitBackingFields`. In this repo that's three places: `KotlinMultiplatformLibraryConventionPlugin` (KMP producers), `AndroidApplicationConventionPlugin` (`:app`), and `iosApp/build.gradle.kts` (no convention plugin). The same propagation rule applies to any future experimental opt-in — audit every Kotlin compilation in the graph, not just the module introducing the feature.

**Source:** ViewModels migration to KEEP-278 — `:app:compileReleaseKotlin` failed first (missed app plugin); `:iosApp:compileKotlinIosArm64` failed second (missed iosApp build script)

### L-2026-05-18-03 · Compose compiler trusts an `@Immutable` parent class as authoritative — nested `data class` fields don't need their own annotation
**TL;DR:** Compose trusts `@Immutable` on a parent as authoritative — nested `data class` fields inside it can drop their own annotation.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-18 · **Tags:** compose, stability, kotlin, k2
**Applies to:** Adding `@Immutable` to cross-module domain models (companion to L-2026-05-17-01)

When a data class `Parent` carries an `@Immutable` annotation, the Compose compiler does not descend into the types of `Parent`'s fields to verify their stability. Nested data classes used only inside an `@Immutable` parent (e.g. `Store.StoreImages` inside `@Immutable Store`, `GameDetails.GameInfo` inside `@Immutable GameDetails`) can drop their own `@Immutable` without any baseline flip. **L-2026-05-17-01 stands**: cross-module top-level types DO still need explicit `@Immutable` in Kotlin 2.3.21 — K2 inference has not improved enough to drop the outer annotation.

**Source:** PR #174 K2 inference cleanup — dropped 8 of 18 `@Immutable` annotations from PR #166 without any composable flipping to unstable

### L-2026-05-18-02 · Coil 3.x `AsyncImage(model = null)` logs `NullRequestDataException` even when a Painter `error`/`fallback` is set
**TL;DR:** Don't pass `null` to Coil 3.x `AsyncImage(model = …)` — guard the call site and render a placeholder `Image` instead.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-18 · **Tags:** coil, compose, logging, kmp
**Applies to:** Any `AsyncImage` call whose `model` is reached through `?.` chains (i.e. a value that may be null on the first composition before a ViewModel emits)

The Painter-level `error` and `fallback` parameters render the placeholder correctly, but the underlying `ImageRequest` still has `data = NullRequestData` and `RealImageLoader.execute()` throws `NullRequestDataException` internally. The exception is caught but surfaced to the `Logger`-wired `EventListener` — release-build logcat fills with the stack trace on every screen open until you gate the call site. Pattern: `val x = nullable?.path; if (x != null) AsyncImage(model = x, ...) else Image(painter = placeholder, ...)`.

**Source:** :feature:store StoreScreen banner bug — surfaced via R8'd release-build logcat smoke test

### L-2026-05-18-01 · Check the upstream's own `libs.versions.toml` to predict KLIB ABI compatibility for a KMP Gradle plugin
**TL;DR:** Before adopting a KMP Gradle plugin, read its own `libs.versions.toml` for the `kotlin = "…"` pin — that's the real klib ABI source.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-18 · **Tags:** kmp, gradle, klib, dependencies, kotlin
**Applies to:** Picking a version of a Gradle plugin that publishes KMP runtime klibs (iosArm64, etc.) for a project on a specific Kotlin version

A plugin's release notes claim a Kotlin version pin only for the *source*. Its published klibs may have been built with a different (usually newer) Kotlin compiler whose ABI version your project rejects (see L-2026-05-17-05). Before adopting, open the plugin's repository on GitHub and read `gradle/libs.versions.toml` at HEAD or the chosen tag — the `kotlin = "..."` line there is the actual ABI source of truth. Companion to L-2026-05-17-05: that lesson tells you to validate *after* the fact via an iOS-target compile; this one lets you predict *before* you spend the build cycle.

**Source:** PR #173 Compose Stability Analyzer 0.7.5 revival — picked 0.7.5 by reading upstream's own catalog (`kotlin = "2.3.21"`) at HEAD before adopting

### L-2026-05-17-16 · Local `connectedAndroidDeviceTest` needs `adb shell settings put global mdevx.grpc_guest_port 8554` for `androidx.test.espresso.device` to find the emulator gRPC service
**TL;DR:** After launching an AVD for `connectedAndroidDeviceTest` locally, run `adb shell settings put global mdevx.grpc_guest_port 8554` once.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** instrumented-tests, espresso-device, emulator, local-dev, ci-parity
**Applies to:** Running `connectedAndroidDeviceTest` locally on a standard Android-Studio-launched emulator (not via Gradle Managed Devices) when any test uses `androidx.test.espresso.device 1.1.0+`

The espresso-device API reads the emulator's gRPC control port from a `Settings.Global` key the test process expects to be set. AGP/UTP writes it when running tests on a Gradle Managed Device; for externally-launched emulators (the normal Android Studio AVD case) nothing writes it, and tests fail with `DeviceControllerOperationException: Unable to connect to Emulator gRPC port`. Workaround: run `adb shell settings put global mdevx.grpc_guest_port 8554` once after starting the emulator (modern emulators expose gRPC on 8554 by default). CI's `.github/workflows/android.yml` already does this. The previous AGP-8 era had `testOptions.emulatorControl.enable = true` + `android.experimental.androidTest.enableEmulatorControl=true` to bridge this for non-GMD emulators, but the AGP 9 KMP-library plugin doesn't expose `testOptions.emulatorControl` — so locally you set the system property manually until the new plugin grows an equivalent. Affects two screen-test suites in this repo: `:feature:game GameScreenTest`, `:feature:home HomeScreenTest`.

**Source:** chore/upgrade-kotlin-2.3-kmp · post-migration `connectedAndroidDeviceTest` run — 9 of 81 tests initially failed with the gRPC error, fixed by setting the property locally to match CI

### L-2026-05-17-15 · Under the AGP 9 KMP-library plugin, gate `withDeviceTestBuilder { }` on a real `src/androidDeviceTest/` directory
**TL;DR:** Gate `withDeviceTestBuilder { }` on `project.file("src/androidDeviceTest").exists()` so modules without tests skip the device-test pipeline.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** agp-9, kmp-library-plugin, convention-plugins, instrumented-tests, conditional-config
**Applies to:** Writing a convention plugin that applies `com.android.kotlin.multiplatform.library` to library modules where some have device tests and some don't (the typical pattern in a multi-module Android+KMP project)

If the convention plugin calls `withDeviceTestBuilder { }.configure { instrumentationRunner = "..." }` unconditionally, every library module produces a `connectedAndroidDeviceTest` task and a test APK. Modules with no test files still deploy that empty test APK to the device, and the device process crashes with `ClassNotFoundException: androidx.test.runner.AndroidJUnitRunner` because androidx-runner is typically only declared in feature/test convention deps. The clean fix is to gate `withDeviceTestBuilder` on `project.file("src/androidDeviceTest").exists()` so non-test modules silently skip the device-test pipeline. Paired downstream gate: any feature convention that does `getByName("androidDeviceTest").dependencies { ... }` must use the same condition, since the source set only exists when the library convention opted in. Empty `src/androidDeviceTest/` directories work as opt-in markers if you want a module to participate without tests yet.

**Source:** chore/upgrade-kotlin-2.3-kmp · `:common`, `:domain`, `:logging`, `:testing`, `:remote*`, `:feature:favourites` all hit the runner-class-not-found crash on first `connectedAndroidDeviceTest` run; gating fixed it without touching any module's build file

### L-2026-05-17-14 · Under the AGP 9 KMP-library plugin, Compose Multiplatform resources require `androidResources { enable = true }` explicitly
**TL;DR:** Set `androidResources { enable = true }` on every `com.android.kotlin.multiplatform.library` target, or Compose Resources fails.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** agp-9, kmp-library-plugin, compose-multiplatform, compose-resources, gradle-config
**Applies to:** Any KMP module that applies `com.android.kotlin.multiplatform.library` + Compose Multiplatform Resources (i.e. `org.jetbrains.compose` with `compose.components.resources`), which in this repo is every module applying `gamedeals.kmp.library.compose`

AGP 9's KMP-library plugin disables Android resource processing by default — different from `com.android.library`. Compose Multiplatform Resources expects the pipeline to be enabled; without it, the per-source-set `CopyResourcesToAndroidAssetsTask` is generated with no `outputDirectory` and Gradle fails configuration with `property 'outputDirectory' doesn't have a configured value`. The fix is `androidResources { enable = true }` inside the `kotlin.android { }` (or from a convention plugin, `targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach { androidResources { enable = true } }`). Apply it unconditionally in the base library convention plugin — modules that don't use Compose Resources just have an empty pipeline; cheap and uniform.

**Source:** chore/upgrade-kotlin-2.3-kmp · `:common:ui:copyAndroidDeviceTestComposeResourcesToAndroidAssets FAILED` on first `connectedAndroidDeviceTest` invocation; one-line convention plugin fix unblocked everything downstream

### L-2026-05-17-13 · Under the AGP 9 KMP-library plugin, dex-merge across many `androidDeviceTest` APKs OOMs at the 4GB default heap
**TL;DR:** Bump `org.gradle.jvmargs` to `-Xmx8192m` — AGP 9 KMP-library dex-merge across many androidDeviceTest APKs OOMs at the 4 GB default.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** agp-9, kmp-library-plugin, dex-merge, jvm-heap, gradle-properties, instrumented-tests
**Applies to:** Running `connectedAndroidDeviceTest` on a multi-module project (7+ library modules with device tests) after migrating to `com.android.kotlin.multiplatform.library`

The new plugin produces one `androidDeviceTest` test APK per library module that opts in. Gradle's parallel worker pool then runs `mergeExtDexAndroidDeviceTest` (D8) concurrently across those modules. At `-Xmx4096m` D8 reproducibly throws `java.lang.OutOfMemoryError: Java heap space` in `R8/D8.run` mid-merge, killing several tasks at once. Bump `org.gradle.jvmargs` in `gradle.properties` to at least `-Xmx8192m`. Note this affects new workstations too — the OOM only surfaces once you run instrumented tests across the whole module graph, not from compile-only or unit-test runs.

**Source:** chore/upgrade-kotlin-2.3-kmp · first `connectedAndroidDeviceTest` run with 4GB heap reproducibly OOM'd in 4 simultaneous `mergeExtDexAndroidDeviceTest` workers (`:common:ui`, `:feature:favourites`, `:feature:game`, `:feature:giveaways`); 8GB bump cleared it

### L-2026-05-17-11 · CMP 1.11 requires Kotlin 2.3 on iOS targets and removes `iosX64` (Apple x86_64) entirely
**TL;DR:** CMP 1.11 needs Kotlin 2.3 on iOS targets and removes `iosX64()` — drop it from every KMP target list and matching KSP per-target deps.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** compose-multiplatform, kotlin-native, ios, kmp, target-removal, upgrade
**Applies to:** Any bump of `org.jetbrains.compose` to 1.11.0+ in a KMP project with iOS targets, especially convention plugins that still declare `iosX64()`

Two coupled requirements: (1) Kotlin 2.3+ is mandatory for native/iOS targets when on CMP 1.11 — staying on Kotlin 2.2.x while bumping CMP fails to link; (2) `iosX64()` (Intel Mac simulator target) is no longer supported and must be removed from every KMP target list. Also drop any per-target processors that reference it, e.g. `add("kspIosX64", libs.room.compiler)`. Apple Silicon Mac simulators are covered by `iosSimulatorArm64`; real Apple Silicon devices by `iosArm64`. Keep an eye on `iosApp/build.gradle.kts` AND the convention plugin — both declare iOS targets independently in this repo.

**Source:** chore/upgrade-kotlin-2.3-kmp · linker failures on first CMP 1.11 + iOS compile pointed at the `iosX64()` declarations

### L-2026-05-17-10 · KSP 2.3.x writes to `kotlin.sourceSets` but AGP 9 built-in Kotlin disallows it — set `android.disallowKotlinSourceSets=false`
**TL;DR:** On AGP 9 + KSP 2.3.x, set `android.disallowKotlinSourceSets=false` in `gradle.properties` to unblock KSP's source-set registration.
**Status:** tentative · **Confidence:** confirmed for KSP 2.3.8 / AGP 9.1.1 · **Added:** 2026-05-17 · **Tags:** ksp, agp-9, kotlin-built-in, gradle-properties, workaround
**Applies to:** Any project on AGP 9.x + Kotlin built-in support (`android.builtInKotlin=true`, the AGP-9 default) where KSP 2.3.x is applied to an Android-target module

Configuration fails with `"Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin. Kotlin source set 'debug' contains: […]/build/generated/ksp/debug/…"`. KSP 2.3.x still uses the legacy mechanism to register its generated-source directories on the Kotlin source set; AGP 9's built-in Kotlin support rejects external writes. Workaround: set `android.disallowKotlinSourceSets=false` in `gradle.properties`. This is marked experimental by AGP and is intended as a transition flag — remove it when KSP migrates to `android.sourceSets`. Search: https://developer.android.com/r/tools/built-in-kotlin.

**Source:** chore/upgrade-kotlin-2.3-kmp · first failure after dropping `org.jetbrains.kotlin.android` from the application convention plugin

### L-2026-05-17-09 · AGP 9 KMP-library is single-variant — `debugImplementation`, `buildTypes.release { proguardFiles }`, and `buildFeatures.buildConfig` are all gone
**TL;DR:** AGP 9 KMP-library is single-variant — `debugImplementation`, library `buildTypes.release { proguardFiles }`, and `buildConfig` are gone.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** agp-9, kmp-library-plugin, build-variants, compose-tooling, buildconfig, proguard
**Applies to:** Any module migrated from `com.android.library` to `com.android.kotlin.multiplatform.library` under AGP 9.1+

The new plugin produces one variant per module; everything keyed on `debug`/`release` disappears: (a) `debugImplementation` is unresolved — move Compose `ui-tooling` to `androidMain.dependencies { implementation(...) }` (slight release bloat, accept it) and `ui-test-manifest` to `androidDeviceTest.dependencies { implementation(...) }`. (b) `buildTypes.named("release") { isMinifyEnabled = false; proguardFiles(...) }` is not supported on library modules — drop it; rely on `:app`'s proguard config or `consumer-proguard-rules.pro`. (c) `buildFeatures.buildConfig = true` does not generate a `BuildConfig` class — replacements: BuildKonfig plugin (proper), inject the constant from `:app` via Koin (cleaner), or hardcode + accept the regression (what this repo did for `RemoteBuildUtil.android.kt` as a temporary fix). `:app` itself (still `com.android.application`) keeps BuildConfig and variants — applications are unchanged.

**Source:** chore/upgrade-kotlin-2.3-kmp · `:common:ui` failed `debugImplementation` resolution; `:remote` lost `BuildConfig.BUILD_TYPE` access; central convention plugin needed both fixes

### L-2026-05-17-08 · AGP 9 KMP-library plugin renames test source sets AND Gradle test tasks — propagates to CI
**TL;DR:** AGP 9 KMP-library renames `androidUnitTest`→`androidHostTest` and `androidInstrumentedTest`→`androidDeviceTest` — update files & CI.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** agp-9, kmp-library-plugin, source-sets, gradle-tasks, ci, instrumented-tests
**Applies to:** Every KMP module migrated to `com.android.kotlin.multiplatform.library` and every Gradle task name referenced from CI, scripts, or docs

Source-set names: `androidUnitTest` → `androidHostTest`; `androidInstrumentedTest` → `androidDeviceTest`. This means three coordinated changes per module: (a) `val androidUnitTest by getting { ... }` → `val androidHostTest by getting { ... }` in every `build.gradle.kts`; (b) `getByName("androidUnitTest")` / `getByName("androidInstrumentedTest")` in convention plugins → the new names; (c) `git mv src/androidUnitTest src/androidHostTest` (and the instrumented equivalent) on the filesystem. Task names also shift: `testDebugUnitTest` → `testAndroidHostTest` (no debug/release library variants), `connectedDebugAndroidTest` → `connectedAndroidDeviceTest`, with `allTests` as the cross-target aggregator (commonTest + androidHostTest + iosSimulatorArm64Test). Update `.github/workflows/android.yml` (this repo: the `connectedDebugAndroidTest` invocation) and any developer docs that mention the old names.

**Source:** chore/upgrade-kotlin-2.3-kmp · first surfaced as `"KotlinSourceSet with name 'androidUnitTest' not found"` at `:common:build.gradle.kts:33` configuration time; CI workflow needed a paired edit

### L-2026-05-17-07 · `com.android.kotlin.multiplatform.library` DSL accessor: `android {}` from build scripts, `targets.withType<KotlinMultiplatformAndroidLibraryTarget>` from convention plugins
**TL;DR:** Build scripts: `android {}`. Convention plugins: `targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach { … }`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** agp-9, kmp-library-plugin, convention-plugins, dsl, kotlin-multiplatform
**Applies to:** Writing or migrating precompiled convention plugins in `build-logic` for projects on AGP 8.12+ that use the new `com.android.kotlin.multiplatform.library` plugin

Per Android dev docs: the configuration block was called `androidLibrary {}` in AGP < 8.12.0; AGP 8.12.0 introduced `android {}` as the replacement and deprecated `androidLibrary {}`. Use `android {}` going forward. But the auto-generated DSL accessors only exist in project-level build scripts — from a *precompiled* convention plugin in `build-logic` neither name resolves at compile time. The supported access pattern from a plugin is via the typed AGP API: `extensions.configure<KotlinMultiplatformExtension> { targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { /* namespace, compileSdk, minSdk, withHostTestBuilder { }, withDeviceTestBuilder { sourceSetTreeName = "test" }.configure { instrumentationRunner = "..." } */ } }` with `import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget`. Note `withDeviceTestBuilder { ... }.configure { ... }`: `instrumentationRunner` lives on the post-builder `configure` block, not inline. Sample to cross-check the API surface against a known-good project: `android/kotlin-multiplatform-samples/Fruitties/shared/build.gradle.kts`.

**Source:** chore/upgrade-kotlin-2.3-kmp · two failed compile attempts (one each on `androidLibrary { }` and `android { }`) before discovering the typed-API path via `developer.android.com/kotlin/multiplatform/kmp-integration`. The repo's older `docs/kotlin-2.3-upgrade-findings.md` recommended `androidLibrary { }` and was outdated.

### L-2026-05-17-05 · Validate KMP-targeted Gradle plugins on an iOS-target compile task before pushing — Android-only smoke tests miss KLIB ABI mismatches
**TL;DR:** Before pushing a KMP Gradle plugin, run an iOS-target compile (`:…:compileKotlinIosArm64`) — Android smoke tests miss klib ABI skew.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** kmp, gradle, kotlin-native, klib, ci
**Applies to:** Adopting any third-party Gradle plugin that publishes a multiplatform runtime (e.g. a `*-runtime-iosArm64` klib alongside the JVM/Android artifact) into a Kotlin Multiplatform project — especially when the plugin's release notes claim a Kotlin-version pin

A plugin's "Kotlin 2.2 language level" pin in release notes only covers the **Gradle plugin source**, not the published runtime KLIBs. The iOS klibs may have been built with a newer Kotlin compiler and carry a higher ABI version that the project's Kotlin/Native compiler refuses to consume (`KLIB resolver: Skipping … having incompatible ABI version`). The Android-only `:app:<task>Dump` and `./gradlew :app:assembleDebug` paths can pass because the JVM artifact has no ABI mismatch — the failure only surfaces when any iOS-target task runs (`compileKotlinIosArm64`, `linkPodDebugFramework*`, etc.). **Before pushing**, run at least one explicit iOS-target compile task (e.g. `./gradlew :common:ui:compileKotlinIosArm64`); don't trust the Android smoke alone. Confirmed against `compose-stability-analyzer` 0.7.1 (PR #167, reverted) — its iOS klibs were Kotlin-2.3.20-built despite the source-pin to 2.2.

**Source:** PR #167 (closed) — compose-stability-analyzer integration attempt

### L-2026-05-17-06 · Gradle 9.x rejects implicit compile-output reads; wire missing `dependsOn` in a convention plugin as a workaround
**TL;DR:** Wire missing `dependsOn(compileXxxKotlin)` in a convention plugin to satisfy Gradle 9's strict task validator for third-party plugins.
**Status:** active · **Confidence:** tentative · **Added:** 2026-05-17 · **Tags:** gradle, kotlin-compile, task-validation, convention-plugin
**Applies to:** Adopting a third-party Gradle plugin whose tasks read other tasks' outputs (typically `compileXxxKotlin[Android]`) without declaring the dependency — Gradle 9.x's task validator hard-fails this with `Task ':X' uses this output of task ':Y' without declaring an explicit or implicit dependency`

The failure typically surfaces only when both tasks run in the same graph — e.g. `./gradlew build` triggers `check` → both the plugin's `xxxCheck` task and the unit-test compile. Standalone invocations of the plugin task may pass. Workaround: declare the missing edge in a convention-plugin helper:
```kotlin
internal fun Project.wireXxxTaskDependencies() {
    tasks.matching { /* the plugin's tasks */ }.configureEach {
        dependsOn(tasks.matching { /* the compile tasks it reads */ })
    }
}
```
Call from each convention plugin that applies the upstream plugin. Mark the helper file with a clear "remove when upstream fixes input declaration" comment + reference to a tracking issue. Marked tentative because seen only against `compose-stability-analyzer` 0.7.1 so far; the underlying pattern (third-party plugin missing input declarations + Gradle 9.x strict validator) is likely to recur.

**Source:** PR #167 (closed) — compose-stability-analyzer integration attempt

### L-2026-05-17-01 · K2 Compose plugin (Kotlin 2.2.21) does NOT auto-infer cross-module data classes as stable
**TL;DR:** K2 in Kotlin 2.2.21 doesn't infer cross-module `data class` stability — annotate cross-module domain models with `@Immutable` explicitly.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** compose, stability, recomposition, kmp, kotlin-2-2, k2
**Applies to:** Any `data class` defined in a module that does not apply the Compose compiler plugin (e.g. `:domain` is pure KMP) and is consumed as a parameter — directly or transitively — by a `@Composable` in a feature module

The Compose stability classifier treats cross-module data classes as `Stability.Unstable` regardless of how structurally stable they are (all-`val` primitives/`String`/enums + `ImmutableList`). The compiler reads the upstream class metadata but won't infer stability across compilation boundaries without an explicit signal. Verified empirically against `:domain` models in this repo (`Deal`, `Store`, `Release`, etc. all reported `unstable` from every consumer feature module before annotation) and against the skydoves/compose-stability-inference reference doc: *"For classes from external modules: separately compiled… Result: Stability.Unstable."* This is the load-bearing reason why `L-2026-05-02-02` ("@Immutable on every domain model used as a composable parameter") still needs to be applied even though K2 added cross-module inference improvements — those improvements don't cover this case in 2.2.21. May change in a future Kotlin release; check the Compose compiler reports before assuming.

**Source:** PR #166 — Compose stability retype + metrics

### L-2026-05-17-02 · `@Immutable` is strictly stronger than a `stability_config.conf` entry — prefer it when feasible
**TL;DR:** Prefer `@Immutable` over `stability_config.conf` — only `@Immutable` enables Compose lambda memoization and static-expression detection.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** compose, stability, recomposition, lambda-memoization, annotations
**Applies to:** Deciding between (a) annotating a model `@Immutable` (with the Compose-runtime dep cost on the module that owns the class) and (b) listing the class in a `stability_config.conf` / `composeCompiler.stabilityConfigurationFiles` entry

Both paths flip the class to `Stability.Stable` so per-row composables become `@Skippable`. Only `@Immutable` additionally enables Compose's **lambda memoization** and **static expression detection** optimizations, per the skydoves/compose-stability-inference reference. For per-row patterns like `onClick = { onLoadDealDetails(deal.dealID, …) }` — where the lambda captures a model and runs N times per frame inside `LazyColumn` — that extra optimization is a real win, not a theoretical one. Cost of `@Immutable`: one `implementation(libs.compose.runtime)` line on the model's module (KMP-safe; iOS-compatible via `org.jetbrains.compose.runtime:runtime`). Reach for the config file only when adding the Compose runtime to that module is infeasible (legacy library, source you don't own, etc.).

**Source:** PR #166 — choosing between strategies during plan-mode

### L-2026-05-17-03 · Stability-fix verification needs `--rerun-tasks` (or per-module `build/compose-reports/` cleanup)
**TL;DR:** Verifying a Compose stability fix needs `--rerun-tasks` (or `rm -rf */build/compose-reports/`) — incremental compile skips regen.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** compose, stability, gradle, incremental-compile, ci, verification
**Applies to:** Verifying a Compose stability change by re-reading `<module>/build/compose-reports/*-classes.txt` / `*-composables.txt` after annotating or retyping an upstream `:domain` type

When the only change to an upstream module is a `@Immutable` annotation (no behavioural change), Gradle's incremental compile marks downstream feature modules' `compileDebugKotlinAndroid` as UP-TO-DATE — and the compose-reports inside those modules are NOT regenerated. You'll then read stale "unstable" verdicts on the very classes you just fixed and incorrectly conclude the fix didn't work. Force regeneration with `./gradlew :app:assembleDebug -Pgamedeals.composeReports=true --rerun-tasks` (project's report-gate property) or `rm -rf */build/compose-reports/`. Spend the extra 30s; otherwise you'll waste 10 minutes hunting a ghost.

**Source:** PR #166 — caught a false-negative diff during Step 9 verification

### L-2026-05-17-04 · `./gradlew testDebugUnitTest` does NOT compile `androidInstrumentedTest` sources — CI will surface the gap
**TL;DR:** Run `./gradlew compileDebugAndroidTestKotlinAndroid` before signature sweeps — `testDebugUnitTest` skips the instrumented-test source set.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** testing, instrumented-tests, gradle, ci, signature-change
**Applies to:** Any change that alters a public Composable parameter type or ViewModel `StateFlow` shape — i.e. anything that would force a test fixture or mockk stub to update

`testDebugUnitTest` only compiles `commonTest` + `androidUnitTest` source sets. `androidInstrumentedTest` (the Compose UI-test source set, where `every { viewModel.foo } returns MutableStateFlow(...)` stubs and `setupCompose(...)` literals live) compiles only under `compileDebugAndroidTestKotlinAndroid` or as part of the Instrumented UI Tests CI job. A signature-changing sweep that updates commonTest/androidUnitTest will look locally green and fail at CI compile in the four screens that have instrumented tests (Home/Search/Store + DealBottomSheet). Before pushing a stability/retype sweep, run `./gradlew compileDebugAndroidTestKotlinAndroid` locally as a separate gate.

**Source:** PR #166 — CI red after the first push; four `MutableStateFlow(emptySet())` / `listOf(...)` literals in instrumented-tests had been missed

### L-2026-05-15-08 · When a bug-hunt finding's error path is practically unreachable, drop the fix or relabel — don't infrastructure-wrap a dead branch
**TL;DR:** Before adding retry infrastructure for a bug-hunt finding, check the error path can actually fire — Room observation flows rarely throw.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** scope, bug-hunt, error-handling, viewmodel, refactor
**Applies to:** Implementing a `severity:low`/`severity:medium` bug-hunt finding that recommends adding retry/recovery infrastructure to an error path — before adding `retryTrigger` + `flatMapLatest` + hoisted `onRetry` lambdas + preview proliferation, ask whether the error path can realistically fire

Before implementing the literal "option (a): add a retry method" route for a bug-hunt finding on an error UX path, sanity-check that the underlying flow actually errors in practice. Pure Room observation flows (`observeFavourites`, `observeStoreDeals` etc.) over a local SQLite DB rarely throw outside of DB corruption — the ERROR snackbar branch may be unreachable. In that case, prefer (b): relabel the misleading action ("Retry" → "Back" when the handler actually calls `onBack`) or drop the action entirely. Reserve the full retry-trigger infrastructure for flows that legitimately fail — network fetches, one-shot HTTP calls inside an error-prone repository method. Asymmetric error handling between two screens is fine when it reflects reality.

**Source:** Issue #145 / PR #159 — first pass added a full `retryTrigger`-driven retry to both StoreViewModel (legitimate: contains a one-shot `getStoreDetails` HTTP call) and FavouritesViewModel (unreachable: only observes a Room flow). User pushback led to dropping the Favourites half (revert VM + test to dev; relabel snackbar action to "Back" reusing existing string); kept Store's retry. Net diff fell from ~174 LoC to ~92 LoC.

### L-2026-05-15-07 · `Dispatchers.IO` is internal on Kotlin/Native in every source set — commonMain AND iosMain
**TL;DR:** `Dispatchers.IO` is `internal` on Kotlin/Native — in `iosMain` use `Dispatchers.Default` or inject the dispatcher per platform.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** kmp, coroutines, kotlin-native, dispatchers, ios
**Applies to:** any K/N-targeting source set that wants an IO-tagged dispatcher — commonMain, iosMain, macosMain, etc.

`kotlinx.coroutines.Dispatchers.IO` is declared `internal` to the coroutines library on Kotlin/Native — only the JVM source set exposes it publicly. Referencing it from `iosMain` fails to compile with `Cannot access 'val IO: CoroutineDispatcher': it is internal`, identical to the error in commonMain. The correct substitute is `Dispatchers.Default` (which K/N's `IO` would alias to internally anyway). Either inject a `CoroutineDispatcher` from each platform's DI module (`Dispatchers.IO` on Android, `Dispatchers.Default` on iOS), or use `Dispatchers.Default` literally in platform-specific Apple source sets.

**Source:** Issue #146 / PR #158 — Room `setQueryCoroutineContext` fix. Sub-agent followed the older lesson's wording, tried `Dispatchers.IO` in `iosMain`, hit the internal-access compile error, switched to `Dispatchers.Default`.

### L-2026-05-15-06 · Room `@Transaction suspend fun` default methods on `interface` DAOs work cleanly on Room 2.8.x KMP — both Android and iOS KSP
**TL;DR:** Use `@Transaction suspend fun` default methods on `interface` DAOs for atomic RMW — Room 2.8.x KMP accepts them on Android and iOS.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** room, kmp, ksp, dao, transaction, atomicity, sqlite
**Applies to:** Any `FooDao` `interface` in `:domain` that needs atomic read-modify-write (e.g. toggle, conditional-insert, upsert with a side check) — and would otherwise tempt you into a non-atomic `.first()` + `add()` shape in the repository

In Room 2.8.x (KMP, with `androidx.sqlite`), KSP cleanly accepts `@Transaction suspend fun` declared as a Kotlin `default` method on an `interface` DAO — and the same code path generates correctly for `kspDebugKotlinAndroid` and `kspKotlinIosSimulatorArm64`, no special configuration. This is the in-house idiom for atomic read-modify-write: e.g. an `EXISTS`-style `suspend fun isXxxNow(id): Boolean` plus a `@Transaction suspend fun toggleXxx(...)` default that wraps `if (isXxxNow(id)) delete(id) else insert(...)`. Repo simply delegates to the new DAO method — its public signature stays unchanged, no DI plumbing changes, and existing Mokkery test stubs need only swap from `every { dao.observeXxx(...) }` chains to `everySuspend { dao.toggleXxx(...) } returns <bool>`.

One operational note: when pushing logic from the repo into a DAO, factor any wall-clock parameter (`dateAddedMs: Long`, etc.) into the DAO method's signature and let the repo pass `clock.nowMillis()`. DAOs shouldn't depend on `Clock`; this keeps the deterministic-clock contract for tests intact.

**Source:** Issue #150 / PR #161 — `FavouritesRepository.toggleFavourite` previously did `observeIsFavourite(gameId).first()` + `add`/`remove` (TOCTOU). Replaced with `FavouritesDao.toggleFavourite` `@Transaction` default method; KSP accepted on both Android and iOS without complaint.

### L-2026-05-15-05 · `Pair<A, B>` in a Compose parameter type is unstable regardless of `ImmutableList` wrapping
**TL;DR:** Replace `Pair<A, B>` in any Compose parameter with a small named `@Immutable data class` — `Pair` is unstable even inside `ImmutableList`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** compose, stability, recomposition, immutable, pair, kotlinx-collections-immutable
**Applies to:** Any `@Composable` whose parameter type is `List<Pair<A, B>>` or `ImmutableList<Pair<A, B>>` (including types nested inside `@Immutable data class` UI-state holders consumed by a composable)

`kotlin.Pair` carries no `@Immutable` / `@Stable` annotation, so the Compose compiler classifies any `Pair<A, B>` as unstable — regardless of how stable `A` and `B` individually are. Wrapping in `kotlinx.collections.immutable.ImmutableList<...>` doesn't rescue the parameter because the element type itself still makes the list-as-parameter unstable for the compiler's skippability analysis. Fix: introduce a small named `@Immutable data class FooBarPair(val foo: A, val bar: B)` per Pair shape, in the closest module where the consumer lives. Bonus: `.first/.second` become `.foo/.bar` at call sites, which is easier to read. Refines `L-2026-05-02-02` (`@Immutable + ImmutableList on every domain model used as a composable parameter`) to cover the element-type case — that lesson catches the *collection* type; this one catches the *element* type.

**Source:** Issue #147 / PR #163 — `GameViewModel.dealDetails: ImmutableList<Pair<Store, GameDetails.GameDeal>>` was claimed "correctly typed" in closed #80 but actually still defeated skipping. Replaced with `ImmutableList<StoreDealPair>` (`@Immutable data class StoreDealPair(val store: Store, val deal: GameDetails.GameDeal)`); same pattern applied to `DealBottomSheetData.cheaperStores` via `StoreCheaperStorePair`.

### L-2026-05-15-04 · `SharingStarted.Eagerly` → `WhileSubscribed(5_000)` swap on a VM's `uiState` is NOT test-transparent
**TL;DR:** Swapping `Eagerly` for `WhileSubscribed(5_000)` on a VM `uiState` is not test-transparent — emission counts and trigger order shift.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** testing, stateflow, sharingstarted, viewmodel, coroutines-test, mokkery, eagerly, whilesubscribed
**Applies to:** Any future migration of a ViewModel's `uiState = upstream.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)` to `SharingStarted.WhileSubscribed(5_000)` — relevant when the project's `Eagerly` outliers are aligned with the prevailing convention

Tests authored against `Eagerly`'s "upstream is primed before the test collector subscribes" semantics break in two specific ways under `WhileSubscribed`:

1. **Emissions-count assertions invert.** Tests doing `assertEquals(1, emissions.size)` + `emissions.first()` become wrong: `WhileSubscribed` emits the placeholder `initialValue` *before* the upstream's first real emission lands, giving 2 emissions. Switch to `emissions.last()` and drop the `size == 1` assertion entirely.
2. **Imperative-trigger-before-subscribe stops working.** Tests that call `viewModel.someTrigger()` *before* the collector subscribes (`observeStates(viewModel)`) observe nothing because there's no subscriber yet — with `Eagerly` the upstream was already collecting, so the trigger's intermediate states (LOADING, etc.) made it into the StateFlow's replay slot. Under `WhileSubscribed`, no subscriber means those writes are dropped. Fix: reorder to **subscribe first**, then call the trigger. If the underlying suspending work is a fast-returning Mokkery stub, also gate it with a `CompletableDeferred` so the success transition doesn't race the assertion and clobber the LOADING state before the test observes it.

In the Giveaways migration, 5 of 11 existing tests required adjustment under one or both patterns. Budget the test rewrites accordingly when planning the migration of remaining `Eagerly` ViewModels (currently `FavouritesViewModel` is the only one left — deliberately, per the issue body).

**Source:** Issue #149 / PR #162 — switched `GiveawaysViewModel.uiState` from `Eagerly` to `WhileSubscribed(5_000)`; broke 5 tests, all fixed via the two patterns above.

### L-2026-05-15-03 · `actual` implementations must agree on whether system state is read live per call or cached
**TL;DR:** Where one platform's `actual` reads live OS state, the other's must too — default `expect`/`actual` pairs to per-call reads, never cached.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** kmp, expect-actual, platform-parity, system-state, locale, timezone
**Applies to:** Any `expect`/`actual` pair where the function reads OS-provided state (locale, timezone, theme, network status, screen metrics) — the `actual`s on different platforms must agree on the read strategy.

If one platform's `actual` reads live OS state on every call (e.g. iOS `formatLocaleAwareDate` reads `NSLocale.currentLocale` / `NSTimeZone.systemTimeZone` per call), the other platform's `actual` cannot cache that state at class-load — otherwise behaviour diverges the moment the user changes the setting in OS Settings: iOS picks it up immediately, Android stays on the boot-time value until process death. **Default to per-call reads** unless the constructor cost is provably expensive AND the state is immutable for the process lifetime. The per-call allocation cost of building a fresh formatter is negligible for typical UI usage (a handful of formatted dates per screen).

**Source:** Issue #143 / PR #154 — Android `formatLocaleAwareDate` built a `private val DateTimeFormatter` once with `Locale.getDefault()` + `ZoneId.systemDefault()` at class load while the iOS actual read both per call. Fix: construct the formatter inside the function on each call.

### L-2026-05-15-02 · `CGRectMake` and `cValue<T>.useContents { ... }` require file-level `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)`
**TL;DR:** Add `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` whenever `iosMain` uses `CGRectMake`/`useContents` or cinterop APIs.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** kotlin-native, cinterop, opt-in, ios, uikit
**Applies to:** Any K/N `iosMain` code that builds a C value via the `*Make` helpers (`CGRectMake`, `CGPointMake`, `CGSizeMake`), reads from a `cValue<T>` via `useContents { ... }`, or otherwise touches the `kotlinx.cinterop` foreign-API surface in current Kotlin/Native (2.2.x).

These APIs are gated behind `kotlinx.cinterop.ExperimentalForeignApi`. Without an explicit opt-in, the build fails with `Calling '...' may have an unintended effect: ...` or `This declaration is experimental and its usage should be marked with '@OptIn(...)' or '@kotlinx.cinterop.ExperimentalForeignApi'`. Apply the opt-in at the file level (`@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` near the imports) rather than per-call site. Will likely become non-experimental in a future K/N release.

**Source:** Issue #144 / PR #155 — `IosPlatformActions.share` configures `popoverPresentationController.sourceRect` via `CGRectMake(...)` and reads `keyWindow.bounds.useContents { ... }`; first compile failed on the opt-in until a file-level `@file:OptIn` was added.

### L-2026-05-15-01 · K/N exposes Objective-C **category** properties as top-level extension properties, not as members
**TL;DR:** ObjC category properties (e.g. `popoverPresentationController`) are bridged as top-level Kotlin extensions — add an explicit `import`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-15 · **Tags:** kotlin-native, ios, uikit, objc-interop, imports
**Applies to:** K/N `iosMain` code reading any property that Apple defined via an Objective-C category on a UIKit (or Foundation) class — common examples include `UIViewController.popoverPresentationController`, `UIView.safeAreaInsets`, or anything from `UIKit/UIView+Additions.h`-style headers.

Regular Objective-C members on a class are bridged as Kotlin member access (`view.bounds`, no extra import needed). Properties added via ObjC **categories**, however, are bridged as top-level Kotlin extension properties — accessing them requires an explicit import. Example: `viewController.popoverPresentationController` compiles only if you add `import platform.UIKit.popoverPresentationController` at the top of the file. The compile error doesn't obviously point at the missing import (it looks like the property doesn't exist on the type); know the pattern to recognise it.

**Source:** Issue #144 / PR #155 — `UIActivityViewController.popoverPresentationController?.sourceView` failed to compile until `import platform.UIKit.popoverPresentationController` was added. `sourceView` and `sourceRect` on `UIPopoverPresentationController` itself are regular members and needed no import.

### L-2026-05-14-02 · Per-test-class `ScreenSemantics` + `setupCompose` in instrumented UI tests
**TL;DR:** In `*ScreenTest.kt`, hoist `stringResource` lookups into a per-class `ScreenSemantics` and wrap `setContent` in `setupCompose(...)`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-14 · **Tags:** testing, compose, instrumented, boilerplate
**Applies to:** Any new instrumented `*ScreenTest.kt` with ≥2 string-resource lookups or ≥2 `setContent` blocks

Don't repeat `composeTestRule.setContent { GameDealsTheme { Screen(...) } }` per test, and don't sprinkle `var x = ""` then-reassign-inside-`setContent` per resource lookup. Instead: declare a private nested `data class ScreenSemantics(...)` with a `@Composable fun load(): ScreenSemantics` companion factory that resolves every static `stringResource(Res.string.X)` the suite needs as `val` fields. Parameterised CDs (anything formatted with runtime data) become `@Composable` companion methods (`fun dealRowCd(title: String, price: String): String`). Add a `lateinit var screenSemantics: ScreenSemantics` field and a `private fun setupCompose(...)` wrapping the `setContent` block; inside the lambda, assign `screenSemantics = ScreenSemantics.load()`. Tests then read `screenSemantics.foo` and call `setupCompose()` with default-valued overrides (`onBack`, `onClick`, etc.) — bodies shrink to mock-stub setup + `setupCompose()` + assertions. MockK stubs that fire on first composition must still be set before `setupCompose()`. Parameterised CDs use a class-level `var` captured inside `setupCompose` (because their values depend on test fixture data); don't try to fold them into `ScreenSemantics.load()`'s constructor.

Full pattern with code samples and pointers to all six refactored test files lives in `docs/patterns/ui-testing.md` under "Per-Test-Class `ScreenSemantics` + `setupCompose`".

**Source:** May 2026 — applied across `:feature:game`, `:feature:search`, `:feature:store`, `:feature:giveaways`, `:feature:home`, and `:common:ui`'s `DealBottomSheetTest` after the testTag refactor surfaced the duplication. Pattern shape borrowed from a sibling project's `CoachingWidgetTest`.

### L-2026-05-14-01 · Find Compose nodes by visible text or content description, never `testTag`
**TL;DR:** Never use `testTag` in production or tests — find nodes by `onNodeWithText`, `onNodeWithContentDescription`, or role matchers.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-14 · **Tags:** testing, compose, instrumented, accessibility, content-description
**Applies to:** Any new `@Composable` screen, any new instrumented `*ScreenTest.kt`, and any edit that adds a clickable surface or unlabeled control to existing screens

`testTag` is forbidden on production composables and as a finder in tests. The finder hierarchy is: (1) `onNodeWithText(stringResource(...))` for elements that already render user-visible copy; (2) `onNodeWithContentDescription(stringResource(...))` for icons/images/sliders/switches/spinner — adding `Modifier.semantics { contentDescription = stringResource(...) }` only on leaf or semantic-bearing nodes (never on wrapper `Column`/`Box`/`ModalBottomSheet` — Compose merges descendants into the parent, masking children for TalkBack); (3) `clickable(role = Role.Button) { ... }` on Card/Row/Box tap surfaces (production code, not test plumbing) + `hasContentDescription(...) and hasRole(...)` matchers in tests. Define `fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)` locally in the test file until a second consumer appears. `stringResource(...)` is `@Composable`, so tests resolve resources during composition via the per-class `ScreenSemantics` capture pattern (see L-2026-05-14-02) rather than scattering `var x = ""` placeholders. Skip extension-function helpers (`tapBack()`, `assertX()`) for per-feature tests with <10 methods — inline is clearer; promote to a shared source set only when a second test file imports them.

Full policy with examples, "Seen in" pointers, and rationale lives in `docs/patterns/ui-testing.md`. Cross-referenced from `docs/patterns/testing.md`.

**Source:** May 2026 — codebase-wide campaign to remove `testTag`. First pass landed `:feature:game`, `:feature:search`, `:feature:store`, `:feature:giveaways`, and the `:app` journey test after trialling four approaches; the policy then covered `:feature:home`, `:feature:favourites`, and `:common:ui`'s `DealBottomSheet` in the same branch. As of 2026-05-14 no production composable or instrumented test in this codebase carries a `testTag`.

### L-2026-05-13-02 · Preview the modal-sheet body inside `Surface`, not via `ModalBottomSheet`
**TL;DR:** Preview a `ModalBottomSheet`'s body composable directly inside `Surface { … }` — `ModalBottomSheet` itself renders blank in `@Preview`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-13 · **Tags:** compose, preview, material3, modal-bottom-sheet
**Applies to:** Any feature with a filter/detail UI built on `ModalBottomSheet` (giveaways `Filters`, search `Filters`, `DealBottomSheet` in `:common:ui`)

`ModalBottomSheet` doesn't drive its sheet-state machine in static previews, so a `@Preview` that wraps a sheet renders blank or just the scrim. Factor the sheet's body into a separately-named composable (e.g. `Filters`, `DealContent`) and preview that directly inside a `Surface(color = MaterialTheme.colorScheme.surface) { ... }`. The wrapping `ModalBottomSheet` composable still exists for production code; previews just call the body composable straight.

**Source:** Phase preview-enablement — giveaways, search, deal

### L-2026-05-13-01 · In commonMain previews, use the JetBrains-named `@Preview` import — not the AndroidX-named one the deprecation warning recommends
**TL;DR:** In commonMain previews use the JetBrains `Preview` import + `@file:Suppress("DEPRECATION")` — the AndroidX one won't resolve on iOS.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-13 · **Tags:** compose, kmp, preview, jetbrains-compose, deprecation
**Applies to:** Any `@Preview`-annotated composable placed in `commonMain` (i.e. the standard pattern in this project)

Use `import org.jetbrains.compose.ui.tooling.preview.Preview` with `@file:Suppress("DEPRECATION")` at the top of the file. The CMP-1.10 deprecation message recommending `androidx.compose.ui.tooling.preview.Preview` is correct only when the file is in `androidMain` — the AndroidX-qualified import does not resolve on the iOS commonMain compile (the iOS variant of `org.jetbrains.compose.ui:ui-tooling-preview` doesn't expose that package). Following the deprecation hint blindly breaks the iOS build.

**Source:** Phase preview-enablement, all 6 feature screens + `:common:ui` `DealBottomSheet`

### L-2026-05-11-05 · New collected VM `StateFlow` requires updating MockK stubs in instrumented `*ScreenTest.kt`
**TL;DR:** Adding a collected `StateFlow` to a ViewModel breaks strict MockK in `*ScreenTest.kt` — backfill `every { vm.newFlow } returns …` stubs.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-11 · **Tags:** testing, mockk, instrumentation, viewmodel, compose
**Applies to:** Changes that add a `StateFlow`/`SharedFlow` property to a ViewModel that an existing Composable will `collectAsStateWithLifecycle()`; matters for `androidInstrumentedTest` `*ScreenTest.kt` files that mock the VM via strict MockK

Adding `val newFlow: StateFlow<...>` to a VM and reading it in a Composable will throw `MockKException: no answer found` on first composition in any instrumented test that constructs the VM via `mockk<MyViewModel>()` (strict by default — unlike Mokkery's `MockMode.autoUnit`, MockK does not silently no-op). Unit tests don't catch it because they never compose. After adding a new collected flow on a VM, grep `*/androidInstrumentedTest/**/*ScreenTest.kt` for the VM type and add `every { viewModel.newFlow } returns <StateFlow>` in each test's `@Before` setup. The instrumentation suite only runs on a device/emulator, so the break can land in `dev` for weeks unnoticed.

**Source:** Favourite Games — `StoreScreenTest` latent break discovered while wiring (and later reverting) swipe-to-favourite

### L-2026-05-11-04 · For `.catch`-recovery tests, mock upstream as `flow { throw … }`, not `every { … } throws`
**TL;DR:** To test a Flow consumer's `.catch` recovery, return `flow { throw … }` from the Mokkery stub — not `every { … } throws`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-11 · **Tags:** testing, mokkery, flow, kmp
**Applies to:** Any test that needs to verify a `Flow` consumer's `.catch { emit(default) }` recovery branch

Mokkery's `every { repo.observeXxx() } throws Exception()` makes the **function call itself** throw at construction — before any `Flow` object exists, so `.catch` never sees it. The VM's `_state.catch { emit(empty) }` only catches in-stream errors. Correct shape: `every { repo.observeXxx() } returns flow { throw Exception() }`. The `flow { ... }` builder defers the throw to collection time, where the `.catch` operator is in scope. Same applies to `everySuspend` for suspend functions returning `Flow`. This is identical mechanics to the production hot/cold-source distinction documented in L-2026-05-02-05 — failure-injection sites must match where the production code's recovery operator runs.

**Source:** Favourite Games — `FavouritesViewModelTest` ERROR case and `favouriteIds`/`favourites` recovery tests in Home/Store VM tests

### L-2026-05-11-03 · Mokkery `MockMode.autoUnit` only auto-stubs Unit-returning calls
**TL;DR:** Mokkery's `MockMode.autoUnit` no-ops only Unit returns — stub every non-Unit call with `everySuspend { … } returns <default>`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-11 · **Tags:** testing, mokkery, kmp, commontest
**Applies to:** Any `mock(MockMode.autoUnit)` in `commonTest` whose subject has non-Unit methods that the system-under-test invokes

`MockMode.autoUnit` silently returns Unit for unstubbed Unit-returning calls but throws `CallNotMockedException` for **any** non-Unit return type (`Boolean`, `Flow<T>`, `Int`, anything else) — including suspend functions launched fire-and-forget inside `viewModelScope.launch { ... }`. The fire-and-forget shape means the exception surfaces *after* the test thinks it's done, so the failure message can point at the wrong line. For repository methods that return values (e.g. `toggleFavourite(...): Boolean`), add an explicit `everySuspend { … } returns <default>` stub even when the test doesn't care about the return value — the goal is just to keep the launch from throwing.

**Source:** Favourite Games — test backfill for `toggleFavouriteFromDeal`

### L-2026-05-11-02 · Compose host bootstrap must be applied in BOTH `:app:MainActivity` AND `:iosApp:MainViewController`
**TL;DR:** Any new Koin module or `CompositionLocalProvider` must be wired in BOTH `:app:MainActivity` AND `:iosApp:MainViewController`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-11 · **Tags:** kmp, compose-multiplatform, koin, composition-local, ios, dual-host
**Applies to:** Any new root-scope wiring — Koin modules added to `startKoin { modules(...) }`, `CompositionLocalProvider(LocalX provides …)` around the NavHost — introduced for a feature

The project has two roots of composition: `app/src/main/java/.../MainActivity.kt` (Android, via `setContent { GameDealsTheme { … } }`) and `iosApp/src/iosMain/.../MainViewController.kt` (iOS, via `ComposeUIViewController { App() }`). Each registers its own Koin module list and installs its own `CompositionLocalProvider`s. Anything host-scoped — a new Koin module, a `LocalX` provider — must be added to **both** files; the Android-side build will compile cleanly even if iOS is forgotten.

When combined with a no-op `CompositionLocal` default (see L-2026-05-11-01), forgetting the iOS host presents as "feature silently does nothing on iOS" rather than a crash. A diagnostic helper: log a Sentry breadcrumb *before* emitting the side-effect event from the ViewModel — if the breadcrumb fires but the platform action doesn't, the host-wiring step is the suspect.

**Source:** PR #135 (share feature) — iOS share path was silently no-op until `MainViewController.kt` got the matching `CompositionLocalProvider(LocalPlatformActions provides rememberPlatformActions())`.

### L-2026-05-11-01 · `staticCompositionLocalOf { error(...) }` crashes `@Preview`
**TL;DR:** Use a no-op singleton default for shared `staticCompositionLocalOf` — `error(...)` defaults crash every `@Preview` that reads the local.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-11 · **Tags:** compose, composition-local, preview, kmp
**Applies to:** Any new `staticCompositionLocalOf<T>` introduced for a host-provided service in `:common:ui` (or anywhere a `@Preview` may render a consumer)

The textbook default `staticCompositionLocalOf<T> { error("X not provided") }` crashes every `@Preview` that renders a composable reading `LocalX.current` — even when the preview never invokes the underlying side effect. For locals consumed inside shared screens (`DealBottomSheet`, screens with `@Preview` annotations) prefer a no-op singleton default (e.g. `object NoOpPlatformActions : PlatformActions { override fun share(text: String) = Unit }`) and let host-level `CompositionLocalProvider` bind the real impl.

Trade-off: "fail-loud on missing provider" is lost. A missing host wiring becomes a silent runtime no-op, not a crash. See L-2026-05-11-02 for the mitigation.

**Source:** PR #135 (share feature) — initial `error(...)` default caught in review.

### L-2026-05-06-05 · `TopAppBarDefaults.pinnedScrollBehavior` allocates per recomposition via the default `canScroll` lambda, not the wrapper
**TL;DR:** Pass `canScroll = remember { { true } }` to `TopAppBarDefaults.pinnedScrollBehavior(...)` — the default lambda re-allocates each frame.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** compose, recomposition, top-app-bar, material3, stable-lambda
**Applies to:** Any code calling `TopAppBarDefaults.pinnedScrollBehavior(...)` (or `enterAlwaysScrollBehavior`, `exitUntilCollapsedScrollBehavior`) directly inside a composable body

`TopAppBarDefaults.pinnedScrollBehavior(state, canScroll)` is itself `@Composable` and already does `remember(state, canScroll) { PinnedScrollBehavior(...) }` internally. So you cannot wrap it in a caller-side `remember(state) { TopAppBarDefaults.pinnedScrollBehavior(state) }` — that fails with `Composable invocations can only happen from the context of a Composable function`.

The actual cause of per-recomposition allocation is the default `canScroll = { true }` parameter — a fresh lambda each recomposition, which invalidates Material3's internal remember key. Hoist `canScroll` into a stable `remember { { true } }` (or pass `canScroll = remember { { true } }` inline). Same applies to other Material3 helpers that take a default lambda parameter — check before assuming "wrap in `remember`" is the fix.

**Source:** 2026-05-06-bug-hunt-severity-low batch (issue #125 / PR #134). The original bug-hunt finding misidentified the cause; worker investigated Material3 source to find the real one.

### L-2026-05-06-04 · Test mocking library is Mokkery, not MockK
**TL;DR:** This project uses Mokkery for all `commonTest` mocking — MockK is JVM-only and is not used; the patterns doc on this is stale.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** testing, mokkery, mockk, kmp, commontest
**Applies to:** Any test in a `*/src/commonTest/**` source set; by convention, all tests in this codebase

This codebase uses Mokkery (`dev.mokkery:mokkery`) for mocking, not MockK. Mokkery is KMP-compatible; MockK ships JVM-only and cannot be used in `commonTest`. Use `every { … } returns …` for non-suspend stubs, `everySuspend { … } returns …` for suspend stubs, `throws` for exception stubs, and matchers like `any()` / `eq(...)`. The canonical examples live in `feature/*/src/commonTest/**/ui/*ViewModelTest.kt` (e.g., `StoreViewModelTest`, `GiveawaysViewModelTest`).

`docs/patterns/testing.md` still says "MockK Everywhere; No Hand-Rolled Fakes" — that doc was surveyed at SHA `31a89bc` on 2026-05-03, before the KMP migration completed. The pattern is stale; this lesson takes precedence until the patterns doc is refreshed. JVM-only tests outside KMP modules (none currently exist in this project, but possible in `:app` androidTest) could theoretically still use MockK, but default to Mokkery for consistency.

**Source:** Wave 1 of campaign `2026-05-06-bug-hunt-severity-medium` — issue #124 / PR #132. The worker prompt and issue body referenced MockK based on the stale patterns doc; the worker noticed the existing test file used Mokkery and adapted.

### L-2026-05-06-02 · `compose.material3` does NOT bring in `material-icons-core`
**TL;DR:** Keep `compose.materialIconsExtended` — `compose.material3` doesn't pull in `material-icons-core`, so `Icons.*` fails to resolve.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** compose, compose-multiplatform, gradle, dependencies
**Applies to:** Compose Multiplatform modules referencing `androidx.compose.material.icons.Icons` (e.g. `Icons.Filled.ArrowBack`)

The `Icons` API surface (`androidx.compose.material.icons.*`) is in `material-icons-core`, which is shipped only transitively via `compose.material` (legacy Material 1) or `compose.materialIconsExtended`. `compose.material3` does NOT pull it in. Before dropping `compose.materialIconsExtended` "because we only use core icons like ArrowBack/Search", verify there's another path — otherwise the build snaps with "Unresolved reference 'icons'".

**Source:** Phase-2 Compose-runtime lift in convention plugin — assumed material3 included the icons surface; broke the build, restored extended.

### L-2026-05-06-01 · iOS-simulator test serializer BuildService must bind to `KotlinNativeTest`
**TL;DR:** Register the cross-module iOS-test-serialization `BuildService` against `KotlinNativeTest`, not `KotlinNativeHostTest` (excludes simulator).
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** gradle, kmp, kotlin-native, ios, build-service
**Applies to:** Convention-plugin BuildService that serializes `iosSimulatorArm64Test` across modules

When wiring the cross-module test-serialization BuildService from L-2026-05-05-02, register against `KotlinNativeTest::class.java` — not `KotlinNativeHostTest`. The simulator tests are `KotlinNativeSimulatorTest`, the host tests are `KotlinNativeHostTest`, and both extend `KotlinNativeTest`. Binding to the host subclass leaves simulator tasks unserialized, so the XML race still fires intermittently and looks like a one-module flake instead of a known race.

**Source:** Code-quality cleanup of feature/kmp-migration — prior selector had been masking the race for weeks.

### L-2026-05-05-04 · `sentry-kotlin-multiplatform` SPM-only integration breaks Kotlin/Native test linking
**TL;DR:** Don't integrate sentry-kotlin-multiplatform via Xcode SPM — K/N tests fail to link. Use the Gradle plugin or CocoaPods instead.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** sentry, sentry-kotlin-multiplatform, sentry-cocoa, kmp, kotlin-native, spm, gradle, version-skew
**Applies to:** A KMP project considering integrating `sentry-kotlin-multiplatform` on iOS — choosing between SPM, CocoaPods, or the SDK's official Gradle plugin

Adding Sentry-Cocoa via Xcode SPM gets the iOS *app* linking, but **Kotlin/Native test executables (`linkDebugTestIosSimulatorArm64`) fail with `framework 'Sentry' not found`** — Gradle's link step has no path to the SPM-managed framework, since that copy lives only inside Xcode's DerivedData. Every consuming module's iOS tests break: lifting `sentry-kotlin-multiplatform` to `commonMain` of a `:logging`-style module poisons the link path for `:common`, `:domain`, `:remote`, and every feature module that transitively depends on it. Either apply the official `io.sentry.kotlin.multiplatform.gradle` plugin (which auto-downloads Sentry-Cocoa for native targets), use the CocoaPods integration the SDK was designed for, or keep Sentry confined to `androidMain` with a `[Sentry stub]` listener on iOS.

Two SPM gotchas worth preserving in case you return to that path: (1) pin Sentry-Cocoa to the **exact version** sentry-kotlin-multiplatform's cinterop was built against — `0.13.0` requires `8.36.0`. Newer 8.x and 9.x rewrite the referenced ObjC classes (`SentrySDK`, `SentryEnvelope`, `SentryDependencyContainer`) as Swift, breaking cinterop with `Undefined symbols: _OBJC_CLASS_$_Sentry…`. (2) Pick the **`Sentry-Dynamic`** SPM product, not `Sentry`. Xcode 26 treats the static product as a "codeless framework" (`Injecting stub binary into codeless framework` in the build log), strips its Mach-O during embedding, and leaves the linker with nothing to resolve.

**Source:** Phase-7e iOS Sentry wire-up — SPM integration succeeded for the app but broke `:common:linkDebugTestIosSimulatorArm64` and every other iOS test target; reverted in `6037a7a`.

### L-2026-05-05-01 · `kotlin.test` annotations don't resolve in `commonMain` of a non-test KMP fixtures module
**TL;DR:** Don't put `@BeforeTest`/`@AfterTest` on a KMP fixtures module's commonMain — `kotlin("test")` resolves them only in test source sets.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** kmp, kotlin-test, source-sets, testing, fixtures-module
**Applies to:** A KMP "test fixtures" module (e.g. `:testing`) that publishes test helpers via its `commonMain` (consumed by other modules as `commonTestImplementation(project(":testing"))`) — specifically attempts to put `@BeforeTest`/`@AfterTest`-annotated methods on a superclass in that `commonMain`.

Adding `implementation(kotlin("test"))` to the fixtures module's `commonMain.dependencies` puts the artifact on the classpath, but `kotlin.test.BeforeTest`/`AfterTest` are `expect annotation class` decls that only resolve through platform-specific variants (`kotlin-test-junit`, `kotlin-test-junit5`, `kotlin-test-native`), which `kotlin("test")` pulls in only when declared in a *test* source set — not commonMain. Symptom: `Unresolved reference 'BeforeTest'` at `:fixtures-module:compileDebugKotlinAndroid` even after the dep is declared. Workaround: keep the superclass in commonMain providing plain helper methods (`protected fun installMainDispatcher() = Dispatchers.setMain(testDispatcher)`); subclasses in their consuming modules' commonTest carry the `@BeforeTest`/`@AfterTest` annotations themselves. Two annotated one-liners per subclass is the modest cost for not fighting source-set semantics.

**Source:** Phase-A6 simplify pass — attempted to lift `@BeforeTest`/`@AfterTest` into `:testing/commonMain/.../MainDispatcherTest.kt` for 6 feature ViewModel tests.

### L-2026-05-04-07 · Pick JetBrains-AndroidX-fork versions by reading the iOS dep tree first
**TL;DR:** Before adding an `org.jetbrains.androidx.*` artifact, read the existing transitive version from `:iosApp:dependencies` and pin to that.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, kotlin-native, jetbrains-androidx-fork, version-skew, gradle
**Applies to:** Adding any `org.jetbrains.androidx.*` artifact (navigation-compose, lifecycle-viewmodel, savedstate, etc.) to a KMP project that already has them transitively

The JetBrains AndroidX KMP forks publish dozens of alpha/beta versions, most of which crash at runtime on Native with `IrLinkageError` if the fork's pinned transitive (e.g., `org.jetbrains.androidx.savedstate`) doesn't match the version your other deps already pull in. Don't pick by latest-Maven or by guessing; run `./gradlew :iosApp:dependencies --configuration iosSimulatorArm64CompileKlibraries | grep -E '<artifact-prefix>' | sort -u` first, identify the existing transitive version (e.g., `lifecycle-viewmodel:2.9.0-beta01 -> 2.9.6`), then declare the new artifact at *that* version. Skipping this step cost me three abandoned alphas in 7.6 before the working `2.9.0-beta01` (matched lifecycle's own published lineage).

**Source:** Phase 7.6 retry — JetBrains nav-compose fork. Same lesson applies to any future `org.jetbrains.androidx.*` adoption.

### L-2026-05-04-06 · `NSLog` from Kotlin/Native must use the single-arg pattern — varargs don't bridge
**TL;DR:** From K/N, call `NSLog(line.replace("%","%%"))` — the vararg `%@` form crashes at runtime; `String` doesn't bridge to `NSString *`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kotlin-native, ios, nslog, foundation, interop
**Applies to:** Any Kotlin/Native iOS code calling `platform.Foundation.NSLog` with format-and-args style

`NSLog("%@", line)` from Kotlin/Native crashes at runtime with `EXC_BAD_ACCESS` the first time a log line lands. Kotlin/Native's Foundation binding types `NSLog` as `fun NSLog(format: String, vararg args: Any?)` but doesn't bridge Kotlin `String` to Objective-C `NSString *` through the C variadic ABI, so the slot for `%@` receives a garbage pointer. Use the single-arg form: pass the application content as the format string itself, pre-escape `%` characters (`line.replace("%", "%%")`) so user content can't be reinterpreted as format specifiers. Doesn't surface during compile/link — only at runtime when the affected code path actually logs.

**Source:** Phase 7.2 — first iOS log line through `IosConsoleLoggingListener` after HomeScreen wired up.

### L-2026-05-04-05 · Koin 4.0 references Android-only AndroidX lifecycle symbols on iOS
**TL;DR:** Use Koin 4.1.0+ for `koinViewModel()` from iOS Composables — 4.0.0 references `androidx.lifecycle.SavedStateHandle` and crashes on K/N.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** koin, kmp, kotlin-native, lifecycle, irlinkageerror, koin-compose-viewmodel
**Applies to:** Any KMP project using `koin-compose-viewmodel` to call `koinViewModel()` from a Composable that runs on iOS

Koin 4.0.0's `AndroidParametersHolder` references `androidx.lifecycle.SavedStateHandle` (Google's Android-only artifact) directly. On iOS the classpath has the JetBrains fork `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:*` instead, so first invocation of `koinViewModel()` from any Composable rendered on iOS throws `IrLinkageError: No class found for symbol 'androidx.lifecycle/SavedStateHandle|null[0]'`. Compile/link succeeds; the failure only appears when the affected code path runs (Composable rendering an `internal viewModel: T = koinViewModel()` default). Bump Koin to 4.1.0+, which dropped the direct AndroidX reference. Same family of failure as L-2026-05-04-04 (Ktor skew) — JVM resolves per-class at link time and silently muddles through; Native carries klib symbol fingerprints and crashes hard at runtime.

**Source:** Phase 6.7d — first iOS render of a real feature Composable.

### L-2026-05-04-04 · Ktor version skew silently breaks Native, silently works on JVM
**TL;DR:** Align every Ktor artifact via the catalog BOM — a transitive forcing a higher core version causes Native `IrLinkageError` at first call.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** ktor, kmp, kotlin-native, version-skew, irlinkageerror
**Applies to:** Any KMP project pulling Ktor in alongside transitives (sandwich-ktor, coil3-network-ktor3, etc.) that may force a Ktor version higher than the BOM in `libs.versions.toml`

JVM resolves missing/changed symbols at link time per-class, so a transitive forcing `ktor-client-core` from 3.0.3 → 3.3.0 while `ktor-client-darwin:3.0.3` stays pinned is invisible on Android. Kotlin/Native klibs carry exact symbol fingerprints — the same skew turns into `kotlin.internal.IrLinkageError` at runtime as soon as the affected code path runs (e.g., the first HTTP response body). Manifested for us as `Function 'dropCompressionHeaders' can not be called: No function found for symbol ...`. Fix: align the BOM (`ktor = "3.3.0"`) so all Ktor artifacts agree. Watch for this on every Ktor major version bump and any new dep that Ktor-uses internally.

**Source:** Phase 6.7c — first iOS network round-trip surfaced the skew.

### L-2026-05-04-03 · Two-Koin-module split is the right shape for platform-specific construction in KMP
**TL;DR:** Prefer a commonMain Koin module + a platform module seeding the platform binding over `expect`/`actual` for platform-specific construction.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, koin, di, room
**Applies to:** A KMP module that needs platform-specific construction (database Builder, file-path resolution, platform context, native handle) — i.e. a binding that can't live in commonMain because it touches `androidContext()`/`NSHomeDirectory()`/etc.

Prefer two Koin modules over `expect`/`actual` for this case: `xModule` in commonMain owns all portable wiring (converters, DAOs, repositories, the final bound type that customizes a Builder and `.build()`s it), `xAndroidModule` in androidMain owns just the platform seed binding (`single<RoomDatabase.Builder<T>> { Room.databaseBuilder<T>(androidContext(), name) }`). The consumer registers both modules at `startKoin`. iOS later registers its own `xIosModule` providing the same bound type. Avoids `expect`/`actual` ceremony — DI is already a runtime indirection, no need to add a compile-time one. Test overrides target the *final* type (e.g. override `single<DomainDatabase>` with `inMemoryDatabaseBuilder`), bypassing the Builder graph entirely so it doesn't need a test double.

**Source:** Phase 5.16 — `:domain` DI module split.

### L-2026-05-04-02 · `BoxWithConstraints { maxWidth < 600.dp }` substitutes for `WindowWidthSizeClass` in CMP
**TL;DR:** For phone-vs-wider in commonMain, wrap in `BoxWithConstraints` + check `maxWidth < 600.dp` — `WindowWidthSizeClass` is Android-only.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, compose-multiplatform, adaptive-layout
**Applies to:** Lifting an Android Composable that branches on window size class to commonMain

`androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()` and `WindowWidthSizeClass` are Android-only. For a screen that just needs "is this a phone or wider," wrap the layout in `BoxWithConstraints` and branch on `maxWidth < 600.dp` (or whatever breakpoint matters). It's a literal substitute, no behavior change on Android, works on every CMP target. For multi-breakpoint adaptive layouts the call shape gets uglier — but for the binary compact-vs-not case this is the path of least resistance.

**Source:** Phase 5.10 — `:feature:game` migration.

### L-2026-05-04-01 · `androidx.paging` is split: `paging-common` is KMP, `paging-compose` is not
**TL;DR:** `paging-common` 3.3+ is KMP; `paging-compose` is Android-only. Put the paging ViewModel in commonMain, the Screen in androidMain.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, paging, compose-multiplatform
**Applies to:** Migrating a feature module that uses paging to KMP

When migrating a paging-using feature, `androidx.paging:paging-common` (3.3+) is multiplatform-capable so types like `PagingData`, `LoadState`, and `cachedIn` work in commonMain — a paging-aware ViewModel can move freely. `androidx.paging:paging-compose` (`LazyPagingItems`, `collectAsLazyPagingItems`, `itemKey`) is Android-only; any Composable consuming it stays androidMain. JetBrains maintains a multiplatform fork, but adopt it only when an iOS consumer actually motivates it — until then, the natural cut is "ViewModel commonMain, Screen androidMain" and that has zero design overhead.

**Source:** Phase 5.13 — `:feature:store` migration.

### L-2026-05-03-06 · Same-named `.kt` files across KMP source sets generate duplicate JVM class names
**TL;DR:** Same-named top-level `.kt` files in commonMain and androidMain need `@file:JvmName("…")` on one side, else duplicate JVM classes.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** kmp, kotlin, jvm, source-sets, file-naming
**Applies to:** KMP modules where commonMain and androidMain (or any two source sets reaching the same target) both contain a top-level-functions file with the same filename — typical when splitting a file into a commonMain core + androidMain platform-specific implementation

Kotlin generates one `<FileName>Kt` JVM class per top-level-functions file. When `Theme.kt` exists in both `commonMain/.../theme/` and `androidMain/.../theme/`, both compile to `ThemeKt.class` on the Android target → `Duplicate JVM class name 'pm/bam/gamedeals/common/ui/theme/ThemeKt' generated from: ThemeKt, ThemeKt`. Add `@file:JvmName("AndroidTheme")` (or rename the file `Theme.android.kt` if you prefer the convention) to one side. Doesn't trip when the file contains only classes/objects (those have per-class JVM names) or when using `expect/actual` (those merge to one JVM class on resolution). Watch for it during file-splits where one half stays androidMain because of platform deps.

**Source:** Phase 5.4e — Theme.kt split (schemes/locals to commonMain; `GameDealsTheme` Composable stays androidMain).

### L-2026-05-03-05 · `pluginManager.apply()` in precompiled convention plugins requires `implementation` deps, not `compileOnly`
**TL;DR:** In a convention plugin that calls `pluginManager.apply("third.party")`, declare the upstream plugin as `implementation`, not `compileOnly`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** gradle, build-logic, convention-plugins, kmp, compose-multiplatform
**Applies to:** Precompiled convention plugins in `:build-logic:convention` that call `pluginManager.apply("third.party.id")` from inside `Plugin<Project>.apply()`

A `compileOnly(libs.foo.gradle.plugin)` in build-logic is enough for the convention's Kotlin source to compile against the plugin's APIs (configuring `ComposeExtension`, etc.), but at apply-time on a consuming module Gradle fails with `Plugin with id 'foo' not found`. The runtime classpath of the *convention plugin* needs the third-party plugin's classes, so switch to `implementation(...)`. `kotlin-gradle-plugin` and `compose-compiler-gradle-plugin` slip past this trap because they're already on the build's classpath via the project-level plugins block — third-party plugins like `org.jetbrains.compose` are not, and need the explicit `implementation`. Symptom catches you the *first* time a module applies the convention; build-logic's own compile is green so you don't spot it until consumer-build failure.

**Source:** Phase 5.4a — `:common:ui` first module to apply `kmp.library.compose`; build-logic had `compose-multiplatform-gradle-plugin` as `compileOnly`.

### L-2026-05-03-04 · Removing a Hilt module (esp. `hilt-navigation-compose`) silently strips Compose runtime from KSP classpath
**TL;DR:** When removing Hilt from a module that uses `@Immutable`, explicitly add `implementation(libs.androidx.compose.runtime)` — KSP needs it.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** ksp, room, hilt-removal, compose-runtime, transitive-deps, kmp
**Applies to:** Modules removing `androidx.hilt:hilt-navigation-compose` (or any Hilt-navigation dep) that contain Room `@Entity` classes annotated with Compose stability annotations like `@Immutable`.

`hilt-navigation-compose` transitively brings `androidx.compose.runtime:runtime`. Modules that never explicitly declared a Compose runtime dep — e.g. `:domain`, which only uses `@Immutable` for stability hints on Room entities — were getting it for free through the Hilt chain. When you drop Hilt during a DI migration, the Compose runtime dep disappears from compile classpath; **Room's KSP processor then fails with `[MissingType]: Element 'pm.bam.gamedeals.domain.models.Deal' references a type that is not present` on every entity** — a misleading message that points at the entity, not at the missing annotation. Fix shape: add `implementation(libs.androidx.compose.runtime)` (i.e. `androidx.lifecycle:lifecycle-runtime-compose`, which brings the actual Compose runtime transitively) to any non-feature module that uses `@Immutable` and previously inherited Compose runtime through Hilt. **Audit checklist when removing Hilt from a module:** `gradle :module:dependencies | grep compose.runtime` *before* the removal; if any Compose-runtime artifact appears via a Hilt path, declare it explicitly on the module before dropping Hilt.

**Source:** Phase 4.2 of the KMP migration; `:domain` → Koin; bisected during Hilt removal.

### L-2026-05-03-03 · Ktor `parameter()` always encodes — use `encodedParameters` for already-encoded values
**TL;DR:** For already-encoded query values use `url { encodedParameters.append(...) }` — `parameter(...)` always re-encodes and produces 404s.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** ktor, retrofit-migration, url-encoding, networking, kmp
**Applies to:** Any Retrofit `@Query("name", encoded = true)` — or any other API surface where a query/path value is supplied already percent-encoded — being ported to Ktor `HttpRequestBuilder.parameter(...)`.

Ktor's `parameter("name", value)` always URL-encodes the value, with no opt-out flag. For values that arrive already percent-encoded (e.g. CheapShark serves dealIDs as `…%3D`), this re-encodes `%` → `%25` and produces `…%253D` on the wire, which servers decode to a literal `%3D` and reject as a 404. The Ktor equivalent of Retrofit's `@Query("id", encoded = true)` is `url { encodedParameters.append("id", id) }` — `URLBuilder.encodedParameters` is the pre-encoded sibling of `URLBuilder.parameters` and is part of the multiplatform URL builder, so it works on iOS too. **Migration audit:** during a Retrofit → Ktor port, grep for every `@Query(...encoded = true)` and `@Path(...encoded = true)` in the pre-migration tree and convert each to `encodedParameters.append`/`encodedPathSegments`. The default `parameter()`/`path()` lookalikes silently drop the semantic.

**Source:** Phase 4 smoke-test catch on `DealsApi.getDeal` (commit `012d27b`); pre-Phase-3 had `@Query("id", encoded = true)` (verified at `git show a8bacbd^`); regression test in `DealsApiTest`.

### L-2026-05-03-02 · Ktor `Logging { level = LogLevel.BODY }` + OkHttp engine hangs `body<T>()`
**TL;DR:** Set Ktor `Logging` to `LogLevel.HEADERS` on the OkHttp engine — `LogLevel.BODY` consumes the one-shot body and hangs `body<T>()`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** ktor, ktor-logging, okhttp-engine, content-negotiation, networking, kmp
**Applies to:** Any HttpClient that installs both `Logging` at `LogLevel.BODY` and `ContentNegotiation`, on the OkHttp engine

The Logging plugin at `LogLevel.BODY` reads the response body to log it; on OkHttp's engine that body is a one-shot stream and the read consumes it. `ContentNegotiation`'s subsequent `body<T>()` then waits indefinitely for bytes that have already been read. Symptom is exact: the `Ktor REQUEST: ...` line logs, then nothing — no `RESPONSE`, no exception, no timeout, no parse error. Use `LogLevel.HEADERS` as the default — same diagnostic value (request line, response status, headers) without consuming the body. If you genuinely need bodies in logs, use Ktor's `observeRequest`/`observeResponse` callbacks which don't consume the stream.

**Source:** Phase 3 of the KMP migration, swapping Retrofit → Ktor in `:remote:*` modules.

### L-2026-05-03-01 · Module that opts out of the convention plugin silently loses inherited test-runtime deps
**TL;DR:** A module opting out of the convention plugin must declare `debugImplementation(libs.androidx.compose.test)` or `createComposeRule` fails.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** gradle, convention-plugins, build-logic, compose-testing, ui-test-manifest, test-infra
**Applies to:** Any feature module that does not apply `gamedeals.android.feature` (or whichever convention plugin the rest of the modules use); audits checking whether existing instrumentation/Compose tests are actually runnable

A module that opts out of the project's convention plugin doesn't inherit the shared `debugImplementation` / `androidTestImplementation` deps that *make tests runnable at all*. The classic symptom is silent: code compiles, lint is green, `assembleDebugAndroidTest` succeeds — but `connectedDebugAndroidTest` fails with `IllegalStateException: No compose hierarchies found in the app` because `ui-test-manifest` isn't on the test classpath, so `createComposeRule()` has no `ComponentActivity` to host. Compilation never notices because the test rule's contract is runtime-only.

`feature:webview` is the canonical example in this codebase: it doesn't apply `gamedeals.android.feature` (no Hilt/Paging/Coil to justify the plugin), so it didn't pick up `debugImplementation(libs.androidx.compose.test)`. The pre-existing `WebViewTest` (commit `57c876a`) was technically never runnable as-shipped — only noticed during issue #101 work.

Audit checklist when adding or reviewing tests in a feature module:

1. Does the module apply the convention plugin? If yes, you inherit the test-infra deps and you're fine.
2. If no, does the module's `build.gradle.kts` explicitly carry `debugImplementation(libs.androidx.compose.test)` (and any other deps the convention plugin would have added)?
3. Run the actual instrumentation target locally — don't trust `assembleDebugAndroidTest` as a runnability proxy.

Long-term fix shapes: either bring the divergent module under the convention plugin (cleanest, but may pull in unwanted deps), or extract a leaner `gamedeals.android.feature.ui` plugin that only carries Compose + test-infra without Hilt/Paging/Coil. Short-term fix is the explicit one-line dep, as in PR #108.

**Source:** Wave 2 of campaign `2026-05-02-bug-hunt-3` (issue #101 → PR #108); discovered when the wave-2 worker for #101 found `:feature:webview:connectedDebugAndroidTest` failing with `No compose hierarchies found` and traced it to the missing `ui-test-manifest` dep that other feature modules pick up via the convention plugin.

### L-2026-05-02-10 · Re-verify findings against `origin/<merge-target>` HEAD before working them
**TL;DR:** Re-verify bug-hunt findings against `origin/<merge-target>` HEAD before working them — wave branches drift while `dev` moves on.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** workflow, planner-agents, worker-agents, merge-target-drift, github-sync, github-issue-waves
**Applies to:** Planner/worker sub-agents in `github-issue-waves`; bug-hunt-to-issues filing in `github-sync`; any flow that reads issues filed from a feature/wave branch and acts on them later

Issues filed from a wave/feature branch can become partially or fully obsolete by the time a worker picks them up — `dev` moves between filing and execution. Issue #90 is the canonical example: the `DealDetailsController` portion was already fixed on `dev` (commit `b64307b`) when the worker started; only the `DealDetailsViewModel` portion remained real. Without the planner re-verifying, the worker would have proposed a no-op or duplicated the existing fix.

Discipline at three layers:

- **`github-sync` (filing-time):** Step 2.5 (added 2026-05-02) extracts a signature from each finding's Evidence block and substring-matches it against `git show origin/<merge-target>:<path>`. Findings whose code is already fixed on the merge target default to skip in the triage plan.
- **`github-issue-waves` planners:** when reporting file lists for a candidate issue, read `origin/<merge-target>:<path>` for each cited path. If the antipattern is gone, narrow the issue's scope or surface a `partially-fixed` flag in the planner's YAML output so the orchestrator can warn the user.
- **Workers (execution-time):** before editing, diff the issue's cited Evidence against current `dev` HEAD. Narrow scope to portions still real, and call out the narrowing in the PR body so the reviewer doesn't expect changes the issue text implied.

The signature-line check is fuzzy on purpose — exact line matches break under whitespace/refactor drift. Treat the verifier's verdict as advisory; the human's veto on the triage plan is the source of truth.

**Source:** Wave 1 of campaign `2026-05-02-bug-hunt-2` (issue #90 → PR #94); pairs with the `Step 2.5 — Verify each finding against the merge target` section in `.claude/skills/android-bug-hunting-github-sync/SKILL.md`

### L-2026-05-02-09 · Wall-clock time in suspend helpers breaks test virtual time
**TL;DR:** Don't mix `currentTimeMillis()` with `delay()` in suspend helpers — `runTest` virtualizes only `delay`, so wall-clock checks break tests.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** coroutines, test-virtual-time, runtest, dispatchers, common-helpers
**Applies to:** Any `coroutineScope`-shaped helper in `:common` (or any module) that pads a minimum duration / debounce floor / rate-limit floor; new "minimum loading time" / "settle" / "throttle" utilities

A helper that measures `System.currentTimeMillis()` deltas and pads via `delay(...)` mixes wall-clock and virtual-clock domains. Under `runTest`, `delay` is virtualized but `currentTimeMillis()` is real wall-clock — so `elapsed` reads as ~0 (the test scheduler skipped real time), and the helper pads the *full* duration against real time. Tests slow to wall-clock pace and lose determinism, and any consumer of the helper is impossible to advance past via `testScheduler.advanceTimeBy(...)`.

Fix shape (matches the operators in this codebase already):
```kotlin
coroutineScope {
    val pad = launch { delay(delayMillis) }
    val result = block()
    pad.join()
    result
}
```

The pad coroutine and `block()` both run on the same `TestDispatcher` under `runTest`, so the whole thing is virtual. `inline`/`crossinline` semantics survive this rewrite. The user-perceived behaviour is unchanged: `pad.join()` after `block()` returns enforces the same minimum total duration in production.

Audit checklist when adding a new "minimum duration"-style helper:
1. No `System.currentTimeMillis()`, `Instant.now()`, `Clock.systemDefaultZone()`, or `nanoTime()` inside the suspend body.
2. All padding uses `delay(...)` directly (or `launch { delay(...) }.join()` when `block()` runs in parallel).
3. A test using `runTest { }` + `testScheduler.currentTime` reads at least the configured minimum after the helper returns instantly — without taking real wall-clock time to do so.

**Source:** Wave 1 PR #59 (issue #45 — `mapDelayAtLeast` / `flatMapLatestDelayAtLeast`); Wave 1 PR #93 (issue #91 — `withMinimumDuration` was the missed companion in the same pattern)

### L-2026-05-02-08 · `combine`-with-trigger flows: reason explicitly about write-order between trigger updates and downstream `_uiState` mutations
**TL;DR:** When one function writes both upstream of `combine` and into `_uiState`, do the upstream write first — combine propagates synchronously.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** stateflow, combine, viewmodel, race-conditions, ordering, source-of-truth
**Applies to:** Feature ViewModel handlers that both (a) update an upstream `MutableStateFlow`/`MutableSharedFlow` feeding a `combine(...)` source-of-truth flow, AND (b) directly write `_uiState.update {}` or `_uiState.emit(...)` in the same function body

When a function does both — write upstream of `combine`, AND write `_uiState` directly — the upstream write synchronously propagates through `combine` and reaches the downstream collector before the function continues. The order of the two writes therefore determines which one wins. In `GiveawaysViewModel.reloadGiveaways()`:

```kotlin
fun reloadGiveaways() = viewModelScope.launch {
    refreshOutcomeFlow.value = RefreshOutcome.Idle    // (1) trigger upstream
    _uiState.update { it.copy(status = LOADING) }     // (2) direct downstream write
    flow<Unit> {
        giveawaysRepository.refreshGiveaways()
    }
        .catch { refreshOutcomeFlow.value = RefreshOutcome.Error }
        .collect { }
}
```

If the previous outcome was `Error`, write (1) causes `combine` to re-emit `SUCCESS` to `_uiState` first; write (2) then overwrites it with `LOADING`. The reverse order would have `LOADING` overwritten by `combine`'s `SUCCESS`. **Pattern:** when a `combine` upstream feeds the same `_uiState` you're directly writing, do upstream resets *before* direct writes. If the upstream feed is via a hot collector inside `init {}`, you can't sequence around it — the discipline applies regardless.

**Source:** Wave 2 PR #89 (issues #75/#77 — `RefreshOutcome` flow combined into `GiveawaysViewModel` source-of-truth)

### L-2026-05-02-07 · `StateFlow` conflation of identical-equals emissions: fix at the flow boundary, not by breaking `equals`
**TL;DR:** Fix StateFlow conflation by swapping to `MutableSharedFlow(replay = 1, DROP_OLDEST)` — never break `equals` on the model type.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** stateflow, sharedflow, conflation, compose, stability, equals-override, anti-pattern
**Applies to:** Any `MutableStateFlow<T>` whose producer needs identical-equals successive emissions to *still* trigger downstream — typically when downstream is `flatMapLatest`, a `LaunchedEffect` keyed on the value, or any `combine`/`zip` that should re-fire on every "user-pressed-go-again"

`StateFlow` conflates by structural equality and silently drops re-emissions of the same value. The wrong fix is to override `equals` on the model type to always return `false` — that destroys Compose stability for every composable taking the type (Compose's skipping mechanism relies on `equals`), and breaks any future `LaunchedEffect` keyed on the value. The right fix is at the producer: `MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)` preserves replay-1 for late subscribers, doesn't conflate, and leaves the model type Compose-stable. Alternatively, wrap emissions in a sentinel envelope (`Indexed<T>`, `Pair<Long, T>`) so the envelope differs while the inner value retains structural equality.

```kotlin
// before — broken
private val parametersFlow = MutableStateFlow(SearchParameters())  // re-emits same params dropped
// SearchParameters had: override fun equals(other: Any?) = false  // destroys Compose stability

// after
private val parametersFlow = MutableSharedFlow<SearchParameters>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
// data class SearchParameters(...) — equals restored to data-class equals; Compose-stable
```

Note: `MutableSharedFlow(replay=1)` has empty replay until the first `emit`, whereas `MutableStateFlow(initial)` synthesises an initial emission. If the consumer chain depended on that synthetic null/initial first emission, you'll need an explicit `.onStart { emit(initial) }` or a `replayCache.firstOrNull() ?: initial` read.

**Source:** Wave 2 PR #88 (issue #76 — `SearchParameters.equals = false` defeated Compose skipping); pairs with L-2026-05-02-05 (test-side analogue: `flowOf` vs `MutableSharedFlow` for hot sources)

### L-2026-05-02-06 · Compose `LaunchedEffect` capturing a caller-provided lambda must wrap it in `rememberUpdatedState`
**TL;DR:** Wrap caller lambdas in `rememberUpdatedState` before capturing in `LaunchedEffect` — otherwise it keeps firing the launch-time copy.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** compose, launchedeffect, rememberupdatedstate, stale-capture, lambda-capture
**Applies to:** Any helper of the shape `LaunchedEffect(key) { … callback(it) }` (or `repeatOnLifecycle { … callback(it) }`) where `callback` arrives via composable parameter and the parent is expected to recompose

`LaunchedEffect`'s coroutine is launched once per key-change and captures whatever lambda values were in scope at launch time. If the lambda comes from a recomposing parent and closes over screen-local state, the coroutine permanently fires the *first* version even after the parent recomposes with an updated closure — silently invoking stale callbacks (wrong navigation target, wrong analytics payload, etc). The fix is to wrap the parameter in `rememberUpdatedState` and call the snapshot through that handle:

```kotlin
val currentCollector by rememberUpdatedState(collector)
LaunchedEffect(sideEffectFlow, lifecycleOwner, lifeCycleState) {
    lifecycleOwner.repeatOnLifecycle(lifeCycleState) {
        sideEffectFlow.collect { currentCollector(it) }
    }
}
```

This is a textbook D7 idiom and applies symmetrically to `DisposableEffect` (cleanup-callback variant). Wrap inside the helper rather than relying on every caller to pass a stable reference — the safety contract belongs to the helper, not the call site. (The repo's `SingleEventEffect` previously was safe-by-accident because its only call site, `HomeScreen`, used a `remember(navController)`-stable reference; that's a fragile property of the chain, not of the helper.)

**Source:** Wave 1 PR #85 (issue #74 — `SingleEventEffect` captures `collector` lambda without `rememberUpdatedState`)

### L-2026-05-02-05 · Hot-source races need `MutableSharedFlow` in tests, not `flowOf(...)` — `flowOf` completes after one emission and masks second-emission bugs
**TL;DR:** Stub hot Flow sources with `MutableSharedFlow`, not `flowOf(...)` — `flowOf` completes after one emission and hides second-emission races.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** testing, coroutines, flow, room, race-conditions, test-fixtures
**Applies to:** Test stubs for repository methods that return a hot `Flow` in production (Room `@Query` flows, `MutableSharedFlow`-backed signals, `callbackFlow`-driven sources) — i.e. anywhere the production flow does not complete

The `GiveawaysViewModelTest` originally stubbed `observeGiveaways()` with `flowOf(listOf(...))`, which completes after exactly one emission. Production's Room flow emits *every time the table is invalidated* — never completing. The parallel-collector race in `GiveawaysViewModel.loadGiveaway` (issue #72) needed a *second* emission to manifest, so the test never reproduced the bug despite covering the call site. Default test stub for hot sources should be `MutableSharedFlow` (or the equivalent `flow { while (isActive) emitAll(channel) }` shape) so tests can drive multiple emissions and exercise race-condition behaviour. Reserve `flowOf(...)` for fixtures that are genuinely terminal/one-shot (e.g. `flow.first()` consumers).

This is a structural test-shape rule, not a per-bug fix — applying it eagerly across `feature/*/src/test/.../ui/*ViewModelTest.kt` files would surface other latent races. Pairs with L-2026-05-01-07 (which governs the *expected emission count* under `UnconfinedTestDispatcher`).

**Source:** Wave 1 PR #86 (issue #72 — `GiveawaysViewModel.loadGiveaway` leaks long-lived Room collector)

### L-2026-05-02-04 · `try { ... } catch (Throwable)` blocks containing suspending work must rethrow `CancellationException` first
**TL;DR:** In any `catch (Throwable)` whose body suspends, rethrow `CancellationException` first — otherwise structured cancellation breaks silently.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** coroutines, cancellation, structured-concurrency, error-handling, crashlytics
**Applies to:** Any `try { … } catch (t: Throwable) { … }` whose catch body suspends (calls `emit`, `withContext`, `delay`, etc.) — or logs as fatal — inside `viewModelScope`, `lifecycleScope`, or any structured-concurrency scope where the host can be cancelled mid-work

Kotlin coroutines signal cancellation by throwing `CancellationException`, which is a `Throwable`. A bare `catch (t: Throwable)` swallows it: structured concurrency breaks (parent doesn't see the cancellation), the catch body runs to completion (emitting state, calling Crashlytics fatal, etc.), and the user-visible result is "navigation away from the screen logged a Crashlytics fatal and overwrote the cancellation state." The discipline is to rethrow `CancellationException` first:

```kotlin
} catch (t: CancellationException) {
    throw t
} catch (t: Throwable) {
    fatal(logger, t)
    _state.emit(...)
}
```

Apply at every catch site that meets the criterion above, including nested fallback catches. The pattern is already established in this repo at `DealsMediator.kt:76-81`; #71 backfilled `DealDetailsController` to match. **Auditing for other `catch (Throwable)` sites with suspend bodies is high-value** — each one is a latent cancellation-swallow.

**Source:** Wave 1 PR #84 (issue #71 — `DealDetailsController.load` swallows `CancellationException` across both catch sites)

### L-2026-05-02-03 · Gradle `implementation` is non-transitive on compile classpath: when a `:domain` type adopts a third-party type/extension, consumer modules need the dep added explicitly
**TL;DR:** When a shared type adopts a third-party type/extension, add the dep to every consumer module — `implementation` doesn't propagate.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** gradle, modularization, kotlinx-collections-immutable, dependency-graph, build-config
**Applies to:** Any refactor that retypes a public field on a `:domain` (or other shared) data class to use a third-party type — `ImmutableList<T>`, `Instant`, `BigDecimal`, etc. — where consumer modules will call extension functions on the type

Gradle `implementation(libs.kotlinx.collections.immutable)` in `:domain` is enough for `:domain` itself to compile, but it does NOT propagate the dep to modules that depend on `:domain`. The *type* (`ImmutableList<GameDeal>`) flows through the public API surface fine — that's what `api` vs `implementation` controls — but **extension functions** on that type (`kotlinx.collections.immutable.toImmutableList`, `.persistentListOf`, etc.) are imported from the third-party library directly, and that import only resolves if the consumer module declares the dep itself. Symptom: `:domain` test runs green, but `:remote:cheapshark` (or `:feature:game`, or any other module that builds a value of the new type via an extension) fails compile with `Unresolved reference: toImmutableList`. Fix: add `implementation(libs.kotlinx.collections.immutable)` to every consumer module that imports an extension on the type. **Planner heuristic:** when retyping a `:domain` field, walk the import-graph of consumer modules and pre-add the dep — don't wait for the build to surface it module-by-module.

Same gotcha will apply to any future migration of this shape (e.g. `kotlinx.datetime.Instant` if/when it spreads, or a hypothetical `:domain` adoption of `Arrow`'s `Either`).

**Source:** Wave 2 PR #83 (issue #80 — GameDetails.deals → ImmutableList migration); also surfaced in PR #82 (issue #79) where `:feature:giveaways` already had the dep so the gotcha was masked

### L-2026-05-02-02 · `@Immutable` + `ImmutableList<…>` on every domain model used as a composable parameter
**TL;DR:** Mark every `:domain` `data class` used as a Composable parameter `@Immutable` and retype its `List<…>` fields to `ImmutableList<…>`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** compose, stability, recomposition, immutable-collections, kotlinx-serialization
**Applies to:** `data class` types in `:domain` whose fields include raw `kotlin.collections.List<…>` and that are read inside a composable (or nested inside another type that is)

Compose marks raw `List` unstable, so any composable taking such a type can't skip on recomposition even when the value is bit-identical. Pattern: retype every `List<…>` field to `kotlinx.collections.immutable.ImmutableList<…>`, annotate the class `@Immutable`, and replace `.toMutableList().map { … }` (which produces a regular `List`) with `.map { … }.toImmutableList()` at every call site. The catalog already pins `kotlinx-collections-immutable` (since #38); add `implementation(libs.kotlinx.collections.immutable)` to whichever module needs it (`:domain`, `:common:ui`, `feature/*`).

**Important:** kotlinx-serialization 1.9.0 (the catalog version) supports `ImmutableList` natively — `@Serializable` domain models adopt it without a custom serializer, and `Saver` round-trips through `Json.encodeToString` keep working. **Operational note:** any two issues that both add `kotlinx-collections-immutable` to the same module's `build.gradle.kts` will conflict — schedule them in separate waves of the bug-hunt campaign.

**Source:** Wave 1 PR #82 (issue #79 — GiveawaySearchParameters @Immutable + ImmutableList migration); also #38 (PR #70) and pending #80

### L-2026-05-02-01 · `MutableStateFlow.update { it.copy(...) }` for field-level merges; `emit(...)` only for full-state replacements
**TL;DR:** Use `_uiState.update { it.copy(...) }` for field-level merges; reserve `emit(...)` for full-state replacements driven by an upstream Flow.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** viewmodel, stateflow, coroutines, race-conditions, atomicity
**Applies to:** Feature ViewModel handlers in `feature/*/src/main/.../ui/*ViewModel.kt` that mutate `_uiState`

Three patterns coexist in this codebase, and they are not interchangeable. (1) `_uiState.value.copy(...)` followed by `_uiState.emit(...)` is a read-modify-write — concurrent coroutines can read the same snapshot, mutate disjoint fields, and clobber each other on emit. Always replace with `_uiState.update { it.copy(...) }`, the documented atomic CAS helper that retries on contention. (2) `_uiState.update {}` works *inside* Flow chains too: `.onStart { _uiState.update { it.copy(state = LOADING) } }` is the right shape, not `.onStart { _uiState.emit(_uiState.value.copy(...)) }`. (3) Keep `.collect { _uiState.emit(it) }` *as-is* when the upstream Flow already produces the complete next state — that's a full-state replacement, not a field-level merge, and there's no race to fix.

Side note: when the LOADING transition moves out of a flow chain into a side-effect `update {}`, the chain now emits nothing — `logFlow` may need an explicit `flow<Unit>` type pin to keep inference happy.

**Source:** Wave 1 PR #81 (issue #78 — ViewModels read-modify-write `_uiState.value.copy(...)`)

### L-2026-05-01-09 · `AndroidView` lifecycle: hoist clients via `remember`, wire `onRelease` for true teardown
**TL;DR:** `remember` clients outside `AndroidView`'s `factory` and wire `onRelease` to tear the native view down (e.g. `WebView.destroy()`).
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** compose, androidview, webview, lifecycle, memory-leak
**Applies to:** Any `AndroidView { }` wrapping a stateful native view (`WebView`, `MapView`, `VideoView`, `ExoPlayer`'s `PlayerView`) — anywhere the view holds session/network/JS state that must be cleaned up when the composable leaves composition

`AndroidView`'s `factory` runs once when the view first enters composition, but `update` runs on every recomposition — so anything constructed inside `factory` (e.g. a `WebViewClient`) is fine for identity but anything constructed inside `update` is reborn each frame. Hoist clients into `remember { }` outside the `AndroidView` and assign them in `factory`. More importantly, **`AndroidView` does not destroy the underlying view when the composable leaves composition** — without an explicit `onRelease`, a `WebView` lingers with its full session, JS engine, network stack, and listeners until GC. Wire `onRelease` with the standard teardown sequence:

```kotlin
AndroidView(
    factory = { ctx -> WebView(ctx).apply { webViewClient = client; loadUrl(url) } },
    update = { /* ... */ },
    onRelease = { wv ->
        wv.stopLoading()
        wv.webChromeClient = null
        wv.webViewClient = WebViewClient()
        wv.loadUrl("about:blank")            // cancels in-flight network + JS before destroy
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
    },
)
```

The `loadUrl("about:blank")` is not cosmetic — it forces the WebView to abandon in-flight network and script execution before `destroy()` is called, which otherwise can leave threads hanging. Same shape applies to other resource-holding native views, adapted to their teardown API (`MapView.onDestroy()`, `ExoPlayer.release()`, etc.).

**Source:** Wave 3 PR #67 (issue #30 — WebView never destroyed when composable leaves composition)

### L-2026-05-01-08 · Use `ApplicationInfo.FLAG_DEBUGGABLE`, not `BuildConfig.DEBUG`, for one-off debug-only logic
**TL;DR:** For one-off debug-build checks, use `(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` — avoids opting into BuildConfig.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** android, agp, buildconfig, debug-gate, gradle
**Applies to:** Any module wanting a single "is this a debug build" boolean — `StrictMode` policies, debug-only logging, dev-only feature flags

This project's modules don't enable `android.buildFeatures.buildConfig`, so AGP 8 doesn't generate `BuildConfig` for them. Reaching for `BuildConfig.DEBUG` would force opting in to BuildConfig generation across that module just for one boolean (and add a build-script change with downstream effects on R8 metadata, Configuration Cache invalidation, etc.). Use `(applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` instead — equivalent semantics (debug build types implicitly set `debuggable=true`), no `buildConfig` opt-in. This is the pattern used in `GameDealsApplication.onCreate` to gate StrictMode (PR #63).

**Source:** Wave 1 PR #63 (issue #42 — Storage suspend / StrictMode gate)

### L-2026-05-01-07 · ViewModel emission tests under `UnconfinedTestDispatcher` should expect `size == 1` for steady state — not `[initial, current]`
**TL;DR:** Under `UnconfinedTestDispatcher`, a correctly-shaped VM emits one steady `uiState` — don't assert `[initial, current]`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** viewmodel, stateflow, coroutines, testing, unconfined-test-dispatcher
**Applies to:** Tests in `feature/*/src/test/.../ui/*ViewModelTest.kt` that collect a ViewModel's `uiState`/`screenState` flow into a list (e.g. `viewModel.uiState.take(N).toList()`) under `runTest` with the project's standard `UnconfinedTestDispatcher` rule

Under `UnconfinedTestDispatcher`, the ViewModel's `init { ... viewModelScope.launch { ... } }` block drains synchronously *before* the test starts collecting, and `MutableStateFlow` is conflated and replay-1. A correctly-shaped ViewModel therefore emits exactly one value to the test collector: the steady-state value after `init` finished. If a test asserts `size == 2, [initialValue, currentValue]`, that "second" emission is *not* the test catching a state transition — it is almost always (a) the spurious initial-flash from a `_uiState.stateIn(WhileSubscribed, initial)` derived StateFlow (the bug fixed in #37 / lesson L-2026-04-30-06), or (b) the test racing the dispatcher. Default expectation is `size == 1`. Larger sequences are only correct when the test *deliberately* drives a state transition during the collect window (e.g. user action, `LOADING` → `DATA` from a hand-fed fake). When in doubt, attach an inline comment naming the issue (`// see #37`) so a future reader doesn't roll the assertion back to match the buggy shape.

Three of six existing VM test files in this repo (`HomeViewModelTest`, `GiveawaysViewModelTest`, `StoreViewModelTest`) were anchored to the buggy two-value shape for months until #37 surfaced it; the other three (`SearchViewModelTest`, `GameViewModelTest`, `DealDetailsViewModelTest`) were already correct.

**Source:** Wave 2 PR #66 (issue #37 — six ViewModels redundant `stateIn`)

### L-2026-05-01-06 · In `:common:ui`, reach for the Activity via `LocalActivity.current` (null-safe) — never `view.context as Activity`
**TL;DR:** In `:common:ui`, get the host Activity via nullable `LocalActivity.current` — `view.context as Activity` crashes `@Preview` and tests.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** compose, theme, activity, common-ui, preview-safety
**Applies to:** Code in `:common:ui` (and any other shared Compose module) that needs the host Activity to mutate window state — `WindowCompat`, `WindowInsetsController`, status-bar styling, `setRequestedOrientation`, etc.

`(view.context as Activity)` casts inside `LocalView.current` work in `MainActivity` but crash with `ClassCastException` in `@Preview`, `@PreviewParameter`, layout-inspector renders, and Robolectric/instrumented hosts that wrap the view in a non-Activity ContextThemeWrapper. Use `androidx.activity.compose.LocalActivity.current` (returns nullable `Activity?` — null in previews and tests) and skip the styling block when null. This was the fix in `GameDealsTheme`; the same pattern applies anywhere in `:common:ui` that needs Activity-typed access. **Project note:** `:common:ui` did not previously declare `androidx.activity:activity-compose` — it has to be added to `common/ui/build.gradle.kts` (the catalog already pins `activity-compose = 1.11.0`). Adding the dep is the right move: `LocalActivity` is the public, supported API since Activity 1.10; the cast is not.

**Source:** Wave 1 PR #61 (issue #41 — GameDealsTheme casts view.context as Activity)

### L-2026-05-01-05 · Don't mix `System.currentTimeMillis()` with `delay()` in Flow operators — `runTest` virtualizes only `delay`
**TL;DR:** In Flow operators that may run under `TestDispatcher`, pad via parallel `launch { delay(...) }`/join — never `currentTimeMillis()`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** kotlin, coroutines, flow, testing, virtual-time
**Applies to:** Custom Flow operators in `common/.../FlowExtensions.kt` (and any new ones) that implement "at least N millis" / minimum-loading / rate-limit semantics

Under `kotlinx.coroutines.test.runTest` with a `TestDispatcher`, `delay(n)` advances the test scheduler's *virtual* time, but `System.currentTimeMillis()` keeps returning real wall-clock. If an operator measures elapsed time with `System.currentTimeMillis()` and then pads with `delay(remaining)`, the measured elapsed in tests is ~0, so the operator always pads the full window — every test asserting "at least N" passes by accident, and a regression where the inner work overran `N` would not be caught. This was the bug in `mapDelayAtLeast` / `flatMapLatestDelayAtLeast` / `latestDelayAtLeast` (production correct, tests blind). Pattern to use instead: launch the work and a `delay` in parallel inside a `coroutineScope`, then join both — all timing flows through `delay` and stays virtual-time-aware:

```kotlin
transform { v ->
    coroutineScope {
        val deferred = async { f(v) }
        val pad = launch { delay(delayMillis) }
        emit(deferred.await())
        pad.join()
    }
}
```

Generalization: never mix wall-clock measurement with `delay()` in coroutine code that may run under `TestDispatcher`. If you genuinely need real-time timing, inject a `kotlinx.datetime.Clock` (the project already has `CachedResource`-style Clock injection per L-2026-05-01-04's neighbor) and stub it in tests, rather than reaching for `System.currentTimeMillis()`.

**Source:** Wave 2 PR #59 (issue #45 — *DelayAtLeast operators virtual-time)

### L-2026-05-01-04 · Don't `.catch` after `.cachedIn` on a Paging Flow — let `LoadState.Error` surface load failures
**TL;DR:** Don't `.catch` after `.cachedIn(...)` on a Paging Flow — let `LoadState.Error` surface load failures or the Flow pins to its last cache.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** paging, coroutines, flow, viewmodel, error-handling
**Applies to:** Feature ViewModels that expose `Flow<PagingData<T>>` to the UI via `collectAsLazyPagingItems()` — Store, Search, Giveaways, etc.

A `Pager` data Flow does not throw on load failures — those are surfaced as `LoadState.Error` on each `LoadResult`, and the UI's `LazyPagingItems.loadState.refresh/append/prepend` handlers (retry buttons, error rows) already key off them. What *can* throw upstream is the *construction* of the Pager Flow — `dealsRepository.getPagingStoreDeals(storeId)` and similar. A terminal `.catch { logger.fatalThrowable(it) }` placed *after* `.cachedIn(viewModelScope)` swallows that construction-time throwable without re-emitting, so `LazyPagingItems` is left pinned to the last-cached `PagingData` forever — no recovery, no retry, just a silent dead Flow. Removing the `.catch` lets Paging's own `LoadState` machinery handle load errors and lets construction errors propagate to `viewModelScope`'s exception handler where they're not silently buried. If you genuinely need to log construction failures separately, do it *before* `.cachedIn` (where `.catch` can re-emit a sentinel like `PagingData.empty()`), not after.

**Source:** Wave 1 PR #55 (issue #46 — StoreViewModel `.catch` after `.cachedIn`)

### L-2026-05-01-01 · Don't call `SavedStateHandle.toRoute<>()` inside ViewModels — use `savedStateHandle.get<Primitive>("propName")`
**TL;DR:** Read nav args in VMs via `savedStateHandle.get<Primitive>("propName")` — `toRoute<>()` round-trips through `Bundle` and breaks JVM tests.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** navigation, compose, viewmodel, testing
**Applies to:** Feature ViewModels in this project that consume args seeded by typed Compose Navigation destinations (`composable<Destination.X>` / `navController.navigate(Destination.X(...))`)

`SavedStateHandle.toRoute<Destination.X>()` performs an internal `android.os.Bundle` round-trip that is unimplemented on plain JVM, so any unit test that constructs a ViewModel using it fails without an Android runtime. Avoid the call inside ViewModels: use `savedStateHandle.get<Int>("propName")!!` where `propName` matches the `@Serializable` property name on the `Destination` subclass — nav-compose 2.9 populates `SavedStateHandle` with property-name keys for primitive args, so end-to-end typing is preserved at the nav layer (registration + navigate calls stay typed) without dragging Android into ViewModel tests. `NavBackStackEntry.toRoute<>()` at the nav-boundary (composable lambda) is fine — that code only runs on Android.

**Source:** Wave 1 PR #50 (issue #23 — typed Compose Navigation)

### L-2026-05-01-02 · Robolectric is not used in this project — find a JVM-only path or move the test to `androidTest`
**TL;DR:** Don't add Robolectric — find a JVM-only path or move the test to `androidTest`. Robolectric is rejected for this project.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** testing, robolectric, project-policy
**Applies to:** Any decision to add a test dependency that brings an Android runtime into plain JVM unit tests

If a JVM unit test fails because some Android API is unimplemented (most commonly a `SavedStateHandle`/`Bundle` round-trip via `toRoute<>()`, or `android.os.*` stubs), do NOT add Robolectric. Find a JVM-only path: extract the Android-touching call out of the system under test, swap to a primitive-key read on the existing data structure, or move the test to instrumented (`androidTest`). The user has explicitly rejected Robolectric for this project — adding it again will be reverted.

**Source:** Wave 1 PR #50 review

### L-2026-05-01-03 · When dropping a public interface in `:domain` whose `@Inject` impl took internal-typed Daos, mark the renamed concrete class's constructor `internal`
**TL;DR:** When dropping a `:domain` interface whose `@Inject` impl took internal Daos, mark the renamed concrete class `@Inject internal constructor`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** hilt, di, kotlin-visibility, multi-module
**Applies to:** Refactors that delete a public interface in `:domain` whose `@Inject` impl took `internal`-typed Daos / collaborators

While the public interface existed, the impl was free to keep a public constructor because Kotlin only checks visibility against the *declared* type and the API surface ran through the interface. Once the interface is removed, the renamed concrete class's `@Inject constructor(...)` becomes the public API surface and must NOT expose internal types — Kotlin fails compilation with "public function exposes its internal parameter type." Solution: declare it `@Inject internal constructor(...)`. The class stays public so feature modules can name the type for injection; the constructor is module-private so only Hilt's generated code in the same module can call it. This preserves the original `:domain` API narrowness — no Daos leaked.

**Source:** Wave 1 PR #49 (issue #16 — drop single-impl Repository interfaces)

### L-2026-04-30-06 · `_uiState.stateIn(WhileSubscribed, initial)` is the wrong shape — use `_uiState.asStateFlow()`
**TL;DR:** Expose VM `uiState` via `_uiState.asStateFlow()` — `stateIn(WhileSubscribed, …)` wraps produce a spurious LOADING flash on resume.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** viewmodel, stateflow, coroutines, compose
**Applies to:** New screen ViewModels in `feature/*` modules; copying the existing convention from Home/Store/Giveaways/Search/Game/DealDetails ViewModels.

The current convention `private val _uiState = MutableStateFlow(initial); val uiState = _uiState.stateIn(viewModelScope, WhileSubscribed(5000), initial)` is broken on two counts. (1) `MutableStateFlow` is already a hot, conflated, replay-1 StateFlow — `stateIn` produces a *second* derived StateFlow whose state machine is independent of `_uiState`. (2) Under `WhileSubscribed(5000)`, after subscribers drop, `uiState.value` returns the frozen last derived value, and new subscribers see `initialValue` for one frame even when `_uiState` has a different value — producing a spurious "LOADING" flash on resume after >5 s of backgrounding. Use `_uiState.asStateFlow()`. Tracked as issue #37 across all six existing ViewModels.

**Source:** android-bug-hunting-dispatcher audit (issues #30–#48)

### L-2026-04-30-05 · `flow { emitAll(repo.observeXxx()) }` is intentional — preserves synchronous-throw safety
**TL;DR:** Keep `flow { emitAll(repo.observeXxx()) }.catch{}` wrappers — they convert sync repo throws into in-stream errors `.catch` can handle.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, repository, error-handling
**Applies to:** ViewModel code that consumes a repository `observeXxx()` Flow returned by `:domain` repositories

When you see `flow { emitAll(repo.observeXxx()) }.map{…}.catch{…}`, do not "simplify" the wrapper away. The repository's `observeXxx()` returns `Flow<…>` but can throw synchronously during construction (e.g., backing-store read failures surfaced before the first emission). Wrapping in `flow {}` converts that synchronous throw into an upstream exception that downstream `.catch {}` can handle; without the wrapper the throw escapes the `viewModelScope.launch` builder. Examples in `HomeViewModel.loadTopStoreDataFlow` / `loadNewReleases` / `loadGiveaways` and `GiveawaysViewModel.{init, loadGiveaway}`.

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-04 · Keep ViewModel functions Flow-shaped; don't lower to `viewModelScope.launch { try/catch }`
**TL;DR:** Keep ViewModel handlers Flow-shaped (`launch { someFlow.onStart{…}.catch{…}.collect{…} }`) — don't lower to imperative `try/catch`.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, viewmodel, architecture
**Applies to:** Any feature ViewModel function in this project that triggers work in response to a UI event

In this project, ViewModel handlers are deliberately structured as `viewModelScope.launch { someFlow.onStart{…}.map{…}.onError{…}.onCompletion{…}.catch{…}.collect { _state.emit(it) } }`. Do not "modernize" them into imperative `try/catch` blocks even when the body is short — Flow is the medium that composes loading-state emission, `logFlow`, `mapDelayAtLeast` (minimum-loading UX), `onError`/`onCompletion` rethrow semantics, and SharedFlow event side-effects. A bulk lowering across `feature/*` was attempted (b41c34d) and reverted (4f20fa5). Generalizes L-2026-04-27-01 from "which error helper" to "what shape is the function."

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-03 · Test internals from inside the owning module, not via test-only factories
**TL;DR:** Put integration tests inside the module that owns the `internal` collaborators — never add a test-only factory in `main/` to expose them.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** testing, multi-module, mockwebserver, internal-visibility
**Applies to:** Multi-module Android projects where a facade/data-source has `internal` collaborators (Retrofit `*Api` types, KSP-generated artifacts) that an outside test wants to construct against `MockWebServer`.

Do not introduce a test-only `Factory.create(...)` in `main/` to give a downstream module's test access to `internal` collaborators — the cost is permanent (a production-source seam that exists only for tests) and the test pattern stops matching its peers. Keep downstream repository tests mocking the facade interface like their peers, and put the HTTP-wiring/integration test inside the module that owns the impl: same module's `test/` source set sees `internal` for free. Net effect: repository tests stay narrow at the facade boundary, integration coverage lives at the right layer, and no test-only types leak into production source.

**Source:** PR #27 (refactor/15-remote-source-facade)

### L-2026-04-30-02 · GitHub Actions JDK must match `compileOptions.targetCompatibility`, not just the Kotlin toolchain
**TL;DR:** Set CI's `setup-java` `java-version` ≥ `compileOptions.targetCompatibility` — Hilt's `JavaCompile` doesn't use the Kotlin toolchain.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** ci, gradle, hilt, jdk, github-actions
**Applies to:** Android projects with Hilt where the workflow JDK is older than the project's `compileOptions.targetCompatibility`

When the project's `compileOptions.targetCompatibility` (and matching Kotlin `jvmToolchain(...)`) is newer than the runner's JDK, most Java compile tasks succeed because Gradle auto-provisions the toolchain. But `:app:hiltJavaCompileDebug` runs as a plain `JavaCompile` against Gradle's own JVM — *not* the toolchain — and fails with `error: invalid source release: <N>`. Set the workflow JDK (`actions/setup-java@v4` `java-version`) to at least the project's target version; don't rely on the Kotlin toolchain to paper over the mismatch.

**Source:** Wave 1 PR #25 (Hilt KAPT → KSP)

### L-2026-04-30-01 · Ports in `:domain`, adapters in `:remote:*`, wiring in `:app`
**TL;DR:** When the port lives in `:domain` and the `@Provides` impl in `:remote:*`, `:app` must depend on every `:remote:*` so Hilt sees the modules.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** hilt, di, multi-module, ports-and-adapters, architecture
**Applies to:** Any refactor that lifts an interface from a remote/data module up into `:domain` so `:domain` no longer depends on `:remote:*`

When you move a Source/Repository *interface* up to `:domain` (the port) but leave its `@Provides`-bound impl down in `:remote:*` (the adapter), `:domain` correctly drops `implementation(project(":remote:*"))`. But the composition root — `:app` — must then add those `:remote:*` modules as `implementation` deps itself, otherwise Hilt fails at `:app:hiltJavaCompileDebug` with `MissingBinding` for the port type. The Hilt graph follows Gradle visibility; if `:app` can't see the adapter module, it can't see its `@Module @Provides`. This is mechanical, not a design flaw — but it surprises you the first time because the symptom shows up at app-level Hilt compilation rather than at the module boundary.

**Source:** Wave 2 PR #29 (issue #17 — Remote→Domain mapper relocation)

### L-2026-04-27-01 · Prefer flow-native error helpers over `runCatching` inside Flow chains
**TL;DR:** In Flow chains, prefer the `catchAndContinue(default, action)` helper over `runCatching` — keeps error handling on the Flow itself.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-27 · **Tags:** kotlin, coroutines, flow, error-handling
**Applies to:** ViewModel/repository code that wraps a single suspend call inside a Flow pipeline

When lifting a `suspend` call into a Flow, prefer `flow { emit(call()) }` plus the project's `catchAndContinue(defaultValue, action)` helper from `common/logic/util/FlowExtensions.kt` over `runCatching { ... }.getOrNull()`. It keeps error handling on the Flow itself (so cancellation and downstream operators see the right thing) and reuses the codebase's existing helper instead of inlining a raw `.catch { ... emit(default) }`.

**Source:** CommitmentPackagesViewModel FAQ refactor

### L-2026-04-20-01 · On migration branches, map conflicting *features* not *files*
**TL;DR:** When resolving merge conflicts on a long-running migration branch, map them by *feature* — not file-by-file — and decide per feature.
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-20 · **Tags:** merge-conflicts, migration, di, architecture
**Applies to:** Any long-running branch refactoring infrastructure (DI, networking, shared modules) while `main` ships features

Don't resolve merge conflicts file-by-file. First identify which *features* landed on `main` during the migration, then decide per feature: was it already migrated? Does it need to be? Does it stay where it is? This gives a coherent strategy instead of ad-hoc ours/theirs picks that silently break DI wiring. In practice, a dozen conflicting files often reduce to just two or three feature-level decisions.

**Source:** merge conflict resolution

## Archive

### L-2026-05-17-12 · Gradle 9.3.1 K/N test report aggregator chokes on Mokkery 3.x stdout for `throws`-mock tests
**TL;DR:** (superseded) Don't gate CI on iOS test report aggregation under Gradle 9.3.1 + Mokkery 3.x `throws` mocks — aggregator misparses stdout.
**Status:** superseded by L-2026-05-18-05 · **Confidence:** confirmed · **Added:** 2026-05-17 · **Tags:** gradle, kotlin-native, mokkery, testing, ios, tooling-bug
**Applies to:** Any iOS-target unit test (`iosSimulatorArm64Test`) on a module whose Mokkery mocks use the `everySuspend { ... } throws Exception()` (or `every { ... } throws ...`) pattern under Gradle 9.3.1 + Mokkery 3.x + Kotlin 2.3.x

Gradle's K/N test runner fails the task with `"Index N out of bounds for length 2"` (N varies per run) when Mokkery prints the thrown exception's stack trace to stdout — the TeamCity protocol parser appears to misinterpret the `at <frame>` lines. The tests themselves pass: their JUnit-XML reports under `<module>/build/test-results/iosSimulatorArm64Test/` show `failures=0 errors=0`. Workaround for now: do not gate CI on iOS test report aggregation for affected modules; rely on the per-class XML. Repro examples in this repo: `:feature:game GameViewModelTest.error_state` (line 73 `throws Exception()`), `:feature:giveaways GiveawaysViewModelTest`. Modules without throwing mocks (`:common`, `:domain`) pass cleanly.

**Source:** chore/upgrade-kotlin-2.3-kmp · post-bump verification of `./gradlew allTests` + targeted `:feature:game:iosSimulatorArm64Test --rerun-tasks`

### L-2026-05-06-03 · `Dispatchers.IO` is not accessible from `commonMain` in coroutines 1.10.x
**TL;DR:** (superseded) Don't default a commonMain constructor's dispatcher to `Dispatchers.IO` — internal on K/N; inject per platform instead.
**Status:** superseded by L-2026-05-15-07 · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** kmp, coroutines, kotlin-native, dispatchers
**Applies to:** KMP commonMain code that wants an IO-tagged dispatcher for blocking work

`kotlinx.coroutines.Dispatchers.IO` is internal in commonMain in coroutines 1.10.x — only the JVM source set exposes it as public. Don't use it as a default value for a `commonMain` constructor parameter; declare the dispatcher as a required `CoroutineDispatcher` and inject it from each platform's DI module (`Dispatchers.IO` on Android, `Dispatchers.Default` on iOS, where K/N's `IO` aliases to `Default` anyway).

**Source:** Phase-3 Storage commonization — wanted `Dispatchers.IO` as default for `StorageImpl.ioDispatcher`; hit "Cannot access 'val IO' — internal in 'kotlinx.coroutines.Dispatchers'".

### L-2026-05-05-03 · `sentry-kotlin-multiplatform` via Sentry-Cocoa SPM needs an exact version pin and the `Sentry-Dynamic` product
**TL;DR:** (superseded) For sentry-kotlin-multiplatform 0.13.0 via SPM, pin Sentry-Cocoa to exactly `8.36.0` and pick the `Sentry-Dynamic` product.
**Status:** superseded by L-2026-05-05-04 · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** sentry, sentry-kotlin-multiplatform, sentry-cocoa, kmp, kotlin-native, spm, xcode-26, version-skew
**Applies to:** A KMP project integrating `sentry-kotlin-multiplatform` on iOS by adding `https://github.com/getsentry/sentry-cocoa` as a Swift Package Manager dependency in `iosApp.xcodeproj` (rather than via CocoaPods)

`sentry-kotlin-multiplatform`'s cinterop layer references specific Objective-C class symbols (`_OBJC_CLASS_$_SentrySDK`, `_OBJC_CLASS_$_SentryEnvelope`, `_OBJC_CLASS_$_SentryDependencyContainer`, etc.). Those symbols only exist in narrow Sentry-Cocoa version windows: Sentry-Cocoa 9.x rewrote them as Swift classes (mangled `__TtC6Sentry…` symbols), and even within 8.x successive patches keep deprecating more public surface to Swift. SPM rules like "Up to Next Major" silently pick a too-new patch and break the link with `Undefined symbols for architecture arm64: _OBJC_CLASS_$_Sentry…`. Pin to **Exact Version** matching the sentry-kotlin-multiplatform release's CocoaPods Podfile.lock — for `sentry-kotlin-multiplatform:0.13.0` that is **`8.36.0`**.

Two more SPM gotchas worth knowing up front: (1) pick the **Sentry-Dynamic** SPM product, not `Sentry`. Xcode 26's SwiftBuild treats the static `Sentry` product as a "codeless framework", strips its Mach-O during embedding, and replaces it with a stub binary — so the linker has no symbols to resolve even when search paths are correct. The build log gives this away with `Injecting stub binary into codeless framework`. (2) Confirm the actual binary on disk with `nm -gU …/Build/Products/Debug-iphonesimulator/Sentry.framework/Sentry | grep <expected class>`; a missing symbol there is the difference between "wrong product variant" and "wrong upstream version" — the former produces no symbols, the latter produces *different* symbols (Swift-mangled).

**Source:** Phase-7e iOS Sentry wire-up — three rounds of unresolved-symbol errors before isolating the dynamic product and the exact 8.36.0 pin.

### L-2026-05-05-02 · Serialize racy cross-module Gradle tasks via shared BuildService, not `--max-workers=1`
**TL;DR:** (superseded) Serialize racy cross-module Gradle tasks via a shared `BuildService` with `maxParallelUsages.set(1)` — not `--max-workers=1`.
**Status:** superseded by L-2026-05-06-01 · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** gradle, kmp, kotlin-native, ios, build-service, test-parallelism
**Applies to:** Any KMP project where the same task type (e.g. `KotlinNativeHostTest`/`iosSimulatorArm64Test`) runs concurrently across modules and races on a non-thread-safe writer — the Gradle 9.1 + Kotlin 2.2.21 test-result XML reporter is the canonical case, failing with `Could not write XML test results for ... .xml` while the test itself passed.

`--max-workers=1` works as a workaround but throttles the entire build. Better: register a marker `BuildService` with `maxParallelUsages.set(1)` at the project level (in a convention plugin so every consuming module hooks in), then `tasks.withType(<TaskType>::class.java).configureEach { usesService(svc) }`. Gradle permits only one of those tasks to run at a time across the build while leaving every other task free to parallelize. Same pattern works for any cross-module serialization need (shared simulator slots, exclusive emulators, native code-signing).

**Source:** Phase-7b iOS polish — eliminating the `--max-workers=1` workaround we kept passing for `iosSimulatorArm64Test` runs.
