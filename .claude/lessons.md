# Lessons Learned

Condensed, structured lessons from past development sessions. Claude reads this file at the start of each session (via a separate skill) so it can apply past learnings without re-deriving them.

Each lesson has an immutable ID. When a lesson is superseded or turns out to be wrong, it is moved to `## Archive` with an updated `Status` line — its content is never rewritten. This preserves the audit trail.

**Claude:** apply lessons from `## Active` only. Consult `## Archive` only if something appears contradictory and you need the history.

## Active

### L-2026-05-11-02 · Compose host bootstrap must be applied in BOTH `:app:MainActivity` AND `:iosApp:MainViewController`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-11 · **Tags:** kmp, compose-multiplatform, koin, composition-local, ios, dual-host
**Applies to:** Any new root-scope wiring — Koin modules added to `startKoin { modules(...) }`, `CompositionLocalProvider(LocalX provides …)` around the NavHost — introduced for a feature

The project has two roots of composition: `app/src/main/java/.../MainActivity.kt` (Android, via `setContent { GameDealsTheme { … } }`) and `iosApp/src/iosMain/.../MainViewController.kt` (iOS, via `ComposeUIViewController { App() }`). Each registers its own Koin module list and installs its own `CompositionLocalProvider`s. Anything host-scoped — a new Koin module, a `LocalX` provider — must be added to **both** files; the Android-side build will compile cleanly even if iOS is forgotten.

When combined with a no-op `CompositionLocal` default (see L-2026-05-11-01), forgetting the iOS host presents as "feature silently does nothing on iOS" rather than a crash. A diagnostic helper: log a Sentry breadcrumb *before* emitting the side-effect event from the ViewModel — if the breadcrumb fires but the platform action doesn't, the host-wiring step is the suspect.

**Source:** PR #135 (share feature) — iOS share path was silently no-op until `MainViewController.kt` got the matching `CompositionLocalProvider(LocalPlatformActions provides rememberPlatformActions())`.

### L-2026-05-11-01 · `staticCompositionLocalOf { error(...) }` crashes `@Preview`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-11 · **Tags:** compose, composition-local, preview, kmp
**Applies to:** Any new `staticCompositionLocalOf<T>` introduced for a host-provided service in `:common:ui` (or anywhere a `@Preview` may render a consumer)

The textbook default `staticCompositionLocalOf<T> { error("X not provided") }` crashes every `@Preview` that renders a composable reading `LocalX.current` — even when the preview never invokes the underlying side effect. For locals consumed inside shared screens (`DealBottomSheet`, screens with `@Preview` annotations) prefer a no-op singleton default (e.g. `object NoOpPlatformActions : PlatformActions { override fun share(text: String) = Unit }`) and let host-level `CompositionLocalProvider` bind the real impl.

Trade-off: "fail-loud on missing provider" is lost. A missing host wiring becomes a silent runtime no-op, not a crash. See L-2026-05-11-02 for the mitigation.

**Source:** PR #135 (share feature) — initial `error(...)` default caught in review.

### L-2026-05-06-05 · `TopAppBarDefaults.pinnedScrollBehavior` allocates per recomposition via the default `canScroll` lambda, not the wrapper
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** compose, recomposition, top-app-bar, material3, stable-lambda
**Applies to:** Any code calling `TopAppBarDefaults.pinnedScrollBehavior(...)` (or `enterAlwaysScrollBehavior`, `exitUntilCollapsedScrollBehavior`) directly inside a composable body

`TopAppBarDefaults.pinnedScrollBehavior(state, canScroll)` is itself `@Composable` and already does `remember(state, canScroll) { PinnedScrollBehavior(...) }` internally. So you cannot wrap it in a caller-side `remember(state) { TopAppBarDefaults.pinnedScrollBehavior(state) }` — that fails with `Composable invocations can only happen from the context of a Composable function`.

The actual cause of per-recomposition allocation is the default `canScroll = { true }` parameter — a fresh lambda each recomposition, which invalidates Material3's internal remember key. Hoist `canScroll` into a stable `remember { { true } }` (or pass `canScroll = remember { { true } }` inline). Same applies to other Material3 helpers that take a default lambda parameter — check before assuming "wrap in `remember`" is the fix.

