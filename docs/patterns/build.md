---
**Path scope:** `build-logic/**`, `gradle/libs.versions.toml`, `*/build.gradle.kts`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Build

A multi-module KMP-first app with a rewritten Gradle convention-plugin family. The conventions now apply `com.android.kotlin.multiplatform.library` (the AGP 9 KMP-library plugin) on top of `org.jetbrains.kotlin.multiplatform`, register `iosArm64` + `iosSimulatorArm64` targets, and derive each module's Android namespace from its Gradle path. The stack is AGP 9.1.1, Kotlin 2.3.21, KSP 2.3.8, JDK 21 toolchain. The `:app` release build runs R8 minification with `isShrinkResources` (21MB → 6.9MB). The Compose Stability Analyzer is wired by the Compose convention; the correctness gate (baselines, `debugStabilityCheck`) lives under the compose-correctness pattern doc. All annotation processors use KSP. Compose Compiler is wired via `org.jetbrains.kotlin.plugin.compose` alone — no `composeOptions` block. App signing reads credentials from local properties or CI environment variables.

## Patterns

### KotlinMultiplatformLibraryConventionPlugin (`pm.bam.gamedeals.kmp.library`)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every KMP library module

**The pattern.**
The convention applies `org.jetbrains.kotlin.multiplatform` plus `com.android.kotlin.multiplatform.library` (the AGP 9 KMP-library plugin), then registers `iosArm64()` and `iosSimulatorArm64()` targets. It sets `compileSdk = 36`, `minSdk = 26`, and the JDK 21 toolchain. The Android namespace is derived from `project.path` (`:feature:home` → `pm.bam.gamedeals.feature.home`). Android resources are enabled unconditionally because the KMP-library plugin disables them by default and the Compose Multiplatform resource pipeline needs them on. `withDeviceTestBuilder { }` is opted into only when `src/androidDeviceTest/` exists. The `IosSimulatorTestSerializer` BuildService is wired to every `KotlinNativeTest`. Mockk's JDK-21 agent flag and the Mokkery 3.x license excludes are applied here.

**Why this works for us.**
One plugin to apply across every library module; all AGP 9 + Kotlin 2.3 quirks (KMP-library plugin, Android-resources default-off, conditional device-test, native-test serializer) are encoded in one file rather than scattered across modules.

**Known trade-offs / when it strains.**
A large amount of policy lives in a single file — changes there ripple across every library. The Android target is always included; modules can't selectively opt out of Android. Namespace derivation depends on Gradle-path naming discipline.

**How to apply it.**
```kotlin
class KotlinMultiplatformLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("com.android.kotlin.multiplatform.library")
    extensions.configure<KotlinMultiplatformExtension> {
      jvmToolchain(21)
      iosArm64(); iosSimulatorArm64()
      targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java) {
        namespace = "pm.bam.gamedeals" + project.path.replace(":", ".")
        compileSdk = 36; minSdk = 26
        if (file("src/androidDeviceTest").exists()) withDeviceTestBuilder { }
      }
    }
    // IosSimulatorTestSerializer wiring + Mockk + Mokkery excludes…
  }
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryConventionPlugin.kt
- build-logic/convention/build.gradle.kts

**Related lessons.** L-2026-05-17-07, L-2026-05-17-09, L-2026-05-17-14, L-2026-05-17-15

### KotlinMultiplatformLibraryComposeConventionPlugin (`pm.bam.gamedeals.kmp.library.compose`)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every Compose-using KMP library

**The pattern.**
The Compose convention layers on top of the library convention. It applies `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.compose`, and `com.github.skydoves.compose.stability.analyzer`. It wires the Compose runtime, Compose resources, and tooling-preview dependencies into `commonMain`. The Compose resources namespace is also derived from `project.path`. Android resources stay enabled (required for the CMP resource pipeline).

**Why this works for us.**
Compose-specific wiring stays separate from the bare library convention, so modules without Compose stay lean. The Stability Analyzer is enabled here at the build level; the correctness gate (baseline files + the `debugStabilityCheck` task) is owned by the compose-correctness discipline.

**Known trade-offs / when it strains.**
Opting into Compose now requires applying a second convention plugin per module. Pure data/remote modules without Compose stay on the bare library convention.

**How to apply it.**
```kotlin
class KotlinMultiplatformLibraryComposeConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    pluginManager.apply("pm.bam.gamedeals.kmp.library")
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    pluginManager.apply("org.jetbrains.compose")
    pluginManager.apply("com.github.skydoves.compose.stability.analyzer")
    extensions.configure<KotlinMultiplatformExtension> {
      sourceSets.commonMain.dependencies {
        implementation(compose.runtime); implementation(compose.components.resources)
      }
    }
  }
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryComposeConventionPlugin.kt

