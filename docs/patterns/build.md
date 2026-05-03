---
**Path scope:** `build-logic/**`, `gradle/libs.versions.toml`, `*/build.gradle.kts`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Build

A multi-module Android app with composition-ready Gradle convention plugins. Five conventions orchestrate consistent SDK levels, JDK targeting, Hilt + Compose integration, and KSP wiring. All annotation processors use KSP (no KAPT). Compose Compiler is wired via the Kotlin plugin alone — `composeOptions` is intentionally absent on Kotlin 2.2. App signing reads credentials from local properties or CI environment variables.

## Patterns

### Composable Convention Plugins

**Status:** established
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

### Centralized SDK / JDK / Packaging Defaults

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all modules

**The pattern.**
`AndroidCommon.kt` defines `configureAndroidCommon()`, called by both library and application conventions. It sets `compileSdk = 36`, `minSdk = 26`, JDK 21 source/target, and OSGi-safe packaging excludes (`META-INF/{AL2.0,LGPL2.1}`, `META-INF/versions/9/OSGI-INF/MANIFEST.MF` for jspecify + okhttp-logging). MockK's inline mock-maker on JDK 21+ requires `-XX:+EnableDynamicAgentLoading`, which is baked into all `Test` task JVM args.

**Why this works for us.**
Policy enforced in one place — no scattered `compileSdk` or `minSdk`. OSGi packaging conflicts are pre-solved. JVM toolchain (21) aligns with source/target and the Kotlin `jvmToolchain()` call.

**Known trade-offs / when it strains.**
SDK bumps are a single deliberate edit (acknowledged in the file comment). A future module needing a different `minSdk` would break the shared function and force a fork.

**How to apply it.**
```kotlin
// AndroidCommon.kt
internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
  extension.compileSdk = 36
  extension.defaultConfig.minSdk = 26
  // packaging excludes, jvmToolchain, Test jvmArgs…
}

// Convention plugin:
extensions.configure<ApplicationExtension> {
  configureAndroidCommon(this)
  buildFeatures.compose = true
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidCommon.kt
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidApplicationConventionPlugin.kt
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidLibraryConventionPlugin.kt

### Version Catalog with Aliased Plugin Bundles

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** 40+ libraries, 12 plugins, 6 convention plugins

**The pattern.**
A single `gradle/libs.versions.toml` holds Android Gradle Plugin 9.0.1, Kotlin 2.2.21, KSP 2.3.2, the Compose Compiler (via Kotlin), Hilt 2.57.2, Room 2.8.3, Retrofit 3.0.0, Firebase BOM, Paging 3.3.6, Coil 2.7.0, and test frameworks. All standard plugins use `alias(libs.plugins.*)`; convention plugins are also registered as plugin aliases (e.g., `gamedeals.android.feature`). Convention plugins themselves are resolved via explicit dependency declarations in build-logic's own `build.gradle.kts` (Gradle plugin JARs as `compileOnly`).

**Why this works for us.**
One source of truth for versions. No skew between the main project and build-logic. Plugin aliases catch typos at config time. Custom conventions sit alongside standard plugins for visual consistency.

**Known trade-offs / when it strains.**
TOML syntax is less IDE-friendly than Kotlin DSL in older Android Studio versions. Adding a new library or plugin requires two edits (version section + library/plugin section).

**How to apply it.**
```toml
# gradle/libs.versions.toml
[versions]
hilt-core = "2.57.2"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt-core" }

[plugins]
gamedeals-android-feature = { id = "pm.bam.gamedeals.android.feature" }
```

```kotlin
// :feature:*/build.gradle.kts
plugins {
  alias(libs.plugins.gamedeals.android.feature)
}
```

**Seen in.**
- gradle/libs.versions.toml
- settings.gradle.kts
- build-logic/settings.gradle.kts

### KSP over KAPT

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** Hilt, Room, all code-generation stacks

**The pattern.**
Every module needing annotation processing uses `com.google.devtools.ksp` (applied by `AndroidKspConventionPlugin` or directly). Hilt compiler, AndroidX Hilt compiler, and Room compiler are declared as `ksp(…)` dependencies — never `kapt(…)`. The KSP plugin is applied separately from the Hilt Gradle plugin (`com.google.dagger.hilt.android`); only `:base` and `:app` apply the Hilt Gradle plugin.

**Why this works for us.**
KSP is faster than KAPT and integrates cleanly with Kotlin 2.2.21. Hilt Gradle plugin is opt-in, preserving module-level intent — modules that don't need it (e.g., `:testing`, `:logging`) don't pay the cost. KSP is infrastructure; Hilt plugin is optional.

**Known trade-offs / when it strains.**
If a future processor doesn't support KSP, the codebase has no pre-built KAPT convention. Mixing KSP and KAPT in a single module complicates the mental model.

**How to apply it.**
```kotlin
// AndroidKspConventionPlugin.kt
class AndroidKspConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("com.google.devtools.ksp")
  }
}