**Source:** 2026-05-06-bug-hunt-severity-low batch (issue #125 / PR #134). The original bug-hunt finding misidentified the cause; worker investigated Material3 source to find the real one.

### L-2026-05-06-04 · Test mocking library is Mokkery, not MockK
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** testing, mokkery, mockk, kmp, commontest
**Applies to:** Any test in a `*/src/commonTest/**` source set; by convention, all tests in this codebase

This codebase uses Mokkery (`dev.mokkery:mokkery`) for mocking, not MockK. Mokkery is KMP-compatible; MockK ships JVM-only and cannot be used in `commonTest`. Use `every { … } returns …` for non-suspend stubs, `everySuspend { … } returns …` for suspend stubs, `throws` for exception stubs, and matchers like `any()` / `eq(...)`. The canonical examples live in `feature/*/src/commonTest/**/ui/*ViewModelTest.kt` (e.g., `StoreViewModelTest`, `GiveawaysViewModelTest`).

`docs/patterns/testing.md` still says "MockK Everywhere; No Hand-Rolled Fakes" — that doc was surveyed at SHA `31a89bc` on 2026-05-03, before the KMP migration completed. The pattern is stale; this lesson takes precedence until the patterns doc is refreshed. JVM-only tests outside KMP modules (none currently exist in this project, but possible in `:app` androidTest) could theoretically still use MockK, but default to Mokkery for consistency.

**Source:** Wave 1 of campaign `2026-05-06-bug-hunt-severity-medium` — issue #124 / PR #132. The worker prompt and issue body referenced MockK based on the stale patterns doc; the worker noticed the existing test file used Mokkery and adapted.

### L-2026-05-06-03 · `Dispatchers.IO` is not accessible from `commonMain` in coroutines 1.10.x
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** kmp, coroutines, kotlin-native, dispatchers
**Applies to:** KMP commonMain code that wants an IO-tagged dispatcher for blocking work

`kotlinx.coroutines.Dispatchers.IO` is internal in commonMain in coroutines 1.10.x — only the JVM source set exposes it as public. Don't use it as a default value for a `commonMain` constructor parameter; declare the dispatcher as a required `CoroutineDispatcher` and inject it from each platform's DI module (`Dispatchers.IO` on Android, `Dispatchers.Default` on iOS, where K/N's `IO` aliases to `Default` anyway).

**Source:** Phase-3 Storage commonization — wanted `Dispatchers.IO` as default for `StorageImpl.ioDispatcher`; hit "Cannot access 'val IO' — internal in 'kotlinx.coroutines.Dispatchers'".

### L-2026-05-06-02 · `compose.material3` does NOT bring in `material-icons-core`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** compose, compose-multiplatform, gradle, dependencies
**Applies to:** Compose Multiplatform modules referencing `androidx.compose.material.icons.Icons` (e.g. `Icons.Filled.ArrowBack`)

The `Icons` API surface (`androidx.compose.material.icons.*`) is in `material-icons-core`, which is shipped only transitively via `compose.material` (legacy Material 1) or `compose.materialIconsExtended`. `compose.material3` does NOT pull it in. Before dropping `compose.materialIconsExtended` "because we only use core icons like ArrowBack/Search", verify there's another path — otherwise the build snaps with "Unresolved reference 'icons'".

**Source:** Phase-2 Compose-runtime lift in convention plugin — assumed material3 included the icons surface; broke the build, restored extended.

### L-2026-05-06-01 · iOS-simulator test serializer BuildService must bind to `KotlinNativeTest`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-06 · **Tags:** gradle, kmp, kotlin-native, ios, build-service
**Applies to:** Convention-plugin BuildService that serializes `iosSimulatorArm64Test` across modules

When wiring the cross-module test-serialization BuildService from L-2026-05-05-02, register against `KotlinNativeTest::class.java` — not `KotlinNativeHostTest`. The simulator tests are `KotlinNativeSimulatorTest`, the host tests are `KotlinNativeHostTest`, and both extend `KotlinNativeTest`. Binding to the host subclass leaves simulator tasks unserialized, so the XML race still fires intermittently and looks like a one-module flake instead of a known race.

**Source:** Code-quality cleanup of feature/kmp-migration — prior selector had been masking the race for weeks.

### L-2026-05-05-04 · `sentry-kotlin-multiplatform` SPM-only integration breaks Kotlin/Native test linking
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** sentry, sentry-kotlin-multiplatform, sentry-cocoa, kmp, kotlin-native, spm, gradle, version-skew
**Applies to:** A KMP project considering integrating `sentry-kotlin-multiplatform` on iOS — choosing between SPM, CocoaPods, or the SDK's official Gradle plugin

Adding Sentry-Cocoa via Xcode SPM gets the iOS *app* linking, but **Kotlin/Native test executables (`linkDebugTestIosSimulatorArm64`) fail with `framework 'Sentry' not found`** — Gradle's link step has no path to the SPM-managed framework, since that copy lives only inside Xcode's DerivedData. Every consuming module's iOS tests break: lifting `sentry-kotlin-multiplatform` to `commonMain` of a `:logging`-style module poisons the link path for `:common`, `:domain`, `:remote`, and every feature module that transitively depends on it. Either apply the official `io.sentry.kotlin.multiplatform.gradle` plugin (which auto-downloads Sentry-Cocoa for native targets), use the CocoaPods integration the SDK was designed for, or keep Sentry confined to `androidMain` with a `[Sentry stub]` listener on iOS.

Two SPM gotchas worth preserving in case you return to that path: (1) pin Sentry-Cocoa to the **exact version** sentry-kotlin-multiplatform's cinterop was built against — `0.13.0` requires `8.36.0`. Newer 8.x and 9.x rewrite the referenced ObjC classes (`SentrySDK`, `SentryEnvelope`, `SentryDependencyContainer`) as Swift, breaking cinterop with `Undefined symbols: _OBJC_CLASS_$_Sentry…`. (2) Pick the **`Sentry-Dynamic`** SPM product, not `Sentry`. Xcode 26 treats the static product as a "codeless framework" (`Injecting stub binary into codeless framework` in the build log), strips its Mach-O during embedding, and leaves the linker with nothing to resolve.

**Source:** Phase-7e iOS Sentry wire-up — SPM integration succeeded for the app but broke `:common:linkDebugTestIosSimulatorArm64` and every other iOS test target; reverted in `6037a7a`.

### L-2026-05-05-01 · `kotlin.test` annotations don't resolve in `commonMain` of a non-test KMP fixtures module
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** kmp, kotlin-test, source-sets, testing, fixtures-module
**Applies to:** A KMP "test fixtures" module (e.g. `:testing`) that publishes test helpers via its `commonMain` (consumed by other modules as `commonTestImplementation(project(":testing"))`) — specifically attempts to put `@BeforeTest`/`@AfterTest`-annotated methods on a superclass in that `commonMain`.

Adding `implementation(kotlin("test"))` to the fixtures module's `commonMain.dependencies` puts the artifact on the classpath, but `kotlin.test.BeforeTest`/`AfterTest` are `expect annotation class` decls that only resolve through platform-specific variants (`kotlin-test-junit`, `kotlin-test-junit5`, `kotlin-test-native`), which `kotlin("test")` pulls in only when declared in a *test* source set — not commonMain. Symptom: `Unresolved reference 'BeforeTest'` at `:fixtures-module:compileDebugKotlinAndroid` even after the dep is declared. Workaround: keep the superclass in commonMain providing plain helper methods (`protected fun installMainDispatcher() = Dispatchers.setMain(testDispatcher)`); subclasses in their consuming modules' commonTest carry the `@BeforeTest`/`@AfterTest` annotations themselves. Two annotated one-liners per subclass is the modest cost for not fighting source-set semantics.

**Source:** Phase-A6 simplify pass — attempted to lift `@BeforeTest`/`@AfterTest` into `:testing/commonMain/.../MainDispatcherTest.kt` for 6 feature ViewModel tests.

### L-2026-05-04-07 · Pick JetBrains-AndroidX-fork versions by reading the iOS dep tree first
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, kotlin-native, jetbrains-androidx-fork, version-skew, gradle
**Applies to:** Adding any `org.jetbrains.androidx.*` artifact (navigation-compose, lifecycle-viewmodel, savedstate, etc.) to a KMP project that already has them transitively

The JetBrains AndroidX KMP forks publish dozens of alpha/beta versions, most of which crash at runtime on Native with `IrLinkageError` if the fork's pinned transitive (e.g., `org.jetbrains.androidx.savedstate`) doesn't match the version your other deps already pull in. Don't pick by latest-Maven or by guessing; run `./gradlew :iosApp:dependencies --configuration iosSimulatorArm64CompileKlibraries | grep -E '<artifact-prefix>' | sort -u` first, identify the existing transitive version (e.g., `lifecycle-viewmodel:2.9.0-beta01 -> 2.9.6`), then declare the new artifact at *that* version. Skipping this step cost me three abandoned alphas in 7.6 before the working `2.9.0-beta01` (matched lifecycle's own published lineage).

**Source:** Phase 7.6 retry — JetBrains nav-compose fork. Same lesson applies to any future `org.jetbrains.androidx.*` adoption.

### L-2026-05-04-06 · `NSLog` from Kotlin/Native must use the single-arg pattern — varargs don't bridge
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kotlin-native, ios, nslog, foundation, interop
**Applies to:** Any Kotlin/Native iOS code calling `platform.Foundation.NSLog` with format-and-args style

`NSLog("%@", line)` from Kotlin/Native crashes at runtime with `EXC_BAD_ACCESS` the first time a log line lands. Kotlin/Native's Foundation binding types `NSLog` as `fun NSLog(format: String, vararg args: Any?)` but doesn't bridge Kotlin `String` to Objective-C `NSString *` through the C variadic ABI, so the slot for `%@` receives a garbage pointer. Use the single-arg form: pass the application content as the format string itself, pre-escape `%` characters (`line.replace("%", "%%")`) so user content can't be reinterpreted as format specifiers. Doesn't surface during compile/link — only at runtime when the affected code path actually logs.

**Source:** Phase 7.2 — first iOS log line through `IosConsoleLoggingListener` after HomeScreen wired up.

### L-2026-05-04-05 · Koin 4.0 references Android-only AndroidX lifecycle symbols on iOS
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** koin, kmp, kotlin-native, lifecycle, irlinkageerror, koin-compose-viewmodel
**Applies to:** Any KMP project using `koin-compose-viewmodel` to call `koinViewModel()` from a Composable that runs on iOS

Koin 4.0.0's `AndroidParametersHolder` references `androidx.lifecycle.SavedStateHandle` (Google's Android-only artifact) directly. On iOS the classpath has the JetBrains fork `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:*` instead, so first invocation of `koinViewModel()` from any Composable rendered on iOS throws `IrLinkageError: No class found for symbol 'androidx.lifecycle/SavedStateHandle|null[0]'`. Compile/link succeeds; the failure only appears when the affected code path runs (Composable rendering an `internal viewModel: T = koinViewModel()` default). Bump Koin to 4.1.0+, which dropped the direct AndroidX reference. Same family of failure as L-2026-05-04-04 (Ktor skew) — JVM resolves per-class at link time and silently muddles through; Native carries klib symbol fingerprints and crashes hard at runtime.