### KotlinMultiplatformFeatureConventionPlugin (`pm.bam.gamedeals.kmp.feature`)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every feature module

**The pattern.**
The feature convention composes the library + compose + KSP conventions. It auto-wires the feature dependency surface to the correct source sets: Koin (`koin-core`, `koin-compose-viewmodel`) and lifecycle into `commonMain`, Coil and navigation into `commonMain`, the multiplatform test stack into `commonTest`. AGP 9's KMP-library plugin renames the test source sets (`androidUnitTest` → `androidHostTest`, `androidInstrumentedTest` → `androidDeviceTest`); the feature convention gates the `androidDeviceTest` dep block on `src/androidDeviceTest/` existence to avoid the AGP-9 ClassNotFoundException on modules with no device tests.

**Why this works for us.**
Every feature applies one plugin and inherits the whole feature surface. Dep versions and source-set wiring live in one place to update.

**Known trade-offs / when it strains.**
Convention-hidden deps mean reading a feature's `build.gradle.kts` doesn't reveal that Koin, Coil, lifecycle, and navigation are wired — onboarding hazard. A feature that doesn't use everything still pays the cost.

**How to apply it.**
```kotlin
class KotlinMultiplatformFeatureConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    pluginManager.apply("pm.bam.gamedeals.kmp.library.compose")
    pluginManager.apply("pm.bam.gamedeals.kmp.ksp")
    extensions.configure<KotlinMultiplatformExtension> {
      sourceSets.commonMain.dependencies {
        implementation(libs.koin.core); implementation(libs.koin.compose.viewmodel)
        implementation(libs.lifecycle); implementation(libs.coil); implementation(libs.navigation)
      }
      sourceSets.commonTest.dependencies { implementation(kotlin("test")); implementation(libs.mokkery) }
      if (file("src/androidDeviceTest").exists()) sourceSets.androidDeviceTest.dependencies { /* … */ }
    }
  }
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformFeatureConventionPlugin.kt

### IosSimulatorTestSerializer BuildService

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every `KotlinNativeTest` task across all KMP modules

**The pattern.**
A marker `BuildService` with `maxParallelUsages = 1` is registered. Every `KotlinNativeTest` task calls `usesService(iosTestSerializer)` so that iOS-simulator test tasks run sequentially across modules. Under Gradle 9.1 plus Kotlin 2.x, parallel iOS-simulator test tasks race on the test-result XML writer and randomly fail one module's report. The serializer forces sequential execution for that narrow slice only; the rest of the build keeps full parallelism.

**Why this works for us.**
Surgical fix — only iOS-simulator tests serialize; all other tasks remain free to parallelize. Removes a flaky-test class without slowing the whole build.

**Known trade-offs / when it strains.**
iOS-simulator test runs are now sequential, so the iOS-test phase is slower than it could be. Stays in place until the upstream Gradle/Kotlin XML-writer race is fixed.

**How to apply it.**
```kotlin
abstract class IosSimulatorTestSerializer : BuildService<BuildServiceParameters.None>
val iosTestSerializer = gradle.sharedServices.registerIfAbsent(
  "iosSimulatorTestSerializer", IosSimulatorTestSerializer::class.java
) { maxParallelUsages.set(1) }
tasks.withType(KotlinNativeTest::class.java).configureEach { usesService(iosTestSerializer) }
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryConventionPlugin.kt

**Related lessons.** L-2026-05-06-01

### Path-derived namespace

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every KMP library module