// :domain/build.gradle.kts
plugins {
  alias(libs.plugins.gamedeals.android.library)
  alias(libs.plugins.gamedeals.android.ksp)
}
dependencies {
  ksp(libs.hilt.compiler)
  ksp(libs.room.compiler)
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidKspConventionPlugin.kt
- domain/build.gradle.kts
- app/build.gradle.kts

### Compose Compiler Plugin Without Legacy `composeOptions`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** every Compose-enabled module

**The pattern.**
`AndroidLibraryComposeConventionPlugin` applies `org.jetbrains.kotlin.plugin.compose` and sets `buildFeatures.compose = true`. The `composeOptions { kotlinCompilerExtensionVersion = "…" }` block is intentionally absent — under Kotlin 2.2.21 with the Compose Compiler Gradle plugin, that config is ignored. Feature modules declare their own Compose library dependencies (runtime, Material3, UI) because not every Compose user needs the same surface (e.g., `:feature:webview` omits Paging and Coil).

**Why this works for us.**
No dead config. Kotlin 2.2.21 + Compose Compiler plugin handle version alignment transparently. Modules are explicit about which Compose layers they consume.

**Known trade-offs / when it strains.**
Developers from pre-2.2 codebases may add `composeOptions` by habit and be confused when it's ignored. A custom lint rule would flag this.

**How to apply it.**
```kotlin
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    extensions.configure<LibraryExtension> {
      buildFeatures.compose = true
      // NOT: composeOptions { kotlinCompilerExtensionVersion = … }
    }
  }
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidLibraryComposeConventionPlugin.kt

### App-Layer Signing with Local / CI Dual Source

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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

### Gradle Configuration Cache & Parallel Execution

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** project-wide

**The pattern.**
`gradle.properties` enables `org.gradle.configuration-cache=true` and `org.gradle.parallel=true`. JVM args are tuned to `-Xmx4096m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8`. Non-transitive R classes (`android.nonTransitiveRClass=true`) shrink R class size per module.

**Why this works for us.**
Configuration cache cuts build times significantly on incremental builds. Parallel execution on multi-core CI is a free speedup. Explicit metaspace sizing prevents OOM on resource-constrained agents.

**Known trade-offs / when it strains.**
Configuration cache can hide stale-config bugs; tasks that write to shared resources can become non-deterministic. Older third-party plugins are sometimes not cache-compatible.

**How to apply it.**
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
org.gradle.configuration-cache=true
org.gradle.parallel=true
android.nonTransitiveRClass=true
```

**Seen in.**
- gradle.properties

## What we don't do

- **No custom lint rules.** No `lint.xml`, no per-module lint configuration. **Why we avoid it:** the codebase relies on default Android Lint plus Kotlin compiler diagnostics; adding custom rules is not yet warranted.
- **No detekt or other static analysis plugin.** **Why we avoid it:** convention plugins keep style boilerplate centralized; dedicated static analysis is on the table but not yet wired.
- **No Maven / artifactory publishing.** Monorepo only — modules are not published. **Why we avoid it:** there are no external consumers.
- **No Kotlin Multiplatform.** No `expect` / `actual`, no `commonMain`. **Why we avoid it:** Android-only product surface; KMP would add complexity without payoff.
- **No dynamic feature modules.** All features compile into the app APK. **Why we avoid it:** install size isn't a current constraint; on-demand delivery would complicate Hilt and navigation wiring.
- **No Compose compiler metrics.** No per-module composables / recomposition reports. **Why we avoid it:** not yet a hot enough surface to require profiling.
- **No KAPT.** KSP-only. **Why we avoid it:** KAPT is slower and is already being deprecated upstream.