**Source:** Phase 6.7d — first iOS render of a real feature Composable.

### L-2026-05-04-04 · Ktor version skew silently breaks Native, silently works on JVM
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** ktor, kmp, kotlin-native, version-skew, irlinkageerror
**Applies to:** Any KMP project pulling Ktor in alongside transitives (sandwich-ktor, coil3-network-ktor3, etc.) that may force a Ktor version higher than the BOM in `libs.versions.toml`

JVM resolves missing/changed symbols at link time per-class, so a transitive forcing `ktor-client-core` from 3.0.3 → 3.3.0 while `ktor-client-darwin:3.0.3` stays pinned is invisible on Android. Kotlin/Native klibs carry exact symbol fingerprints — the same skew turns into `kotlin.internal.IrLinkageError` at runtime as soon as the affected code path runs (e.g., the first HTTP response body). Manifested for us as `Function 'dropCompressionHeaders' can not be called: No function found for symbol ...`. Fix: align the BOM (`ktor = "3.3.0"`) so all Ktor artifacts agree. Watch for this on every Ktor major version bump and any new dep that Ktor-uses internally.

**Source:** Phase 6.7c — first iOS network round-trip surfaced the skew.

### L-2026-05-04-03 · Two-Koin-module split is the right shape for platform-specific construction in KMP
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, koin, di, room
**Applies to:** A KMP module that needs platform-specific construction (database Builder, file-path resolution, platform context, native handle) — i.e. a binding that can't live in commonMain because it touches `androidContext()`/`NSHomeDirectory()`/etc.

Prefer two Koin modules over `expect`/`actual` for this case: `xModule` in commonMain owns all portable wiring (converters, DAOs, repositories, the final bound type that customizes a Builder and `.build()`s it), `xAndroidModule` in androidMain owns just the platform seed binding (`single<RoomDatabase.Builder<T>> { Room.databaseBuilder<T>(androidContext(), name) }`). The consumer registers both modules at `startKoin`. iOS later registers its own `xIosModule` providing the same bound type. Avoids `expect`/`actual` ceremony — DI is already a runtime indirection, no need to add a compile-time one. Test overrides target the *final* type (e.g. override `single<DomainDatabase>` with `inMemoryDatabaseBuilder`), bypassing the Builder graph entirely so it doesn't need a test double.

**Source:** Phase 5.16 — `:domain` DI module split.

### L-2026-05-04-02 · `BoxWithConstraints { maxWidth < 600.dp }` substitutes for `WindowWidthSizeClass` in CMP
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, compose-multiplatform, adaptive-layout
**Applies to:** Lifting an Android Composable that branches on window size class to commonMain

`androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()` and `WindowWidthSizeClass` are Android-only. For a screen that just needs "is this a phone or wider," wrap the layout in `BoxWithConstraints` and branch on `maxWidth < 600.dp` (or whatever breakpoint matters). It's a literal substitute, no behavior change on Android, works on every CMP target. For multi-breakpoint adaptive layouts the call shape gets uglier — but for the binary compact-vs-not case this is the path of least resistance.

**Source:** Phase 5.10 — `:feature:game` migration.

### L-2026-05-04-01 · `androidx.paging` is split: `paging-common` is KMP, `paging-compose` is not
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-04 · **Tags:** kmp, paging, compose-multiplatform
**Applies to:** Migrating a feature module that uses paging to KMP

When migrating a paging-using feature, `androidx.paging:paging-common` (3.3+) is multiplatform-capable so types like `PagingData`, `LoadState`, and `cachedIn` work in commonMain — a paging-aware ViewModel can move freely. `androidx.paging:paging-compose` (`LazyPagingItems`, `collectAsLazyPagingItems`, `itemKey`) is Android-only; any Composable consuming it stays androidMain. JetBrains maintains a multiplatform fork, but adopt it only when an iOS consumer actually motivates it — until then, the natural cut is "ViewModel commonMain, Screen androidMain" and that has zero design overhead.