**The pattern.**
The convention plugin computes each module's Android namespace from the Gradle path: `"pm.bam.gamedeals" + project.path.replace(":", ".")`. So `:feature:home` becomes `pm.bam.gamedeals.feature.home`. Per-module `namespace = "…"` lines in `build.gradle.kts` are unnecessary and have been removed.

**Why this works for us.**
Zero per-module boilerplate; renames are automatic when a module moves; namespace is consistent with the Gradle path.

**Known trade-offs / when it strains.**
Relies on path-naming discipline (`:feature:*`, `:remote:*`, `:common:*`). A module needing a custom namespace would have to override the convention-set value in its `build.gradle.kts` — not currently done anywhere.

**How to apply it.**
```kotlin
// inside the library convention
targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java) {
  namespace = "pm.bam.gamedeals" + project.path.replace(":", ".")
}
// in :feature:home/build.gradle.kts — note: NO `namespace = ...` line.
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryConventionPlugin.kt
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryComposeConventionPlugin.kt

### Centralized SDK / JDK / Packaging Defaults (`AndroidCommon.kt`)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all modules

**The pattern.**
`AndroidCommon.kt` defines `configureAndroidCommon()`, called by both the library and application conventions. It sets `compileSdk = 36`, `minSdk = 26`, JDK 21 source/target plus `jvmToolchain(21)`, and OSGi-safe packaging excludes (`META-INF/{AL2.0,LGPL2.1}`, `META-INF/versions/9/OSGI-INF/MANIFEST.MF` for jspecify + okhttp-logging, and the Mokkery 3.x license-conflict excludes). MockK's inline mock-maker on JDK 21+ requires `-XX:+EnableDynamicAgentLoading`, baked into all `Test` task JVM args.

**Why this works for us.**
Policy enforced in one place — no scattered `compileSdk` or `minSdk`. OSGi and Mokkery packaging conflicts are pre-solved. JVM toolchain (21) aligns with source/target and the Kotlin `jvmToolchain()` call.

**Known trade-offs / when it strains.**
SDK bumps are a single deliberate edit. A future module needing a different `minSdk` would break the shared function and force a fork.

**How to apply it.**
```kotlin
internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
  extension.compileSdk = 36
  extension.defaultConfig.minSdk = 26
  extension.packaging.resources.excludes += setOf(
    "META-INF/AL2.0", "META-INF/LGPL2.1",
    "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
    // Mokkery 3.x license-conflict excludes…
  )
  tasks.withType<Test>().configureEach { jvmArgs("-XX:+EnableDynamicAgentLoading") }
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidCommon.kt
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryConventionPlugin.kt

### Version Catalog with Aliased Plugin Bundles

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** 40+ libraries, 12+ plugins, the KMP convention plugin family

**The pattern.**
A single `gradle/libs.versions.toml` pins AGP 9.1.1, Kotlin 2.3.21, KSP 2.3.8, Compose Compiler (via Kotlin), Compose Stability Analyzer 0.7.5, Koin, Coil, Ktor, Mokkery 3.x, and the test frameworks. The AGP 9 KMP-library plugin is registered under the `androidKmpLibrary` alias. All standard plugins use `alias(libs.plugins.*)`; convention plugins are registered as plugin aliases (e.g., `gamedeals.kmp.library`, `gamedeals.kmp.library.compose`, `gamedeals.kmp.feature`, `gamedeals.kmp.ksp`). The old `android-*` aliases (`gamedeals.android.library`, `gamedeals.android.library.compose`, `gamedeals.android.feature`, `gamedeals.android.ksp`) have been removed. Convention plugins themselves are resolved via explicit dependency declarations in build-logic's own `build.gradle.kts` (Gradle plugin JARs as `compileOnly`).

**Why this works for us.**
One source of truth for versions. No skew between the main project and build-logic. Plugin aliases catch typos at config time. The single `androidKmpLibrary` alias keeps the AGP-9 plugin id off every `build.gradle.kts`.

**Known trade-offs / when it strains.**
Adding a new library or plugin still requires two edits (version section + library/plugin section). TOML is less IDE-friendly than Kotlin DSL.

