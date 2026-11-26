# build-logic

Included build (`includeBuild("build-logic")` in `settings.gradle.kts`) that ships the project's precompiled convention plugins. Module build scripts apply
these by id and inherit all the cross-cutting setup (SDK levels, JDK toolchain, Compose, KSP, coverage, packaging excludes, compiler args).

## Plugin composition

```
                              :app                      iosApp
                                |                         |
            pm.bam.gamedeals.android.application       (bypasses conventions —
                                |                       applies kotlin.multiplatform
                                |                       + compose.multiplatform +
                                |                       compose directly)
                                v
            +-------------------------------------+
            | applies:                            |
            |   com.android.application           |
            |   org.jetbrains.kotlin.plugin.compose|
            |   com.github.skydoves.compose       |
            |       .stability.analyzer           |
            |   com.google.devtools.ksp           |
            +-------------------------------------+

  KMP libraries (:common, :logging, :testing, :remote*, :domain)
    apply pm.bam.gamedeals.kmp.library

  KMP libraries that need Compose (:common:ui)
    apply pm.bam.gamedeals.kmp.library
        + pm.bam.gamedeals.kmp.library.compose

  KMP libraries that need KSP (:domain)
    apply pm.bam.gamedeals.kmp.library
        + pm.bam.gamedeals.kmp.ksp

  Feature modules (:feature:*)
    apply pm.bam.gamedeals.kmp.feature
      which itself applies   pm.bam.gamedeals.kmp.library
                           + pm.bam.gamedeals.kmp.library.compose
                           + pm.bam.gamedeals.kmp.ksp
      and wires the shared feature deps (Koin, lifecycle, Coil, nav, common test stack)

  Root project
    applies pm.bam.gamedeals.coverage.root
      (wires kover + jacoco at the root, configures the aggregator filters)
```

## Plugin reference

| Id | Implementation | Role |
| --- | --- | --- |
| `pm.bam.gamedeals.android.application` | `AndroidApplicationConventionPlugin` | `:app` only. AGP `application`, Compose compiler, KSP, stability analyzer. |
| `pm.bam.gamedeals.kmp.library` | `KotlinMultiplatformLibraryConventionPlugin` | KMP library base. `kotlin.multiplatform` + AGP-9 KMP library, iOS targets, JDK 21, `compileSdk=36`, `minSdk=26`, packaging excludes, iOS sim test serializer. |
| `pm.bam.gamedeals.kmp.library.compose` | `KotlinMultiplatformLibraryComposeConventionPlugin` | Layered on `kmp.library`. Compose compiler + JetBrains Compose Multiplatform + stability analyzer, wires the universal Compose runtime artifacts into commonMain, configures `compose.resources`. |
| `pm.bam.gamedeals.kmp.ksp` | `KotlinMultiplatformKspConventionPlugin` | One-liner that applies `com.google.devtools.ksp`. Per-module processor coords stay in each `build.gradle.kts` (e.g. `add("kspAndroid", libs.room.compiler)`). |
| `pm.bam.gamedeals.kmp.feature` | `KotlinMultiplatformFeatureConventionPlugin` | Composes `kmp.library` + `kmp.library.compose` + `kmp.ksp` and wires the shared feature dependency surface (Koin, lifecycle, Coil, nav, test stack). |
| `pm.bam.gamedeals.coverage.root` | `RootCoverageConventionPlugin` | Applied at the root project only. Brings in kover + jacoco and configures the aggregator's Kover filters from `CoverageFilters`. |

## Shared helpers

- `SharedBuildDefaults.kt` — `sharedFreeCompilerArgs` (`-Xexplicit-backing-fields`, `-Xreturn-value-checker=full`) and `sharedPackagingExcludes` (META-INF
  noise the App Bundle merge step rejects). `iosApp/build.gradle.kts` does NOT use this — it duplicates the compiler args inline. Keep them in sync.
- `CoverageFilters.kt` — single source of truth for Kover (FQN globs) + JaCoCo (path globs) coverage exclusions. Consumed by both the per-module Kover
  config (`configureKover()`) and the root JaCoCo task in `build.gradle.kts`.
- `configureKover()` (in `Kover.kt`) — applies kover at the module and sets the standard filter set; called by both `AndroidApplicationConventionPlugin`
  and `KotlinMultiplatformLibraryConventionPlugin`.
- `configureComposeCompilerReports()` (in `ComposeCompilerReports.kt`) — gated behind `-Pgamedeals.composeReports=true`. Off by default.

## Conventions

- KMP library modules don't set their own `namespace` — it's derived from `project.path` in `KotlinMultiplatformLibraryConventionPlugin`.
- Device-test source set is gated on `src/androidDeviceTest/` existing. Modules without device tests skip the pipeline cleanly — don't add empty
  `src/androidDeviceTest/` dirs.
- Mokkery is applied per-module by intent (`alias(libs.plugins.mokkery)` in each feature `build.gradle.kts`). Don't move it into the feature convention —
  the explicit declaration is the convention here.
- AGP 9 ships built-in Kotlin support; the standalone `org.jetbrains.kotlin.android` plugin is NOT applied anywhere and would conflict if added back.

## Why included build (vs. `buildSrc`)

Included build avoids invalidating every compile when a convention-plugin source file changes — `buildSrc` would. Composition is `pluginManagement {
includeBuild("build-logic") }` in `settings.gradle.kts`. The classpath dependencies (AGP, KGP, KSP, Compose, etc.) live in
`build-logic/convention/build.gradle.kts`.