**Source:** Phase 5.13 — `:feature:store` migration.

### L-2026-05-03-06 · Same-named `.kt` files across KMP source sets generate duplicate JVM class names
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** kmp, kotlin, jvm, source-sets, file-naming
**Applies to:** KMP modules where commonMain and androidMain (or any two source sets reaching the same target) both contain a top-level-functions file with the same filename — typical when splitting a file into a commonMain core + androidMain platform-specific implementation

Kotlin generates one `<FileName>Kt` JVM class per top-level-functions file. When `Theme.kt` exists in both `commonMain/.../theme/` and `androidMain/.../theme/`, both compile to `ThemeKt.class` on the Android target → `Duplicate JVM class name 'pm/bam/gamedeals/common/ui/theme/ThemeKt' generated from: ThemeKt, ThemeKt`. Add `@file:JvmName("AndroidTheme")` (or rename the file `Theme.android.kt` if you prefer the convention) to one side. Doesn't trip when the file contains only classes/objects (those have per-class JVM names) or when using `expect/actual` (those merge to one JVM class on resolution). Watch for it during file-splits where one half stays androidMain because of platform deps.

**Source:** Phase 5.4e — Theme.kt split (schemes/locals to commonMain; `GameDealsTheme` Composable stays androidMain).

### L-2026-05-03-05 · `pluginManager.apply()` in precompiled convention plugins requires `implementation` deps, not `compileOnly`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** gradle, build-logic, convention-plugins, kmp, compose-multiplatform
**Applies to:** Precompiled convention plugins in `:build-logic:convention` that call `pluginManager.apply("third.party.id")` from inside `Plugin<Project>.apply()`

A `compileOnly(libs.foo.gradle.plugin)` in build-logic is enough for the convention's Kotlin source to compile against the plugin's APIs (configuring `ComposeExtension`, etc.), but at apply-time on a consuming module Gradle fails with `Plugin with id 'foo' not found`. The runtime classpath of the *convention plugin* needs the third-party plugin's classes, so switch to `implementation(...)`. `kotlin-gradle-plugin` and `compose-compiler-gradle-plugin` slip past this trap because they're already on the build's classpath via the project-level plugins block — third-party plugins like `org.jetbrains.compose` are not, and need the explicit `implementation`. Symptom catches you the *first* time a module applies the convention; build-logic's own compile is green so you don't spot it until consumer-build failure.

**Source:** Phase 5.4a — `:common:ui` first module to apply `kmp.library.compose`; build-logic had `compose-multiplatform-gradle-plugin` as `compileOnly`.

### L-2026-05-03-04 · Removing a Hilt module (esp. `hilt-navigation-compose`) silently strips Compose runtime from KSP classpath
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** ksp, room, hilt-removal, compose-runtime, transitive-deps, kmp
**Applies to:** Modules removing `androidx.hilt:hilt-navigation-compose` (or any Hilt-navigation dep) that contain Room `@Entity` classes annotated with Compose stability annotations like `@Immutable`.

`hilt-navigation-compose` transitively brings `androidx.compose.runtime:runtime`. Modules that never explicitly declared a Compose runtime dep — e.g. `:domain`, which only uses `@Immutable` for stability hints on Room entities — were getting it for free through the Hilt chain. When you drop Hilt during a DI migration, the Compose runtime dep disappears from compile classpath; **Room's KSP processor then fails with `[MissingType]: Element 'pm.bam.gamedeals.domain.models.Deal' references a type that is not present` on every entity** — a misleading message that points at the entity, not at the missing annotation. Fix shape: add `implementation(libs.androidx.compose.runtime)` (i.e. `androidx.lifecycle:lifecycle-runtime-compose`, which brings the actual Compose runtime transitively) to any non-feature module that uses `@Immutable` and previously inherited Compose runtime through Hilt. **Audit checklist when removing Hilt from a module:** `gradle :module:dependencies | grep compose.runtime` *before* the removal; if any Compose-runtime artifact appears via a Hilt path, declare it explicitly on the module before dropping Hilt.

**Source:** Phase 4.2 of the KMP migration; `:domain` → Koin; bisected during Hilt removal.

### L-2026-05-03-03 · Ktor `parameter()` always encodes — use `encodedParameters` for already-encoded values
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** ktor, retrofit-migration, url-encoding, networking, kmp
**Applies to:** Any Retrofit `@Query("name", encoded = true)` — or any other API surface where a query/path value is supplied already percent-encoded — being ported to Ktor `HttpRequestBuilder.parameter(...)`.

Ktor's `parameter("name", value)` always URL-encodes the value, with no opt-out flag. For values that arrive already percent-encoded (e.g. CheapShark serves dealIDs as `…%3D`), this re-encodes `%` → `%25` and produces `…%253D` on the wire, which servers decode to a literal `%3D` and reject as a 404. The Ktor equivalent of Retrofit's `@Query("id", encoded = true)` is `url { encodedParameters.append("id", id) }` — `URLBuilder.encodedParameters` is the pre-encoded sibling of `URLBuilder.parameters` and is part of the multiplatform URL builder, so it works on iOS too. **Migration audit:** during a Retrofit → Ktor port, grep for every `@Query(...encoded = true)` and `@Path(...encoded = true)` in the pre-migration tree and convert each to `encodedParameters.append`/`encodedPathSegments`. The default `parameter()`/`path()` lookalikes silently drop the semantic.

**Source:** Phase 4 smoke-test catch on `DealsApi.getDeal` (commit `012d27b`); pre-Phase-3 had `@Query("id", encoded = true)` (verified at `git show a8bacbd^`); regression test in `DealsApiTest`.

### L-2026-05-03-02 · Ktor `Logging { level = LogLevel.BODY }` + OkHttp engine hangs `body<T>()`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-03 · **Tags:** ktor, ktor-logging, okhttp-engine, content-negotiation, networking, kmp
**Applies to:** Any HttpClient that installs both `Logging` at `LogLevel.BODY` and `ContentNegotiation`, on the OkHttp engine

The Logging plugin at `LogLevel.BODY` reads the response body to log it; on OkHttp's engine that body is a one-shot stream and the read consumes it. `ContentNegotiation`'s subsequent `body<T>()` then waits indefinitely for bytes that have already been read. Symptom is exact: the `Ktor REQUEST: ...` line logs, then nothing — no `RESPONSE`, no exception, no timeout, no parse error. Use `LogLevel.HEADERS` as the default — same diagnostic value (request line, response status, headers) without consuming the body. If you genuinely need bodies in logs, use Ktor's `observeRequest`/`observeResponse` callbacks which don't consume the stream.