**How to apply it.**
```toml
# gradle/libs.versions.toml
[versions]
agp = "9.1.1"
kotlin = "2.3.21"
ksp = "2.3.8"
stability-analyzer = "0.7.5"

[plugins]
androidKmpLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
gamedeals-kmp-feature = { id = "pm.bam.gamedeals.kmp.feature" }
```
```kotlin
// :feature:home/build.gradle.kts
plugins { alias(libs.plugins.gamedeals.kmp.feature) }
```

**Seen in.**
- gradle/libs.versions.toml
- settings.gradle.kts
- build-logic/settings.gradle.kts

**Related lessons.** L-2026-05-04-07, L-2026-05-18-01

### KSP over KAPT

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every code-generation stack

**The pattern.**
Every module needing annotation processing uses `com.google.devtools.ksp` (applied by `KotlinMultiplatformKspConventionPlugin` or directly). Annotation-processor dependencies are declared as `ksp(…)` — never `kapt(…)`. The KSP plugin is applied separately from any consumer-specific Gradle plugin so individual modules opt in.

**Why this works for us.**
KSP is faster than KAPT and integrates cleanly with Kotlin 2.3.21 plus KMP source sets. KSP-as-infrastructure stays out of modules that don't need codegen (e.g., `:logging`).

**Known trade-offs / when it strains.**
If a future processor doesn't support KSP, the codebase has no pre-built KAPT convention to fall back on. Mixing KSP and KAPT in a single module is not supported by these conventions.

**How to apply it.**
```kotlin
class KotlinMultiplatformKspConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("com.google.devtools.ksp")
  }
}
// :domain/build.gradle.kts
plugins { alias(libs.plugins.gamedeals.kmp.library); alias(libs.plugins.gamedeals.kmp.ksp) }
dependencies { ksp(libs.some.compiler) }
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformKspConventionPlugin.kt
- domain/build.gradle.kts
- app/build.gradle.kts

**Related lessons.** L-2026-05-17-10

### Compose Compiler Plugin Without Legacy `composeOptions`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every Compose-enabled module

**The pattern.**
The Compose convention applies `org.jetbrains.kotlin.plugin.compose` — the Compose Compiler Gradle plugin — and lets it own version alignment. The legacy `composeOptions { kotlinCompilerExtensionVersion = "…" }` block is intentionally absent: under Kotlin 2.3.21 with the Compose Compiler Gradle plugin, that config is ignored. Compose dependencies are declared per source set (typically `commonMain`).

**Why this works for us.**
No dead config. Kotlin 2.3.21 + Compose Compiler plugin handle version alignment transparently. Modules are explicit about which Compose layers they consume.

**Known trade-offs / when it strains.**
Developers from pre-2.2 codebases may add `composeOptions` by habit and be confused when it's ignored.

**How to apply it.**
```kotlin
// inside KotlinMultiplatformLibraryComposeConventionPlugin
pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
pluginManager.apply("org.jetbrains.compose")
// NOT: composeOptions { kotlinCompilerExtensionVersion = … }
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformLibraryComposeConventionPlugin.kt

### App-Layer Signing with Local / CI Dual Source

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** `:app` only

**The pattern.**
`:app/build.gradle.kts` reads signing credentials from two sources: if `local.properties` exists (dev machine), it loads `keyAlias`, `keyPassword`, `storePassword` from that file; otherwise, if CI env vars `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_STORE_PASSWORD` are set, it uses those. The keystore path is `../upload_keystore.jks`. If neither source is present, `releaseSigningKey` falls back to `debug` so local development keeps working.

**Why this works for us.**
Local development needs no env vars; CI has no hardcoded secrets in the repo. The fallback to debug keeps unsigned dev builds buildable.

**Known trade-offs / when it strains.**
The keystore path is relative — moving the repo or changing CI's working directory breaks it. No abstraction for per-flavor signing or multi-key scenarios. The two source-loading blocks are easy to drift apart.

