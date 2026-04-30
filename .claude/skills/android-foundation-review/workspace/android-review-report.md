# Game Deals Android — Foundation Review

**Audience:** staff-level Android engineer
**Reviewed:** 2026-04-30
**Project:** `game-deals-android-app` (private), main branch
**Scale:** 18 Gradle modules, ~8,100 LOC Kotlin, 100% Compose, single-Activity, Android-only
**Stack:** AGP 8.13.0 / Gradle 8.13 / Kotlin 2.2.21 (K2) / KSP 2.3.0 / Compose BOM 2025.10.01 / Hilt 2.57.2 / Retrofit 3.0.0 + OkHttp 5.2.1 + Sandwich + kotlinx-serialization / Room 2.8.3 / Firebase BOM 34.4.0 / Coil 2.7.0 / Coroutines 1.10.2 / Paging 3.3.6

---

## Executive summary

Game Deals is a structurally sensible, well-modularized Android app that has been kept current at the **library layer** (latest AGP, Kotlin 2.2 with the K2 Compose plugin, Retrofit 3, Compose BOM, Firebase BOM, KSP for Room) but has **not been kept current at the build-and-release layer**. The four issues that compound across nearly every dimension reviewed are:

1. **R8 is off in every module, including `:app` release.** `isMinifyEnabled = false` in all 18 modules. This single setting owns most of the "Performance" and a chunk of the "Security" findings.
2. **Hilt still runs through kapt in 14 modules.** Room is on KSP and proves the toolchain works. Every Hilt module pays a kapt tax it doesn't need to.
3. **Domain models are simultaneously Room entities, kotlinx-serialization DTOs, and Compose `@Immutable` types.** Every API change forces a Room migration; every DB change ripples into Compose previews. Combined with `fallbackToDestructiveMigration()`, this is a slow-burning correctness risk.
4. **Crashlytics is wired but the in-house `Logger` doesn't feed it.** `Logger.fatalThrowable` ends at `Log.wtf` — every non-fatal logged via the project's own abstraction is invisible in production.

Everything else is incremental. The architecture is sound (clean module DAG, consistent feature shape, typed network errors via Sandwich + a `RemoteHttpException` boundary, internal-modifier discipline, single-Activity Compose, no `GlobalScope`, no LiveData, no kapt for Room). The team made the structural decisions correctly. What's missing is the mechanical scaffolding — convention plugins, KSP everywhere, R8, Baseline Profile, real model separation — that the modern Android stack now expects.

**Overall verdict: ADEQUATE, trending STRONG once R8 + KSP migration + model split + Crashlytics wiring land.**

---

## Cross-cutting scorecard

Verdicts merged across the six specialist reviews. Where agents disagreed, the verdict here is the resolved one (spot-checked).

| Dimension | Verdict | Cited by |
|---|---|---|
| Module graph & dependency direction | **STRONG** | Architecture, Layering |
| Activity / Fragment / Screen discipline | **STRONG** | Layering |
| Permissions & privacy posture | **STRONG** | Security |
| Networking & serialization choices | **STRONG** | Modern Patterns, Architecture |
| Compose adoption & state collection | **STRONG (caveats)** | Modern Patterns |
| Declared architecture pattern (MVVM) | ADEQUATE | Architecture |
| ViewModel responsibility | ADEQUATE | Layering |
| Repository pattern (interface+impl, Sandwich) | ADEQUATE | Layering, Architecture |
| Coroutines / Flow idioms | ADEQUATE | Modern Patterns |
| Navigation (compose-nav, string-routed) | ADEQUATE | Architecture |
| Error handling & resilience | ADEQUATE | Architecture |
| Cross-cutting concerns / observability | ADEQUATE | Architecture |
| UI state management (mixed enum vs sealed) | ADEQUATE | Layering |
| Feature boundary discipline | ADEQUATE | Layering |
| Accessibility | ADEQUATE | Modern Patterns |
| Image loading (Coil 2 + central loader) | ADEQUATE | Modern Patterns, Performance |
| Test quality (unit) | ADEQUATE | Testing |
| Coroutine/Flow testing | ADEQUATE | Testing |
| UI/Compose testing | ADEQUATE | Testing |
| Manifest hardening / backup rules | ADEQUATE | Security |
| Secrets management | ADEQUATE | Security |
| Domain layer & use cases | **WEAK** | Layering |
| Model mapping discipline (triple-annotated entities) | **WEAK** | Layering |
| Local storage (`SharedPreferences.commit`, destructive Room migration) | **WEAK** | Modern Patterns |
| Configuration / build-variant strategy / convention plugins | **WEAK** | Architecture |
| Gradle / KSP / kapt-everywhere | **WEAK** | Modern Patterns |
| Compose performance (no compiler reports, missing `key`s, plain `List`) | **WEAK** | Performance |
| Memory / leaks (no LeakCanary, no StrictMode) | **WEAK** | Performance |
| Firebase Performance plugin (declared, not applied) | **WEAK** | Performance |
| Room indices / query callback in release | **WEAK** | Performance |
| Network security config (`usesCleartextTraffic="true"`) | **WEAK** | Security |
| WebView hardening (JS on, no allowlist, no mixed-content control) | **WEAK** | Security |
| R8 / obfuscation (security & perf angle) | **WEAK** | Security, Performance, Modern Patterns |
| Dependency hygiene (no Dependabot/Renovate, no scanner) | **WEAK** | Security |
| Test doubles strategy (no fakes, no factories, no Turbine) | **WEAK** | Testing |
| CI / coverage (build+test only, no instrumented, no coverage, no static analysis) | **WEAK** | Testing |
| Test inventory shape (zero tests in app/base/common/common:ui/logging/webview) | ADEQUATE→WEAK | Testing |
| Startup performance (no Baseline Profile, no profileinstaller) | **MISSING** | Performance |
| Macrobenchmark / continuous perf measurement | **MISSING** | Performance |
| Jank / tracing / StrictMode | **MISSING** | Performance |
| Integration & end-to-end testing (no MockWebServer, no Hilt-instrumented, no Room-migration tests) | **MISSING** | Testing |
| Static analysis & code-quality gates (no detekt, ktlint, Spotless, Lint enforcement) | **MISSING** | Testing |
| KMP discipline | N/A | Layering, Modern Patterns |
| Auth / anti-tamper / sensitive storage | N/A | Security |
| Background work (none required) | N/A | Performance |