**Source:** Phase 3 of the KMP migration, swapping Retrofit → Ktor in `:remote:*` modules.

### L-2026-05-03-01 · Module that opts out of the convention plugin silently loses inherited test-runtime deps
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
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** testing, coroutines, flow, room, race-conditions, test-fixtures
**Applies to:** Test stubs for repository methods that return a hot `Flow` in production (Room `@Query` flows, `MutableSharedFlow`-backed signals, `callbackFlow`-driven sources) — i.e. anywhere the production flow does not complete

The `GiveawaysViewModelTest` originally stubbed `observeGiveaways()` with `flowOf(listOf(...))`, which completes after exactly one emission. Production's Room flow emits *every time the table is invalidated* — never completing. The parallel-collector race in `GiveawaysViewModel.loadGiveaway` (issue #72) needed a *second* emission to manifest, so the test never reproduced the bug despite covering the call site. Default test stub for hot sources should be `MutableSharedFlow` (or the equivalent `flow { while (isActive) emitAll(channel) }` shape) so tests can drive multiple emissions and exercise race-condition behaviour. Reserve `flowOf(...)` for fixtures that are genuinely terminal/one-shot (e.g. `flow.first()` consumers).

This is a structural test-shape rule, not a per-bug fix — applying it eagerly across `feature/*/src/test/.../ui/*ViewModelTest.kt` files would surface other latent races. Pairs with L-2026-05-01-07 (which governs the *expected emission count* under `UnconfinedTestDispatcher`).

**Source:** Wave 1 PR #86 (issue #72 — `GiveawaysViewModel.loadGiveaway` leaks long-lived Room collector)

### L-2026-05-02-04 · `try { ... } catch (Throwable)` blocks containing suspending work must rethrow `CancellationException` first
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
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** gradle, modularization, kotlinx-collections-immutable, dependency-graph, build-config
**Applies to:** Any refactor that retypes a public field on a `:domain` (or other shared) data class to use a third-party type — `ImmutableList<T>`, `Instant`, `BigDecimal`, etc. — where consumer modules will call extension functions on the type

Gradle `implementation(libs.kotlinx.collections.immutable)` in `:domain` is enough for `:domain` itself to compile, but it does NOT propagate the dep to modules that depend on `:domain`. The *type* (`ImmutableList<GameDeal>`) flows through the public API surface fine — that's what `api` vs `implementation` controls — but **extension functions** on that type (`kotlinx.collections.immutable.toImmutableList`, `.persistentListOf`, etc.) are imported from the third-party library directly, and that import only resolves if the consumer module declares the dep itself. Symptom: `:domain` test runs green, but `:remote:cheapshark` (or `:feature:game`, or any other module that builds a value of the new type via an extension) fails compile with `Unresolved reference: toImmutableList`. Fix: add `implementation(libs.kotlinx.collections.immutable)` to every consumer module that imports an extension on the type. **Planner heuristic:** when retyping a `:domain` field, walk the import-graph of consumer modules and pre-add the dep — don't wait for the build to surface it module-by-module.