**How to apply it.**
```kotlin
// :app/build.gradle.kts
var releaseKeyPresent = false
if (File(rootProject.rootDir, "local.properties").exists()) {
  val localProperties = Properties().apply { load(…) }
  releaseKeyPresent = true
  releaseKeyAlias = localProperties.getProperty("keyAlias")
  // …
} else if (System.getenv("RELEASE_KEY_ALIAS") != null) {
  releaseKeyPresent = true
  releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
  // …
}
if (releaseKeyPresent) {
  signingConfigs {
    create("release") { /* … */ }
  }
}
```

**Seen in.**
- app/build.gradle.kts

### R8 Minification + `isShrinkResources` on `:app` Release

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** `:app` release buildType

**The pattern.**
The `release` buildType in `:app/build.gradle.kts` enables `isMinifyEnabled = true` together with `isShrinkResources = true`, then points at `proguard-rules.pro` for reflection-touched classes (Koin, kotlinx.serialization, etc.). The mapping file is emitted to `app/build/outputs/mapping/release/mapping.txt`. Net effect on the release APK: 21MB → 6.9MB.

**Why this works for us.**
Standard R8 setup; `isShrinkResources` strips unused resources alongside the code shrink. The mapping file lets Sentry/Logcat traces be de-obfuscated via the runbook at `docs/r8-mapping.md`.

**Known trade-offs / when it strains.**
Every release build requires `proguard-rules.pro` to stay current as reflection-using deps are added. Mapping files must be archived per release for stack-trace decoding. Local debug builds skip R8 to keep iteration fast.

**How to apply it.**
```kotlin
android {
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
}
// proguard-rules.pro
-keep class org.koin.** { *; }
-keepclassmembers,allowobfuscation class * { @kotlinx.serialization.Serializable <fields>; }
```

**Seen in.**
- app/build.gradle.kts
- app/proguard-rules.pro

**References.**
- docs/r8-mapping.md

### Platform-specific Dependencies Scoped to Source Sets

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every KMP module

**The pattern.**
In each KMP module's `build.gradle.kts`, dependencies are declared per source set. Cross-platform deps go in `commonMain`. Platform-specific engines/SDKs go in `androidMain` or `iosMain`. Tests split into `commonTest`, `androidHostTest`, and (when present) `androidDeviceTest`. Concrete examples: `ktor-client-okhttp` lives only in `androidMain`, `ktor-client-darwin` only in `iosMain`; `koin-android` only in `androidMain`; Sentry-KMP only in `androidMain` (Sentry-Cocoa is not wired); Mokkery + `kotlin.test` in `commonTest`.

**Why this works for us.**
The platform boundary is explicit: iOS doesn't drag in JVM-only deps and vice versa; tests pull only what they need per platform; the build fails fast when a JVM-only dep leaks into `commonMain`.

**Known trade-offs / when it strains.**
Per-module dep wiring is more verbose than the old single `dependencies { }` block. Mitigated by version-catalog aliases and by the feature convention auto-wiring the common surface.

**How to apply it.**
```kotlin
// :remote/build.gradle.kts
kotlin {
  sourceSets {
    commonMain.dependencies { implementation(libs.ktor.core); implementation(libs.kotlinx.serialization.json) }
    androidMain.dependencies { implementation(libs.ktor.client.okhttp); implementation(libs.koin.android) }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    commonTest.dependencies { implementation(kotlin("test")); implementation(libs.mokkery) }
  }
}
```

**Seen in.**
- remote/build.gradle.kts
- logging/build.gradle.kts
- common/build.gradle.kts
- feature/home/build.gradle.kts

**Related lessons.** L-2026-05-04-01, L-2026-05-04-04

### Gradle Configuration Cache & Parallel Execution

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** project-wide

**The pattern.**
`gradle.properties` enables `org.gradle.configuration-cache=true` and `org.gradle.parallel=true`. JVM args are tuned to `-Xmx8192m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8`. Non-transitive R classes (`android.nonTransitiveRClass=true`) shrink R class size per module.

**Why this works for us.**
Configuration cache cuts build times significantly on incremental builds. Parallel execution on multi-core CI is a free speedup. The 8192m heap accommodates the AGP 9 + Kotlin 2.3 + KMP + Compose configuration phase without OOM.