---

## Strengths to protect

These are working well and should not regress.

1. **Clean module DAG.** Verified: features depend on `:domain` + `:common*` + `:logging`; none import `pm.bam.gamedeals.remote.*`. `:remote:cheapshark` and `:remote:gamerpower` are siblings under a parent `:remote`. App is the integration point. No cyclic edges. `nonTransitiveRClass=true` is set.
2. **Consistent feature shape.** Every feature has `ui/<X>ViewModel.kt` + `ui/<X>Screen.kt` + `navigation/<X>Navigation.kt`. New features slot in trivially. `NavGraphBuilder.<x>Screen(...)` extension functions take outbound nav as lambdas — features don't depend on each other for navigation.
3. **`internal` discipline.** 147 occurrences across 65 files. Public API surface of each module is genuinely thin.
4. **Typed network errors.** Sandwich + `RemoteHttpException` sealed type + `RemoteExceptionTransformer` mean Retrofit's `HttpException` never escapes `:remote`. `mapAnyFailure { transform(this) }` is applied uniformly across data sources.
5. **Modern reactive plumbing.** Every screen uses `collectAsStateWithLifecycle`. Every `MutableStateFlow` is consumed via `stateIn(scope, WhileSubscribed(5000), initial)`. Every `mutableStateOf` is in `remember` or `rememberSaveable`. **Zero** `LiveData`, `GlobalScope`, `runBlocking` in production code. One-shot events use `Channel(BUFFERED).receiveAsFlow()` consumed via a custom lifecycle-aware `SingleEventEffect`.
6. **Single Activity.** `MainActivity` is 26 lines and just hosts the `NavGraph`. No `onActivityResult`/`onRequestPermissionsResult` anywhere — modern result APIs only.
7. **KSP is already in the project.** `:domain` uses `ksp(libs.room.compiler)`. The migration path for Hilt is mechanical, not exploratory.
8. **Build-time configuration cache + parallel + daemon.** Already enabled in `gradle.properties`. The build-system fundamentals are right.
9. **Permissions are exemplary.** A single `INTERNET` permission across the entire app. No location, no storage, no media.
10. **Signing config is well-thought-out.** `app/build.gradle.kts:1-60` supports both `local.properties` and CI env-var paths with sensible fallbacks. `.gitignore` covers `*.jks`, `*.keystore`, `local.properties`. No keys in source.
11. **Test naming convention.** 67 backticked `\`...\`` test names, zero `testX`-style. Behavior-readable.
12. **Crashlytics gating.** Disabled in debug builds via `app/src/debug/AndroidManifest.xml` — symbol-perfect setup, undermined only by the missing Logger→Crashlytics wire (Critical #4 below).

---

## Critical findings (architectural / security / perf-trap)

These compound as the codebase or team scales. They should be addressed first.

### C1. R8 is disabled in `:app` release. Resource shrinking is off everywhere.

**Cited by:** Performance, Security, Modern Patterns, Architecture.

**Evidence (verified):** Every module — including `:app` — sets `isMinifyEnabled = false`. `app/build.gradle.kts:79`:
```kotlin
release {
    isMinifyEnabled = false
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    signingConfig = signingConfigs.getByName(releaseSigningKey)
}
```
No `proguard-rules.pro` exists anywhere in the repo. No `isShrinkResources` set. The Crashlytics plugin is applied but only uploads mapping when minify is on — so production stack traces are not de-obfuscated… but since nothing is obfuscated yet, that's moot until you flip the switch.

**Impact:** Release APK ships full Compose + Material3 + Firebase + Retrofit + Sandwich + Coil + Room bytecode and resources. Crashlytics is providing production crash reports against unminified code (so they look readable), but the moment you enable R8 you'll need keep rules. R8 also surfaces useful warnings about reflective access (kotlinx-serialization, Retrofit interfaces, Room entities) — none of which surface today. Security-side: anyone can decompile, see API endpoints, and trivially fork.

**Recommendation:**
```kotlin
// app/build.gradle.kts
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
    signingConfig = signingConfigs.getByName(releaseSigningKey)
}
```
Add a minimal `proguard-rules.pro` with rules for `kotlinx.serialization` `@Serializable` data classes (the `KEEP_RULES` are documented), Retrofit interfaces, and any reflectively-accessed type. Hilt and Room are R8-aware. Verify Crashlytics mapping upload runs after the build. Library modules can stay un-minified; only `:app` matters for the final binary.

### C2. Hilt runs through kapt in 14 modules. Room already proves KSP works.

**Cited by:** Architecture, Modern Patterns.

**Evidence:** Every Hilt-using module declares `alias(libs.plugins.kotlin.kapt)` and feeds Hilt through `kapt(libs.hilt.compiler)` + `kapt(libs.hilt.androidx.compiler)`. Yet `domain/build.gradle.kts:74` already does `ksp(libs.room.compiler)`. Hilt has supported KSP since 2.48; this project is on 2.57.2.

**Impact:** Every kapt module forces Java-stub generation before annotation processing. Across 14 modules, both cold and incremental compile pay a meaningful tax that the configuration-cache cannot offset.

**Recommendation:** Single PR, mechanical:
- In each module: replace `alias(libs.plugins.kotlin.kapt)` with `alias(libs.plugins.kotlin.ksp)` (drop the kapt alias if KSP is already there).
- Replace every `kapt(libs.hilt.compiler)` and `kapt(libs.hilt.androidx.compiler)` with `ksp(...)` equivalents.
- Drop `kotlin-kapt` alias from root `build.gradle.kts`.

Effort: S. Expect 20–40% incremental-build improvement on touch-an-annotated-class flows.

### C3. Domain models are triple-annotated god objects.

**Cited by:** Layering (lead), Architecture (touches), Modern Patterns (touches).

**Evidence:** Every model under `domain/src/main/java/pm/bam/gamedeals/domain/models/` is simultaneously a Room `@Entity`, a kotlinx-serialization `@Serializable` DTO, and a Compose `@Immutable` view-model surface — in the same class. Example from `Deal.kt`:
```kotlin
@Immutable
@Entity(tableName = "Deal")
@Serializable
data class Deal(
    @PrimaryKey @SerialName("dealID") @EncodeDefault(...) val dealID: String,
    ...
    @SerialName("expires") val expires: Long = System.currentTimeMillis().plus(millisInHour * 8)
)
```
`Search.kt` (`SearchParameters`) imports `RemoteDealsQuery` from the remote layer — domain reaching into data. `:domain` itself is an Android library (`com.android.library` plugin + `androidx.appcompat` + `material` + `room` + `paging`), not pure Kotlin.

**Impact:**
- Any API change forces a Room schema migration.
- Any DB schema change ripples into Compose previews.
- `@Serializable` on the entity means serializing a `Deal` for analytics or deep links accidentally writes the `expires` cache marker.
- Tests must construct 20+ fields and three annotation systems to instantiate one model.
- The "domain" layer cannot be extracted to KMP `commonMain` or unit-tested without Robolectric.
- Combined with `fallbackToDestructiveMigration()` (see C5) and write-time currency formatting, locale changes invalidate every cached row.

**Recommendation (sequence, smallest viable):**
1. Split each entity into three classes:
   - `RemoteX` (already exists; lives in `:remote:*`).
   - `XEntity` (`@Entity`, lives in a new `:data` Android library).
   - `X` (plain Kotlin in `:domain`, no annotations).
2. Add boundary mappers `RemoteX → X` and `XEntity ↔ X`.
3. Convert `:domain` to `kotlin("jvm")` (or KMP `commonMain` later); move Room/Paging into `:data`.
4. Drop `@Immutable` from domain types; if Compose stability matters at the screen, wrap in a presentation model (`HomeContent`, `DealUi`) at the seam.
5. Move currency formatting to the read side (presentation). Stop persisting denominated strings.

This is the single highest-ROI refactor in the codebase. It enables real unit tests, KMP, locale-correct caching, and a meaningful "domain" name.

### C4. The in-house Logger doesn't feed Crashlytics.

**Cited by:** Architecture (lead), Security (touches).

**Evidence:** `logging/src/main/.../implementations/SimpleLoggingListener.kt:30-49` (verified) only delegates to `android.util.Log`. `LogLevel.FATAL` ends at `Log.wtf(...)`. `onFatalThrowable` ends at `Log.wtf(tag, "Fatal crash", throwable)`. Grep for `FirebaseCrashlytics` / `recordException` returns zero source matches. Calls like `fatal(logger, e)` in `HomeViewModel.kt:82`, `DealsMediator.kt:78`, etc. never reach Crashlytics.

**Impact:** Crashlytics catches **uncaught** exceptions, but every error routed through the project's own `Logger.fatalThrowable` infrastructure is silent in production. The team built a logging abstraction expressly designed to be cross-cutting — and then didn't hook it up.

**Recommendation:** Add `CrashlyticsLoggingListener` under `logging/src/main/java/pm/bam/gamedeals/logging/implementations/` implementing `LoggingInterface`:
- For `WARN`/`ERROR`: `FirebaseCrashlytics.getInstance().log(message)` (breadcrumb).
- For `FATAL` and `onFatalThrowable`: `FirebaseCrashlytics.getInstance().recordException(throwable)`.

Register alongside `SimpleLoggingListener` in `LoggingModule.provideLogger()` for release builds. Effort: S. Closes a real observability hole.

### C5. Room ships with `fallbackToDestructiveMigration()`.

**Cited by:** Modern Patterns (lead), Performance (notes), Layering (touches).

**Evidence:** `domain/.../di/DomainModule.kt:118-126`:
```kotlin
Room.databaseBuilder(context, DomainDatabase::class.java, "...db")
    .fallbackToDestructiveMigration()
    .addTypeConverter(...)
    .setQueryCallback({ sqlQuery, bindArgs -> verbose(logger) { "..." } },
                     Executors.newSingleThreadExecutor())
    .build()
```
No `Migration` objects exist. No `MigrationTestHelper` test exists.

**Impact:** Wipes the DB on any schema change. Today the DB is a refetchable cache so user impact is recoverable, but the flag also propagates to any future feature that puts user-meaningful data in Room (favorites, watchlists). The query callback is wired in **release** builds — if `verbose(logger)` is ever materialized to an actual sink, you get a slow drip on every query.

**Recommendation:**
- Either (a) document explicitly that `:domain`'s DB is a refetchable cache and `fallbackToDestructive…` is intentional, or (b) write real `Migration` objects and add a `MigrationTestHelper` test.
- Gate `setQueryCallback` to `BuildConfig.DEBUG`.

### C6. WebView is JS-enabled with no URL allowlist, on a manifest that permits cleartext traffic.

**Cited by:** Security.

**Evidence (verified):** `feature/webview/src/main/.../ui/WebView.kt:85`:
```kotlin
@SuppressLint("SetJavaScriptEnabled")
...
WebView(context).apply {
    settings.javaScriptEnabled = true
    this.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view, request): Boolean {
            loading = true
            return super.shouldOverrideUrlLoading(view, request)  // returns false → loads
        }
        ...
    }
}
```
URL is taken from a nav arg, not validated. The single caller hard-codes a `cheapshark.com/redirect` URL, but `WebView.kt` itself does not enforce that. `setMixedContentMode`, `setAllowFileAccess`, `setAllowContentAccess`, Safe Browsing, and `setAllowFileAccessFromFileURLs` are not configured — defaults apply. `app/src/main/AndroidManifest.xml:17` (verified) sets `android:usesCleartextTraffic="true"` with no `network_security_config.xml`. `addJavascriptInterface` is correctly absent — no native bridge.

**Impact:** A CheapShark open-redirect (or any future change letting external code pass an arbitrary URL into the nav route) lands in a JS-enabled WebView with cleartext permitted and no allowlist. Realistic phishing/XSS surface.

**Recommendation:** Cheapest path is to **replace the in-app WebView with Custom Tabs** for redirect-style content — sidesteps the entire hardening problem and matches the "open in browser" UX the screen already exposes. If WebView must stay:
1. Allowlist URL host (`cheapshark.com`) before `loadUrl`.
2. `settings.mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`.
3. Explicitly `allowFileAccess = false`, `allowContentAccess = false`.
4. `WebSettingsCompat.setSafeBrowsingEnabled(settings, true)` (`androidx.webkit` is already on the classpath).
5. Override `shouldOverrideUrlLoading` to enforce the allowlist on in-WebView navigations.
6. Drop `usesCleartextTraffic="true"` and add a `network_security_config.xml` with `<base-config cleartextTrafficPermitted="false"/>`.

---

## Significant findings (slows the team / risks users; not blocking)

### S1. No convention plugins — ~700 lines of copy-paste Gradle config.

**Cited by:** Architecture, Modern Patterns.

The same ~50-line block repeats across 14 modules: `compileSdk = 36`, `minSdk = 26`, `JavaVersion.VERSION_21`, identical `packaging.resources.excludes`, identical `tasks.withType<Test> { jvmArgs = … }`, identical `composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }`. The Compose options block is **dead** — under the K2 Compose plugin (Kotlin 2.x), `kotlinCompilerExtensionVersion` is ignored.

Add a `build-logic/convention/` module (NowInAndroid pattern) with three plugins: `gamedeals.android.library`, `gamedeals.android.feature`, `gamedeals.android.hilt`. Each module's `build.gradle.kts` shrinks to ~30 lines. **Do this before C2 (KSP migration)** — once convention plugins exist, KSP migration is one line per plugin instead of fourteen line edits.

### S2. No DispatcherProvider; `Dispatchers.Default` is hard-coded for Coil.

**Cited by:** Modern Patterns, Performance.

`AppModule.kt:46` does `.dispatcher(Dispatchers.Default)` on the Coil `ImageLoader`. Coil's default is `IO`, which is the right pool for network/disk-bound work. Forcing the CPU-bound pool risks contending with image decoding while scrolling. Separately, no `DispatcherProvider` is injected anywhere — VM tests rely on `Dispatchers.setMain` + trust that repos honor `Main.immediate`.

Add a `DispatcherProvider` interface in `:common` with a Hilt-provided implementation. Inject into ViewModels and any class that calls `withContext(Dispatchers.X)`. Drop `.dispatcher(Dispatchers.Default)` from the Coil builder, or document the rationale.

### S3. `SettingStorage` calls `SharedPreferences.commit()` on the main thread.

**Cited by:** Modern Patterns, Security (touches).

**Evidence (verified):** `common/src/main/.../storage/SettingStorage.kt:30,35`:
```kotlin
return sharedPreferences.edit().putString(...).commit()
...
return sharedPreferences.edit().remove(storageKey).commit()
```
No `withContext(Dispatchers.IO)` wrapping. `commit()` is synchronous disk I/O and a known ANR vector. `Storage` is also a generic interface — any future caller could put a token into it.

Replace with `DataStore<Preferences>` (or Proto DataStore). The `Storage` abstraction is well-placed for the swap. Short-term: change `.commit()` to `.apply()` and wrap reads/writes in `withContext(Dispatchers.IO)`.

### S4. `Logger.fatalThrowable` doesn't reach Crashlytics. (See C4.)

Listed here as a reminder — it's also the single biggest fix in the "Significant" tier of error handling. Not duplicated in detail.

### S5. `coil-test` is in `implementation(...)` (ships test fakes to release).

**Cited by:** Modern Patterns.

`app` and most `feature/*` modules declare `implementation(libs.coil.test)`. That ships test fakes into the release APK. Move all `implementation(libs.coil.test)` to `testImplementation` / `androidTestImplementation`.

### S6. `allowBackup="true"` with empty backup rules.

**Cited by:** Security.

`backup_rules.xml` and `data_extraction_rules.xml` contain only template comments. Today the data is non-sensitive cache; the risk is forward-looking — any future feature that stores a token or PII silently leaks via Auto Backup.

Either set `allowBackup="false"` (acceptable for a no-account app) or populate `data_extraction_rules.xml` with explicit `<exclude domain="sharedpref" path="."/>` and `<exclude domain="database" path="."/>` (cloud-backup + device-transfer).

### S7. No baseline profile, no Macrobenchmark, no `androidx.profileinstaller`.

**Cited by:** Performance.

Verified: no `androidx.profileinstaller` dependency, no `:baselineprofile` module, no `:macrobenchmark` module, no `MacrobenchmarkRule` usages. `Application.onCreate` is lean and `<profileable android:shell="true"/>` is set in the manifest — the **enabler** is in place, nothing consumes it.

Add a `:baselineprofile` module via the AGP Baseline Profile Gradle plugin and consume it in `:app`. Add a `:macrobenchmark` module with cold-start (`StartupTimingMetric`) + scroll (`FrameTimingMetric`) tests. Run on a Gradle Managed Device in CI. Expected impact: 20–40% cold-start improvement, plus an automated startup-regression signal.

### S8. Compose performance hygiene is uneven.

**Cited by:** Performance.

- Stability annotations are **good** — domain types carry `@Immutable`. Strong skipping is on (Kotlin 2.2 + Compose plugin).
- **No compiler reports/metrics destination** configured — no signal on unstable parameters or excess recomposition.
- **No `ImmutableList` / `kotlinx-collections-immutable`** — VM state uses plain `List`. With strong skipping these still recompose consumers when contents change.
- **Three `LazyColumn` items calls in `HomeScreen.kt:208,216,234` and one in `GiveawaysScreen.kt:185` have no `key`.** The Home `data.items` list is heterogeneous (`StoreData | DealData | ViewAllData`) and is also missing `contentType`.

Add the Compose compiler reports flag, switch hot screen state to `ImmutableList`, add `key` (and `contentType` for the heterogeneous list) to the four `items(...)` calls.

### S9. No fakes, no factories, no Turbine.

**Cited by:** Testing.

Every test calls `mockk<Deal>()`, `mockk<Store>()`, `mockkStatic(RemoteDeal::toDeal)` etc. on plain `data class`es. The custom `Flow.observeEmissions` helper in `:testing` lacks the `awaitItem()` / "no further emissions" semantics Turbine provides — so tests assert on absolute emission counts, which is fragile.

Add Turbine. Hand-write `FakeDealsRepository`, `FakeStoresRepository`, etc. under `:testing`. Add domain-model factories (`StoreFactory`, `DealFactory`) — domain models are simple `data class`es and should be constructed directly. (Note: domain-model factories become trivially easier after C3.)

### S10. CI runs `build test` only — no instrumented tests, no static analysis, no coverage.

**Cited by:** Testing.

`.github/workflows/android.yml` is a single job: `./gradlew --no-daemon build test`. The 6 instrumented Compose tests have **no enforcement** — a developer could break every screen test and CI would still pass. No detekt, ktlint, Spotless, Konsist, or `dependency-analysis` plugin. No Kover/Jacoco. No `lint { abortOnError = true }` in the app module.

Phased addition (cheap → less cheap):
1. detekt + Spotless with ktlint — mechanical, pre-baseline-able.
2. Lint `abortOnError = true` for `:app` release.
3. Kover with reports as CI artifact.
4. Gradle Managed Device job for instrumented tests.

### S11. Real correctness bug in `GiveawaysViewModelTest`.

**Cited by:** Testing.

`feature/giveaways/src/test/.../GiveawaysViewModelTest.kt:51-58` (`\`initially error\``) asserts `LOADING` but never the eventual `ERROR` state — passes only because of `assertEquals(2, emissions.size)` and a stale loading emission. The error path is not actually validated. Fix the test, then audit for similar shapes.

### S12. Firebase Performance Gradle plugin is declared but not applied.

**Cited by:** Performance.

`alias(libs.plugins.firebase.performance) apply false` in root `build.gradle.kts:11`. `:app` plugins block applies `google-services` and `firebase-crashlytics`, **not** `firebase-performance`. The runtime SDK auto-traces app start, network, and screen rendering; without the Gradle plugin the project misses bytecode-instrumented network monitoring — the main differentiator. No `Firebase.performance` or custom `Trace` usage anywhere.

Either apply `alias(libs.plugins.firebase.performance)` in `:app` or drop the dependency.

### S13. No Dependabot / Renovate / vulnerability scanner.

**Cited by:** Security.

Versions are **healthy today** (current AGP, Kotlin, Hilt, Room, Retrofit 3, OkHttp 5, Compose BOM 2025.10.01, Firebase BOM 34.4.0). All pinned, no `+` ranges, no JCenter. But there is no mechanism that surfaces a CVE. The team will only know about a disclosure by chance.

Add `.github/dependabot.yml` (gradle ecosystem, weekly) — zero-effort. Optionally add the Ben Manes `dependencyUpdates` plugin and an OSV-scanner step to the existing workflow.

### S14. Navigation is string-typed; predictive back is opt-out.

**Cited by:** Architecture, Modern Patterns.

`NavigationDestinations.STORE_ROUTE = "store?storeId={storeId}"`; args extracted via `entry.arguments?.getInt(storeIdArg)!!` — `!!` will crash at runtime if a `navArgument` declaration is missed. No `android:enableOnBackInvokedCallback="true"` in `<application>`. `targetSdk = 34`, so predictive back is currently disabled; the next `targetSdk` bump silently changes back-gesture behavior.

Migrate to type-safe Compose Nav (`@Serializable` route classes + `composable<Route>` + `toRoute<Route>()`). Six destinations — 1–2 day refactor focused on `app/.../navigation/Navigation.kt` and the seven `XxxNavigation.kt` extensions. Add `android:enableOnBackInvokedCallback="true"`. Wire `PredictiveBackHandler` for the deal bottom sheet and search/giveaways filter sheets.

### S15. No edge-to-edge.

**Cited by:** Modern Patterns.

`MainActivity.onCreate` does not call `enableEdgeToEdge()`. `targetSdk = 34` means it isn't enforced today; when you bump to 35, layout silently changes. Add `enableEdgeToEdge()` before `setContent`.

---

## Refinement findings (polish; address opportunistically)

- **R-01.** Delete the dead `composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }` block from all 10 modules where it appears. Ignored under the K2 Compose plugin; misleading to readers.
- **R-02.** Remove the duplicate `implementation(libs.androidx.ui.tooling)` line in `feature/store/build.gradle.kts:74-75` (and check `feature/deal`, `feature/game`, `feature/giveaways` per the Architecture report). Tells me no one is reading these files end-to-end.
- **R-03.** Convert `sealed class` hierarchies that don't carry parent state (`RemoteHttpException`, `SearchData`, `DealBottomSheetData`, `GameScreenData`, `HomeScreenListData`) to `sealed interface`.
- **R-04.** Migrate all `_uiState.emit(_uiState.value.copy(...))` to `_uiState.update { it.copy(...) }`. Functionally equivalent in single-collector contexts; idiomatic.
- **R-05.** Replace `SearchParameters.equals = false` (deliberately broken to bypass StateFlow conflation) with a proper `Channel<SearchParameters>` or `MutableSharedFlow(replay = 0)`. Restore default `equals`.
- **R-06.** `LoggingBaseFragment` is dead code — no Fragment subclasses exist anywhere. Delete. Consider also retiring `LoggingBaseActivity` — the lifecycle-trace log is spammy and the diagnostic value for a single-Activity Compose app is low.
- **R-07.** Add `@Index("storeID")`, `@Index("gameID")` to `Deal` (and equivalents). PK is the only auto-indexed column today. Modest impact at current data volumes.
- **R-08.** Replace `mockk<Deal>()` / `mockkStatic(RemoteDeal::toDeal)` with real instances or shared factories (R-08 becomes trivial after C3).
- **R-09.** Replace `delay(1200)` virtual-time waits in coroutine tests with `advanceTimeBy(1.seconds)` / `advanceUntilIdle()`. Hardcoded debounce constants in tests are a refactor smell.
- **R-10.** Rename `feature:deal` to `ui:deal-bottom-sheet` (or absorb into `:common:ui`) — it's a shared UI component, not a screen feature.
- **R-11.** Add `@Preview(fontScale = 2f)` and `@Preview(locale = "ar")` (RTL) variants in `common/ui/.../Annotations.kt`. For new UI tests prefer `Modifier.semantics { contentDescription = … }` + `onNodeWithContentDescription` over `Modifier.testTag` — `testTag` is invisible to TalkBack.
- **R-12.** Add `debugImplementation` LeakCanary to `:app`. Add `StrictMode.setThreadPolicy(...).penaltyLog()` in `GameDealsApplication` debug builds.
- **R-13.** Set `memoryCache { maxSizePercent(0.25) }` and `diskCache { maxSizeBytes(50 * 1024 * 1024) }` on the Coil `ImageLoader`. Plan a Coil 2 → 3 migration (~14 `AsyncImage` sites + one factory).
- **R-14.** Verify in GCP Console that the Firebase Android API key (in the committed `google-services.json`) is restricted to (a) the `pm.bam.gamedeals` package + release SHA-1 and (b) only the Firebase services in use. Standard Firebase pattern, but worth confirming the server-side scoping.
- **R-15.** Decide on Firebase Analytics: `FirebaseAnalytics` is injected into `MainActivity` but **no `logEvent` calls exist anywhere**. Either start tracking screen views from `NavGraph.kt` (via a thin `AnalyticsTracker` interface in `:common`) or remove the dependency and the plugin.

---

## Recommended action sequence

A pragmatic ordering that minimizes rework. Each step is independently shippable; later steps build on earlier ones.

### Sprint 1 — One PR each, mechanical, low-risk

1. **Wire `Logger.fatalThrowable` → Crashlytics** (C4). Effort: S. Closes the biggest observability hole. **Single-file change.**
2. **Drop `usesCleartextTraffic="true"`; add `network_security_config.xml`** (part of C6). Effort: XS.
3. **Move `coil-test` to `testImplementation`** (S5). Effort: XS.
4. **Delete dead `kotlinCompilerExtensionVersion` blocks; remove duplicate `androidx.ui.tooling` lines; remove `LoggingBaseFragment`** (R-01, R-02, R-06). Effort: XS.
5. **Replace `SettingStorage.commit()` with `apply()` + `withContext(Dispatchers.IO)`** as a holding action (S3 short-term). Effort: XS.
6. **Add `.github/dependabot.yml` for Gradle ecosystem** (S13). Effort: XS.
7. **Fix `GiveawaysViewModelTest.\`initially error\``** (S11). Effort: XS.

### Sprint 2 — Build & release plumbing

8. **Introduce `build-logic/convention/`** (S1). Effort: M. Three plugins: `gamedeals.android.library`, `gamedeals.android.feature`, `gamedeals.android.hilt`. Reclaims ~700 lines of Gradle copy-paste.
9. **Migrate Hilt kapt → KSP across all 14 modules** (C2). Effort: S after S1 lands. One line per plugin.
10. **Enable R8 + resource shrinking on `:app` release** (C1). Effort: S–M (write minimal `proguard-rules.pro`, smoke-test internal track, fix any kotlinx-serialization keep-rule issues).
11. **Apply or remove the Firebase Performance plugin** (S12). Effort: XS.

### Sprint 3 — UX & perf signal

12. **Add `enableEdgeToEdge()`, `enableOnBackInvokedCallback="true"`, `PredictiveBackHandler` for sheets** (S15, parts of S14). Effort: S.
13. **Add `:baselineprofile` and `:macrobenchmark` modules with cold-start + scroll tests on a Gradle Managed Device in CI** (S7). Effort: M.
14. **Set Compose compiler reports/metrics destination; switch hot state to `ImmutableList`; add `key`/`contentType` to the four un-keyed `LazyColumn` items calls** (S8). Effort: S.
15. **LeakCanary + StrictMode in debug; explicit Coil memory/disk cache config** (R-12, R-13). Effort: XS.

### Sprint 4 — Architectural cleanup (the long one)

16. **Type-safe Compose Navigation** (S14). Effort: M.
17. **Replace WebView with Custom Tabs** for the redirect flow (C6). Effort: S–M, removes the entire WebView-hardening scope.
18. **Replace `SettingStorage` with `DataStore<Preferences>`** (S3 long-term). Effort: S.
19. **Static analysis: detekt + Spotless + Lint `abortOnError`** (part of S10). Effort: S.
20. **Fakes + factories + Turbine in `:testing`** (S9). Effort: S–M.
21. **Add Hilt-instrumented integration tests + MockWebServer for both APIs** (Testing C-tier). Effort: M.

### Sprint 5+ — The model split

22. **Split each entity into `Remote*` (exists) + `*Entity` (new) + `*` (new pure-Kotlin domain) with boundary mappers** (C3). Effort: L. Convert `:domain` to `kotlin("jvm")`. Move Room/Paging into a new `:data` Android library. Move currency formatting to the read side. Drop `fallbackToDestructiveMigration()` and write real `Migration` objects with a `MigrationTestHelper` test (C5).

This is the single highest-ROI refactor in the codebase — but it's also the largest. Do it once the build/release plumbing is in shape and there's confidence the tests will catch regressions.

---

## Notes on agent disagreements (resolved)

The six specialist reviews disagreed on three points. Resolution recorded:

1. **DI verdict.** Architecture said ADEQUATE-with-asterisk; Modern Patterns said WEAK because of kapt-everywhere. Resolved: the **DI design** is ADEQUATE (Hilt + qualifiers + scoping + `internal` discipline + no DI bypass via `object`). The **DI processor toolchain** is WEAK (kapt across 14 modules). Reported as separate findings — design strength preserved, toolchain treated as the C2 critical.

2. **R8 verdict ownership.** All three of Performance, Security, and Modern Patterns flagged it. Treated as C1 with a single owner — Performance — and cited from each. Recommendation appears once, not three times.

3. **"Domain" layer verdict.** Architecture marked "module graph & dependency direction" STRONG; Layering marked "domain layer & use cases" WEAK. Both are correct because they measure different things. Resolution: **horizontal** module structure (DAG) is STRONG; **vertical** model separation (entity vs domain vs presentation) is WEAK. Both retained in the scorecard.