Same gotcha will apply to any future migration of this shape (e.g. `kotlinx.datetime.Instant` if/when it spreads, or a hypothetical `:domain` adoption of `Arrow`'s `Either`).

**Source:** Wave 2 PR #83 (issue #80 — GameDetails.deals → ImmutableList migration); also surfaced in PR #82 (issue #79) where `:feature:giveaways` already had the dep so the gotcha was masked

### L-2026-05-02-02 · `@Immutable` + `ImmutableList<…>` on every domain model used as a composable parameter
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** compose, stability, recomposition, immutable-collections, kotlinx-serialization
**Applies to:** `data class` types in `:domain` whose fields include raw `kotlin.collections.List<…>` and that are read inside a composable (or nested inside another type that is)

Compose marks raw `List` unstable, so any composable taking such a type can't skip on recomposition even when the value is bit-identical. Pattern: retype every `List<…>` field to `kotlinx.collections.immutable.ImmutableList<…>`, annotate the class `@Immutable`, and replace `.toMutableList().map { … }` (which produces a regular `List`) with `.map { … }.toImmutableList()` at every call site. The catalog already pins `kotlinx-collections-immutable` (since #38); add `implementation(libs.kotlinx.collections.immutable)` to whichever module needs it (`:domain`, `:common:ui`, `feature/*`).

**Important:** kotlinx-serialization 1.9.0 (the catalog version) supports `ImmutableList` natively — `@Serializable` domain models adopt it without a custom serializer, and `Saver` round-trips through `Json.encodeToString` keep working. **Operational note:** any two issues that both add `kotlinx-collections-immutable` to the same module's `build.gradle.kts` will conflict — schedule them in separate waves of the bug-hunt campaign.

**Source:** Wave 1 PR #82 (issue #79 — GiveawaySearchParameters @Immutable + ImmutableList migration); also #38 (PR #70) and pending #80

### L-2026-05-02-01 · `MutableStateFlow.update { it.copy(...) }` for field-level merges; `emit(...)` only for full-state replacements
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-02 · **Tags:** viewmodel, stateflow, coroutines, race-conditions, atomicity
**Applies to:** Feature ViewModel handlers in `feature/*/src/main/.../ui/*ViewModel.kt` that mutate `_uiState`

Three patterns coexist in this codebase, and they are not interchangeable. (1) `_uiState.value.copy(...)` followed by `_uiState.emit(...)` is a read-modify-write — concurrent coroutines can read the same snapshot, mutate disjoint fields, and clobber each other on emit. Always replace with `_uiState.update { it.copy(...) }`, the documented atomic CAS helper that retries on contention. (2) `_uiState.update {}` works *inside* Flow chains too: `.onStart { _uiState.update { it.copy(state = LOADING) } }` is the right shape, not `.onStart { _uiState.emit(_uiState.value.copy(...)) }`. (3) Keep `.collect { _uiState.emit(it) }` *as-is* when the upstream Flow already produces the complete next state — that's a full-state replacement, not a field-level merge, and there's no race to fix.

Side note: when the LOADING transition moves out of a flow chain into a side-effect `update {}`, the chain now emits nothing — `logFlow` may need an explicit `flow<Unit>` type pin to keep inference happy.

**Source:** Wave 1 PR #81 (issue #78 — ViewModels read-modify-write `_uiState.value.copy(...)`)

### L-2026-05-01-09 · `AndroidView` lifecycle: hoist clients via `remember`, wire `onRelease` for true teardown
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
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** android, agp, buildconfig, debug-gate, gradle
**Applies to:** Any module wanting a single "is this a debug build" boolean — `StrictMode` policies, debug-only logging, dev-only feature flags

This project's modules don't enable `android.buildFeatures.buildConfig`, so AGP 8 doesn't generate `BuildConfig` for them. Reaching for `BuildConfig.DEBUG` would force opting in to BuildConfig generation across that module just for one boolean (and add a build-script change with downstream effects on R8 metadata, Configuration Cache invalidation, etc.). Use `(applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` instead — equivalent semantics (debug build types implicitly set `debuggable=true`), no `buildConfig` opt-in. This is the pattern used in `GameDealsApplication.onCreate` to gate StrictMode (PR #63).

**Source:** Wave 1 PR #63 (issue #42 — Storage suspend / StrictMode gate)

### L-2026-05-01-07 · ViewModel emission tests under `UnconfinedTestDispatcher` should expect `size == 1` for steady state — not `[initial, current]`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** viewmodel, stateflow, coroutines, testing, unconfined-test-dispatcher
**Applies to:** Tests in `feature/*/src/test/.../ui/*ViewModelTest.kt` that collect a ViewModel's `uiState`/`screenState` flow into a list (e.g. `viewModel.uiState.take(N).toList()`) under `runTest` with the project's standard `UnconfinedTestDispatcher` rule

Under `UnconfinedTestDispatcher`, the ViewModel's `init { ... viewModelScope.launch { ... } }` block drains synchronously *before* the test starts collecting, and `MutableStateFlow` is conflated and replay-1. A correctly-shaped ViewModel therefore emits exactly one value to the test collector: the steady-state value after `init` finished. If a test asserts `size == 2, [initialValue, currentValue]`, that "second" emission is *not* the test catching a state transition — it is almost always (a) the spurious initial-flash from a `_uiState.stateIn(WhileSubscribed, initial)` derived StateFlow (the bug fixed in #37 / lesson L-2026-04-30-06), or (b) the test racing the dispatcher. Default expectation is `size == 1`. Larger sequences are only correct when the test *deliberately* drives a state transition during the collect window (e.g. user action, `LOADING` → `DATA` from a hand-fed fake). When in doubt, attach an inline comment naming the issue (`// see #37`) so a future reader doesn't roll the assertion back to match the buggy shape.

Three of six existing VM test files in this repo (`HomeViewModelTest`, `GiveawaysViewModelTest`, `StoreViewModelTest`) were anchored to the buggy two-value shape for months until #37 surfaced it; the other three (`SearchViewModelTest`, `GameViewModelTest`, `DealDetailsViewModelTest`) were already correct.

**Source:** Wave 2 PR #66 (issue #37 — six ViewModels redundant `stateIn`)

### L-2026-05-01-06 · In `:common:ui`, reach for the Activity via `LocalActivity.current` (null-safe) — never `view.context as Activity`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** compose, theme, activity, common-ui, preview-safety
**Applies to:** Code in `:common:ui` (and any other shared Compose module) that needs the host Activity to mutate window state — `WindowCompat`, `WindowInsetsController`, status-bar styling, `setRequestedOrientation`, etc.

`(view.context as Activity)` casts inside `LocalView.current` work in `MainActivity` but crash with `ClassCastException` in `@Preview`, `@PreviewParameter`, layout-inspector renders, and Robolectric/instrumented hosts that wrap the view in a non-Activity ContextThemeWrapper. Use `androidx.activity.compose.LocalActivity.current` (returns nullable `Activity?` — null in previews and tests) and skip the styling block when null. This was the fix in `GameDealsTheme`; the same pattern applies anywhere in `:common:ui` that needs Activity-typed access. **Project note:** `:common:ui` did not previously declare `androidx.activity:activity-compose` — it has to be added to `common/ui/build.gradle.kts` (the catalog already pins `activity-compose = 1.11.0`). Adding the dep is the right move: `LocalActivity` is the public, supported API since Activity 1.10; the cast is not.

**Source:** Wave 1 PR #61 (issue #41 — GameDealsTheme casts view.context as Activity)

### L-2026-05-01-05 · Don't mix `System.currentTimeMillis()` with `delay()` in Flow operators — `runTest` virtualizes only `delay`
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
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** paging, coroutines, flow, viewmodel, error-handling
**Applies to:** Feature ViewModels that expose `Flow<PagingData<T>>` to the UI via `collectAsLazyPagingItems()` — Store, Search, Giveaways, etc.

A `Pager` data Flow does not throw on load failures — those are surfaced as `LoadState.Error` on each `LoadResult`, and the UI's `LazyPagingItems.loadState.refresh/append/prepend` handlers (retry buttons, error rows) already key off them. What *can* throw upstream is the *construction* of the Pager Flow — `dealsRepository.getPagingStoreDeals(storeId)` and similar. A terminal `.catch { logger.fatalThrowable(it) }` placed *after* `.cachedIn(viewModelScope)` swallows that construction-time throwable without re-emitting, so `LazyPagingItems` is left pinned to the last-cached `PagingData` forever — no recovery, no retry, just a silent dead Flow. Removing the `.catch` lets Paging's own `LoadState` machinery handle load errors and lets construction errors propagate to `viewModelScope`'s exception handler where they're not silently buried. If you genuinely need to log construction failures separately, do it *before* `.cachedIn` (where `.catch` can re-emit a sentinel like `PagingData.empty()`), not after.

**Source:** Wave 1 PR #55 (issue #46 — StoreViewModel `.catch` after `.cachedIn`)

### L-2026-05-01-01 · Don't call `SavedStateHandle.toRoute<>()` inside ViewModels — use `savedStateHandle.get<Primitive>("propName")`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** navigation, compose, viewmodel, testing
**Applies to:** Feature ViewModels in this project that consume args seeded by typed Compose Navigation destinations (`composable<Destination.X>` / `navController.navigate(Destination.X(...))`)

`SavedStateHandle.toRoute<Destination.X>()` performs an internal `android.os.Bundle` round-trip that is unimplemented on plain JVM, so any unit test that constructs a ViewModel using it fails without an Android runtime. Avoid the call inside ViewModels: use `savedStateHandle.get<Int>("propName")!!` where `propName` matches the `@Serializable` property name on the `Destination` subclass — nav-compose 2.9 populates `SavedStateHandle` with property-name keys for primitive args, so end-to-end typing is preserved at the nav layer (registration + navigate calls stay typed) without dragging Android into ViewModel tests. `NavBackStackEntry.toRoute<>()` at the nav-boundary (composable lambda) is fine — that code only runs on Android.

**Source:** Wave 1 PR #50 (issue #23 — typed Compose Navigation)

### L-2026-05-01-02 · Robolectric is not used in this project — find a JVM-only path or move the test to `androidTest`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** testing, robolectric, project-policy
**Applies to:** Any decision to add a test dependency that brings an Android runtime into plain JVM unit tests

If a JVM unit test fails because some Android API is unimplemented (most commonly a `SavedStateHandle`/`Bundle` round-trip via `toRoute<>()`, or `android.os.*` stubs), do NOT add Robolectric. Find a JVM-only path: extract the Android-touching call out of the system under test, swap to a primitive-key read on the existing data structure, or move the test to instrumented (`androidTest`). The user has explicitly rejected Robolectric for this project — adding it again will be reverted.

**Source:** Wave 1 PR #50 review

### L-2026-05-01-03 · When dropping a public interface in `:domain` whose `@Inject` impl took internal-typed Daos, mark the renamed concrete class's constructor `internal`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-05-01 · **Tags:** hilt, di, kotlin-visibility, multi-module
**Applies to:** Refactors that delete a public interface in `:domain` whose `@Inject` impl took `internal`-typed Daos / collaborators

While the public interface existed, the impl was free to keep a public constructor because Kotlin only checks visibility against the *declared* type and the API surface ran through the interface. Once the interface is removed, the renamed concrete class's `@Inject constructor(...)` becomes the public API surface and must NOT expose internal types — Kotlin fails compilation with "public function exposes its internal parameter type." Solution: declare it `@Inject internal constructor(...)`. The class stays public so feature modules can name the type for injection; the constructor is module-private so only Hilt's generated code in the same module can call it. This preserves the original `:domain` API narrowness — no Daos leaked.

**Source:** Wave 1 PR #49 (issue #16 — drop single-impl Repository interfaces)

### L-2026-04-30-06 · `_uiState.stateIn(WhileSubscribed, initial)` is the wrong shape — use `_uiState.asStateFlow()`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** viewmodel, stateflow, coroutines, compose
**Applies to:** New screen ViewModels in `feature/*` modules; copying the existing convention from Home/Store/Giveaways/Search/Game/DealDetails ViewModels.

The current convention `private val _uiState = MutableStateFlow(initial); val uiState = _uiState.stateIn(viewModelScope, WhileSubscribed(5000), initial)` is broken on two counts. (1) `MutableStateFlow` is already a hot, conflated, replay-1 StateFlow — `stateIn` produces a *second* derived StateFlow whose state machine is independent of `_uiState`. (2) Under `WhileSubscribed(5000)`, after subscribers drop, `uiState.value` returns the frozen last derived value, and new subscribers see `initialValue` for one frame even when `_uiState` has a different value — producing a spurious "LOADING" flash on resume after >5 s of backgrounding. Use `_uiState.asStateFlow()`. Tracked as issue #37 across all six existing ViewModels.

**Source:** android-bug-hunting-dispatcher audit (issues #30–#48)

### L-2026-04-30-05 · `flow { emitAll(repo.observeXxx()) }` is intentional — preserves synchronous-throw safety
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, repository, error-handling
**Applies to:** ViewModel code that consumes a repository `observeXxx()` Flow returned by `:domain` repositories

When you see `flow { emitAll(repo.observeXxx()) }.map{…}.catch{…}`, do not "simplify" the wrapper away. The repository's `observeXxx()` returns `Flow<…>` but can throw synchronously during construction (e.g., backing-store read failures surfaced before the first emission). Wrapping in `flow {}` converts that synchronous throw into an upstream exception that downstream `.catch {}` can handle; without the wrapper the throw escapes the `viewModelScope.launch` builder. Examples in `HomeViewModel.loadTopStoreDataFlow` / `loadNewReleases` / `loadGiveaways` and `GiveawaysViewModel.{init, loadGiveaway}`.

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-04 · Keep ViewModel functions Flow-shaped; don't lower to `viewModelScope.launch { try/catch }`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, viewmodel, architecture
**Applies to:** Any feature ViewModel function in this project that triggers work in response to a UI event

In this project, ViewModel handlers are deliberately structured as `viewModelScope.launch { someFlow.onStart{…}.map{…}.onError{…}.onCompletion{…}.catch{…}.collect { _state.emit(it) } }`. Do not "modernize" them into imperative `try/catch` blocks even when the body is short — Flow is the medium that composes loading-state emission, `logFlow`, `mapDelayAtLeast` (minimum-loading UX), `onError`/`onCompletion` rethrow semantics, and SharedFlow event side-effects. A bulk lowering across `feature/*` was attempted (b41c34d) and reverted (4f20fa5). Generalizes L-2026-04-27-01 from "which error helper" to "what shape is the function."

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-03 · Test internals from inside the owning module, not via test-only factories
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** testing, multi-module, mockwebserver, internal-visibility
**Applies to:** Multi-module Android projects where a facade/data-source has `internal` collaborators (Retrofit `*Api` types, KSP-generated artifacts) that an outside test wants to construct against `MockWebServer`.

Do not introduce a test-only `Factory.create(...)` in `main/` to give a downstream module's test access to `internal` collaborators — the cost is permanent (a production-source seam that exists only for tests) and the test pattern stops matching its peers. Keep downstream repository tests mocking the facade interface like their peers, and put the HTTP-wiring/integration test inside the module that owns the impl: same module's `test/` source set sees `internal` for free. Net effect: repository tests stay narrow at the facade boundary, integration coverage lives at the right layer, and no test-only types leak into production source.

**Source:** PR #27 (refactor/15-remote-source-facade)

### L-2026-04-30-02 · GitHub Actions JDK must match `compileOptions.targetCompatibility`, not just the Kotlin toolchain
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** ci, gradle, hilt, jdk, github-actions
**Applies to:** Android projects with Hilt where the workflow JDK is older than the project's `compileOptions.targetCompatibility`

When the project's `compileOptions.targetCompatibility` (and matching Kotlin `jvmToolchain(...)`) is newer than the runner's JDK, most Java compile tasks succeed because Gradle auto-provisions the toolchain. But `:app:hiltJavaCompileDebug` runs as a plain `JavaCompile` against Gradle's own JVM — *not* the toolchain — and fails with `error: invalid source release: <N>`. Set the workflow JDK (`actions/setup-java@v4` `java-version`) to at least the project's target version; don't rely on the Kotlin toolchain to paper over the mismatch.

**Source:** Wave 1 PR #25 (Hilt KAPT → KSP)

### L-2026-04-30-01 · Ports in `:domain`, adapters in `:remote:*`, wiring in `:app`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** hilt, di, multi-module, ports-and-adapters, architecture
**Applies to:** Any refactor that lifts an interface from a remote/data module up into `:domain` so `:domain` no longer depends on `:remote:*`

When you move a Source/Repository *interface* up to `:domain` (the port) but leave its `@Provides`-bound impl down in `:remote:*` (the adapter), `:domain` correctly drops `implementation(project(":remote:*"))`. But the composition root — `:app` — must then add those `:remote:*` modules as `implementation` deps itself, otherwise Hilt fails at `:app:hiltJavaCompileDebug` with `MissingBinding` for the port type. The Hilt graph follows Gradle visibility; if `:app` can't see the adapter module, it can't see its `@Module @Provides`. This is mechanical, not a design flaw — but it surprises you the first time because the symptom shows up at app-level Hilt compilation rather than at the module boundary.

**Source:** Wave 2 PR #29 (issue #17 — Remote→Domain mapper relocation)

### L-2026-04-27-01 · Prefer flow-native error helpers over `runCatching` inside Flow chains
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-27 · **Tags:** kotlin, coroutines, flow, error-handling
**Applies to:** ViewModel/repository code that wraps a single suspend call inside a Flow pipeline

When lifting a `suspend` call into a Flow, prefer `flow { emit(call()) }` plus the project's `catchAndContinue(defaultValue, action)` helper from `common/logic/util/FlowExtensions.kt` over `runCatching { ... }.getOrNull()`. It keeps error handling on the Flow itself (so cancellation and downstream operators see the right thing) and reuses the codebase's existing helper instead of inlining a raw `.catch { ... emit(default) }`.

**Source:** CommitmentPackagesViewModel FAQ refactor

### L-2026-04-20-01 · On migration branches, map conflicting *features* not *files*
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-20 · **Tags:** merge-conflicts, migration, di, architecture
**Applies to:** Any long-running branch refactoring infrastructure (DI, networking, shared modules) while `main` ships features

Don't resolve merge conflicts file-by-file. First identify which *features* landed on `main` during the migration, then decide per feature: was it already migrated? Does it need to be? Does it stay where it is? This gives a coherent strategy instead of ad-hoc ours/theirs picks that silently break DI wiring. In practice, a dozen conflicting files often reduce to just two or three feature-level decisions.

**Source:** merge conflict resolution

## Archive

### L-2026-05-05-03 · `sentry-kotlin-multiplatform` via Sentry-Cocoa SPM needs an exact version pin and the `Sentry-Dynamic` product
**Status:** superseded by L-2026-05-05-04 · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** sentry, sentry-kotlin-multiplatform, sentry-cocoa, kmp, kotlin-native, spm, xcode-26, version-skew
**Applies to:** A KMP project integrating `sentry-kotlin-multiplatform` on iOS by adding `https://github.com/getsentry/sentry-cocoa` as a Swift Package Manager dependency in `iosApp.xcodeproj` (rather than via CocoaPods)

`sentry-kotlin-multiplatform`'s cinterop layer references specific Objective-C class symbols (`_OBJC_CLASS_$_SentrySDK`, `_OBJC_CLASS_$_SentryEnvelope`, `_OBJC_CLASS_$_SentryDependencyContainer`, etc.). Those symbols only exist in narrow Sentry-Cocoa version windows: Sentry-Cocoa 9.x rewrote them as Swift classes (mangled `__TtC6Sentry…` symbols), and even within 8.x successive patches keep deprecating more public surface to Swift. SPM rules like "Up to Next Major" silently pick a too-new patch and break the link with `Undefined symbols for architecture arm64: _OBJC_CLASS_$_Sentry…`. Pin to **Exact Version** matching the sentry-kotlin-multiplatform release's CocoaPods Podfile.lock — for `sentry-kotlin-multiplatform:0.13.0` that is **`8.36.0`**.

Two more SPM gotchas worth knowing up front: (1) pick the **Sentry-Dynamic** SPM product, not `Sentry`. Xcode 26's SwiftBuild treats the static `Sentry` product as a "codeless framework", strips its Mach-O during embedding, and replaces it with a stub binary — so the linker has no symbols to resolve even when search paths are correct. The build log gives this away with `Injecting stub binary into codeless framework`. (2) Confirm the actual binary on disk with `nm -gU …/Build/Products/Debug-iphonesimulator/Sentry.framework/Sentry | grep <expected class>`; a missing symbol there is the difference between "wrong product variant" and "wrong upstream version" — the former produces no symbols, the latter produces *different* symbols (Swift-mangled).

**Source:** Phase-7e iOS Sentry wire-up — three rounds of unresolved-symbol errors before isolating the dynamic product and the exact 8.36.0 pin.

### L-2026-05-05-02 · Serialize racy cross-module Gradle tasks via shared BuildService, not `--max-workers=1`
**Status:** superseded by L-2026-05-06-01 · **Confidence:** confirmed · **Added:** 2026-05-05 · **Tags:** gradle, kmp, kotlin-native, ios, build-service, test-parallelism
**Applies to:** Any KMP project where the same task type (e.g. `KotlinNativeHostTest`/`iosSimulatorArm64Test`) runs concurrently across modules and races on a non-thread-safe writer — the Gradle 9.1 + Kotlin 2.2.21 test-result XML reporter is the canonical case, failing with `Could not write XML test results for ... .xml` while the test itself passed.

`--max-workers=1` works as a workaround but throttles the entire build. Better: register a marker `BuildService` with `maxParallelUsages.set(1)` at the project level (in a convention plugin so every consuming module hooks in), then `tasks.withType(<TaskType>::class.java).configureEach { usesService(svc) }`. Gradle permits only one of those tasks to run at a time across the build while leaving every other task free to parallelize. Same pattern works for any cross-module serialization need (shared simulator slots, exclusive emulators, native code-signing).

**Source:** Phase-7b iOS polish — eliminating the `--max-workers=1` workaround we kept passing for `iosSimulatorArm64Test` runs.