**Known trade-offs / when it strains.**
Configuration cache can hide stale-config bugs; tasks that write to shared resources can become non-deterministic (see the `IosSimulatorTestSerializer` for an example). Older third-party plugins are sometimes not cache-compatible.

**How to apply it.**
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
org.gradle.configuration-cache=true
org.gradle.parallel=true
android.nonTransitiveRClass=true
```

**Seen in.**
- gradle.properties

## Decommissioned

### Composable Convention Plugins

**Status:** deprecated (AGP 9 + Kotlin 2.3 require the KMP plugin path; superseded by `KotlinMultiplatformLibraryConventionPlugin` family — see new entries)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** every module (app, library, library-compose, feature, ksp)

**The pattern.**
Five convention plugins compose to minimize per-module boilerplate. `AndroidLibraryConventionPlugin` applies Android Library + Kotlin Android and delegates SDK/JDK setup to `AndroidCommon`. `AndroidLibraryComposeConventionPlugin` layers on the Kotlin Compose Compiler plugin. `AndroidKspConventionPlugin` is standalone; the Hilt Gradle plugin is NOT applied by convention so `:base` and `:app` opt in. `AndroidFeatureConventionPlugin` chains library + compose + ksp and auto-wires the typical feature dependency set (Hilt, Material3, Paging, Coil, test stack). `AndroidApplicationConventionPlugin` is library + Compose + KSP + the Android Application plugin.

**Why this works for us.**
Each plugin has a single semantic. Modules apply only what they need. The six core feature modules are interchangeable under the convention; `:feature:webview` deliberately rejects feature convention to skip unused Hilt, Paging, and Coil. Common dependencies are declared once in the convention, not scattered.

**Known trade-offs / when it strains.**
Feature convention encodes all "typical" feature deps, so a new feature inherits some bloat if it doesn't use everything. KSP processor selection (Hilt vs Room vs both) is still manual per module since the set varies.

**How to apply it.**
```kotlin
// :feature:home/build.gradle.kts
plugins {
  alias(libs.plugins.gamedeals.android.feature)  // library + compose + ksp + deps
}
android {
  namespace = "pm.bam.gamedeals.feature.home"
}
dependencies {
  implementation(project(":domain"))
  implementation(libs.compose.material.icons)
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidFeatureConventionPlugin.kt
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidLibraryConventionPlugin.kt
- feature/home/build.gradle.kts
- feature/deal/build.gradle.kts

## What we don't do

- **No `androidTarget() + com.android.library` combo.** **Why we avoid it:** that pairing is incompatible with AGP 9 + Kotlin 2.3 — use the AGP 9 KMP-library plugin (`com.android.kotlin.multiplatform.library`) instead.
- **No `iosX64` target.** **Why we avoid it:** Compose Multiplatform 1.11 + Kotlin 2.3 dropped Intel-Mac simulator support; `iosSimulatorArm64` covers the Apple-Silicon simulator and is the only path forward.
- **No per-module `extensions.configure<LibraryExtension> { namespace = ... }`.** **Why we avoid it:** the convention plugin derives namespace from `project.path`. A per-module `namespace = "…"` line is dead config under the new conventions.
- **No `testOptions.emulatorControl.enable` DSL.** **Why we avoid it:** the AGP 9 KMP-library plugin removed this DSL; builds that still reference it fail.
- **No custom lint rules.** No `lint.xml`, no per-module lint configuration. **Why we avoid it:** the codebase relies on default Android Lint plus Kotlin compiler diagnostics; adding custom rules is not yet warranted.
- **No detekt or other static analysis plugin.** **Why we avoid it:** convention plugins keep style boilerplate centralized; dedicated static analysis is on the table but not yet wired.
- **No Maven / artifactory publishing.** Monorepo only — modules are not published. **Why we avoid it:** there are no external consumers.
- **No dynamic feature modules.** All features compile into the app APK. **Why we avoid it:** install size isn't a current constraint; on-demand delivery would complicate Koin and navigation wiring.
- **No Compose compiler metrics.** No per-module composables / recomposition reports. **Why we avoid it:** the Stability Analyzer covers the correctness concern; per-module recomposition metrics are not yet warranted.
