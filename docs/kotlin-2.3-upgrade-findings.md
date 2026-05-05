# Kotlin 2.3.x upgrade — findings & deferred plan

**Status:** deferred (May 2026). Resume when blockers clear (see "Resume conditions" below).
**Source state at investigation time:**
- Branch: `feature/kmp-migration-phase-A5-feature-viewmodel-tests` @ `260ad8f`
- Kotlin **2.2.21**, AGP **9.0.1**, Gradle **9.1.0**, KSP **2.3.2**
- Investigated against Kotlin **2.3.20** (latest 2.3.x as of May 2026)

---

## TL;DR

The upgrade is mechanically viable — toolchain combinations exist that work — but the cost is **2–3 focused days**, not a single-session bump. Deferring is the right call until:
1. **Compose Multiplatform 1.11 reaches GA** (still beta as of May 2026 — currently 1.11.0-beta03).
2. **The in-flight KMP migration completes** (currently mid-phase A5). Layering an AGP-9-KMP-plugin migration onto an unfinished commonTest move is unnecessarily compounding risk.

---

## Why this is more than a version bump

Two breaking changes in Kotlin 2.3 hit this project specifically:

### 1. `androidTarget()` is forbidden under AGP 9 + Kotlin 2.3
> *"Using `androidTarget` with AGP 9.0.0+ causes configuration error. Android target support is now exclusively through Google's `com.android.kotlin.multiplatform.library` plugin."* — [Kotlin 2.3 release notes](https://kotlinlang.org/docs/whatsnew23.html)

This project applies `androidTarget()` in `build-logic/convention/.../KotlinMultiplatformLibraryConventionPlugin.kt` for **14 KMP modules**. The combination AGP 9.0.1 + Kotlin 2.3 + `androidTarget()` is a hard configuration error.

**Fix:** migrate to `com.android.kotlin.multiplatform.library`. Centralised in the convention plugin (one file), but propagates into every KMP module's `build.gradle.kts` because the namespace setter and source-set names change.

### 2. Source-set rename: `androidUnitTest` → `androidHostTest`

Confirmed in a build probe — first error after the convention plugin migrated successfully:
```
KotlinSourceSet with name 'androidUnitTest' not found.
```

The new plugin renames Android-side test source sets:
- `androidUnitTest` → `androidHostTest`
- `androidInstrumentedTest` → `androidDeviceTest`

This requires:
- Updating `val androidUnitTest by getting { ... }` blocks across all 14 KMP modules.
- Renaming **physical directories** `src/androidUnitTest/...` → `src/androidHostTest/...` (and `androidInstrumentedTest` → `androidDeviceTest`) — a `git mv` across many test files.

---

## Required dependency bumps

Verified against current published releases (May 2026).

### Hard breaks — must bump for Kotlin 2.3.20 to load at all

| Dependency | Current | Target | Why |
|---|---|---|---|
| `mokkery` | 2.10.2 | **3.3.0** | Compiler plugin tightly coupled to Kotlin internals; 3.3.0 explicitly fixes "compatibility broken by Kotlin 2.3.20" |
| `compose-multiplatform` | 1.10.3 | **1.11.0-beta03** | CMP 1.10.x stable line is built against Kotlin 2.2.x. Kotlin 2.3.20 requires CMP 1.11 — currently **beta only** |
| `kotlinx-serialization` (json/properties) | 1.9.0 | **1.11.0** | Per JetBrains compat policy: 1.x compiled against Kotlin 2.Y is compatible with 2.(Y+1) but not 2.(Y+2). 1.9.0 was for Kotlin 2.2; 1.11.0 is the recommended pin for Kotlin 2.3.20 |

### Strong-recommend bumps (known breakage / pre-2.3 testing)

| Dependency | Current | Target | Why |
|---|---|---|---|
| `ktor` | 3.3.0 | **3.4.x** (latest patch) | Ktor 3.3.0 has a confirmed bug under Kotlin 2.3.20 — `KTOR-9370 NoSuchMethodError - getLOCAL_FUNCTION_FOR_LAMBDA`. Fix landed in 3.4. Affects `:remote`, `:remote:cheapshark`, `:remote:gamerpower` |
| `sentry-kotlin-multiplatform` | 0.13.0 | **0.16.0+** | 0.13.0 predates Kotlin 2.3 testing. 0.16.0 bumped to Kotlin 2.1.21 (still pre-2.3, but actively maintained). 0.13.0 is ~18 months old by the 2.3 timeline |
| `google-ksp-plugin` | 2.3.2 | **2.3.7** | KSP versioning is now decoupled from Kotlin (KSP 2.3.0+); 2.3.7 is the latest patch with explicit Kotlin 2.3.20 testing |
| `jetbrains-kotlin-plugin` | 2.2.21 | **2.3.20** | The whole point of this upgrade |

### Likely fine, verify on first build

- `kotlinx-coroutines` 1.10.2 — kotlinx libs are unusually tolerant across Kotlin minors
- `kotlinx-datetime` 0.7.0 — same
- `kotlinx-collections-immutable` 0.4.0 — same
- `Room` 2.8.3 — targets Kotlin 2.0+, fine for 2.3. (Room 2.x is in maintenance mode; Room 3.0 is the active KMP package — consider migrating in the same PR)
- `Coil 3` 3.0.4 — pure runtime
- `koin` 4.1.0 — built against Kotlin 2.1.20; runtime-only library, expected backward compat. (Koin 4.2 exists for the new Koin compiler plugin, but this project doesn't use `koin-annotations` so 4.1 is fine)
- AndroidX `compose-ui` 1.9.4, `compose-runtime` 2.9.4, `navigation` 2.9.5, `lifecycle` 2.9.4 — runtime-only; the Compose **Compiler** is bundled with Kotlin and auto-bumps

### Not Kotlin-version-sensitive

- `okhttp`, `retrofit`, `sandwich`, `okio`, `material`, `appcompat`, `firebase-bom` — no Kotlin coupling

---

## Other migration costs the probe surfaced

These are independent of dependency versions — they're API-shape changes in the new Android-KMP plugin.

### `buildFeatures.buildConfig = true` is unsupported

Used in `:remote`, `:remote:cheapshark`, `:remote:gamerpower` for API-key reading. The new `com.android.kotlin.multiplatform.library` plugin does not generate `BuildConfig`. Replacement options:
- [BuildKonfig](https://github.com/yshrsmz/BuildKonfig) (third-party Gradle plugin)
- Move secret-reading to a runtime mechanism (env vars / properties file via Gradle)
- Keep these 3 modules on `com.android.library` and accept they aren't KMP — but they currently *are* KMP, so this is a regression

### `LibraryExtension` config moves to `kotlin.androidLibrary { ... }`

Affected blocks across modules:
- `defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` — `:testing`, `:feature:home`, `:feature:webview`, others — moves to `withDeviceTestBuilder { sourceSetTreeName = "test" }`
- `testOptions.emulatorControl.enable = true` — `:feature:home` — verify the new DSL exposes this
- `buildFeatures.compose = true` — handled by Compose plugin alone in the new model; no extension flag needed
- `buildTypes.named("release") { proguardFiles(...) }` — **not supported** in the new plugin. Library-side proguard rules either move to `consumer-proguard-rules.pro` via `optimization.consumerKeepRules`, or rely on `:app`'s rules

### `packaging.resources.excludes` not exposed

Currently the convention adds:
```kotlin
excludes += "/META-INF/LICENSE.md"
excludes += "/META-INF/LICENSE-notice.md"
excludes += "/META-INF/{AL2.0,LGPL2.1}"
excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"  // OSGi clash from okhttp logging-interceptor 5.2.1 + jspecify 1.0.0
```

The new plugin's DSL doesn't expose packaging excludes (the OSGi workaround comment dates from before that okhttp version — verify if okhttp 5.x has shipped a fix that removes the need).

### `gradle.properties` carries obsolete AGP-8 flags

AGP 9 emits deprecation warnings for:
- `android.usesSdkInManifest.disallowed=false`
- `android.sdk.defaultTargetSdkToCompileSdkIfUnset=false`
- `android.enableAppCompileTimeRClass=false`
- `android.builtInKotlin=false`
- `android.newDsl=false`
- `android.r8.optimizedResourceShrinking=false`
- `android.defaults.buildfeatures.resvalues=true`

All have flipped defaults in AGP 9. Drop them from `gradle.properties` as part of this work — they will become errors at AGP 10.

### `:app` still applies the deprecated `kotlin-android` plugin

AGP 9 has built-in Kotlin support. Drop `org.jetbrains.kotlin.android` from the `:app` plugin block (and from the `AndroidApplicationConventionPlugin`).

### `iosX64()` is tier-3, slated for removal in Kotlin 2.4

Both the convention plugin (`KotlinMultiplatformLibraryConventionPlugin.kt:39`) and `iosApp/build.gradle.kts:11` declare `iosX64()`. This is the **Intel Mac simulator** target — `iosSimulatorArm64` already covers Apple Silicon Mac simulators (the default since 2020). Decision point at upgrade time: drop `iosX64()` now, or hold for one more release. (Kotlin 2.4 will force the issue.)

### iOS deployment-target floor raised 12.0 → 14.0

This project already deploys at iOS 15.0 (per `phase-7.5: bump iOS deployment target 14.0 → 15.0`) so no action needed — but confirm `IPHONEOS_DEPLOYMENT_TARGET` in `iosApp/iosApp.xcodeproj` is still ≥ 14.0 at upgrade time.

---

## Pros — what the upgrade buys

1. **~40% faster `linkRelease*` Kotlin/Native tasks** — meaningful for iOS CI and local release builds
2. **Better Swift interop** — Kotlin enums export as Swift enums (currently plain classes); `vararg` maps to Swift variadics. Improves the `:domain` framework consumption surface in `iosApp`
3. **Stable `kotlin.time.Clock` / `kotlin.time.Instant`** — can phase out remaining `kotlinx-datetime` Instant usage
4. **Compose stack traces in minified release builds** — actual source line numbers in production R8-shrunk crashes
5. **Stable nested type aliases & data-flow-based `when` exhaustiveness**
6. **Optional opt-ins:** unused-return-value checker, explicit backing fields
7. **Aligns with current ecosystem direction** — staying on 2.2 indefinitely will leave you on a constrained version matrix (CMP 1.10 line, Mokkery 2.x, Ktor 3.3)

---

## Estimated effort

**2–3 focused days** when picked up:

| Phase | Effort |
|---|---|
| Version bumps in `libs.versions.toml` | 30 min |
| Convention plugin rewrite (`KotlinMultiplatformLibraryConventionPlugin`, `KotlinMultiplatformLibraryComposeConventionPlugin`) | 2 hrs |
| 14 module `build.gradle.kts` rewrites (namespace move, source-set renames, drop `LibraryExtension` config) | 4–6 hrs |
| `git mv src/androidUnitTest src/androidHostTest` × 14 modules | 1 hr |
| BuildConfig replacement in 3 remote modules (BuildKonfig wiring) | 2–3 hrs |
| `gradle.properties` cleanup + `:app` `kotlin-android` removal | 30 min |
| iOS framework relink + Xcode build verification | 2 hrs |
| Test run + bisect any Mokkery / CMP-beta regressions | 2–4 hrs |
| Drop `iosX64()` (decision-dependent) | 30 min |

Cycle time dominated by full Gradle config-cache rebuilds across 14+ modules each iteration.

---

## Critical files (for execution reference)

- `gradle/libs.versions.toml` — version catalog
- `gradle.properties` — AGP-8 leftover flags to drop
- `build-logic/convention/build.gradle.kts` — classpath for the new `com.android.kotlin.multiplatform.library` plugin
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryConventionPlugin.kt` — the central rewrite point
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryComposeConventionPlugin.kt` — drop `LibraryExtension` reference
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidApplicationConventionPlugin.kt` — remove `kotlin.android` apply
- 14 KMP module `build.gradle.kts` files: `:common`, `:common:ui`, `:domain`, `:logging`, `:remote`, `:remote:cheapshark`, `:remote:gamerpower`, `:testing`, `:feature:deal`, `:feature:game`, `:feature:home`, `:feature:search`, `:feature:giveaways`, `:feature:store`, `:feature:webview`
- `iosApp/build.gradle.kts` — `iosX64()` decision point
- `iosApp/iosApp.xcodeproj/project.pbxproj` — verify `IPHONEOS_DEPLOYMENT_TARGET ≥ 14.0`

---

## Resume conditions

Pick this back up when **all three** of these are true:

1. **Compose Multiplatform 1.11 ships GA** — track [JetBrains/compose-multiplatform releases](https://github.com/JetBrains/compose-multiplatform/releases). The current 1.11.0-betaXX line is the only path to Kotlin 2.3.20 for Android+iOS-only KMP projects.
2. **In-flight KMP migration is past phase A5 / B1** and the active branch tree has stabilised. Layering this onto half-finished commonTest moves compounds review surface.
3. **Mokkery has at least one stable release after 3.3.0 on Kotlin 2.3.20** so the Mokkery codegen has a few weeks of bake time on the target compiler.

A quieter signal: when the Kotlin 2.3.30 patch ships (typically ~3 months after 2.3.20 = ~mid-2026), the surrounding ecosystem will have caught up. That's a low-risk window.

---

## Sources

- [What's new in Kotlin 2.3.0](https://kotlinlang.org/docs/whatsnew23.html)
- [Kotlin 2.3.20 release notes](https://kotlinlang.org/docs/whatsnew2320.html)
- [Updating multiplatform projects with Android apps to use AGP 9](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)
- [Set up the Android Gradle Library Plugin for KMP](https://developer.android.com/kotlin/multiplatform/plugin)
- [Compose Multiplatform compatibility & versioning](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Mokkery releases](https://github.com/lupuuss/Mokkery/releases) — 3.3.0 fixes Kotlin 2.3.20
- [KSP releases](https://github.com/google/ksp/releases) — 2.3.7 latest
- [Compose Multiplatform releases](https://github.com/JetBrains/compose-multiplatform/releases) — 1.11.0-beta03 latest
- [Ktor 3.4 changelog](https://ktor.io/changelog/3.4/) — KTOR-9370 fix
- [kotlinx.serialization compatibility](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/compatibility.md)
- [Sentry KMP changelog](https://github.com/getsentry/sentry-kotlin-multiplatform/blob/main/CHANGELOG.md)
- [Koin 4.2 release notes](https://insert-koin.io/docs/support/releases/)
- [Room 2.x release notes](https://developer.android.com/jetpack/androidx/releases/room) — 2.x is in maintenance mode
