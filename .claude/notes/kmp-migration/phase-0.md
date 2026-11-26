# Phase 0 — Toolchain + KMP proof-of-life

**Branch:** `feature/kmp-migration-phase-0-toolchain`
**Started:** 2026-05-03
**Status:** Build-green; manual golden-journey smoke pending

## What was done

1. KDoctor verified — env ready (only warnings: KMM Android Studio plugin not installed, CocoaPods UTF-8 — neither blocks anything).
2. CMP version target chosen: **1.10.3**, compatible with Kotlin **2.2.21** (no Kotlin/AGP bump in Phase 0).
3. Version catalog: added one versionless plugin alias `kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform" }`.
4. Two exception classes in `:common` refactored to pass message via `Exception(message)` super constructor (drops `getLocalizedMessage()` override which doesn't exist on Kotlin/Native's `Throwable`).
5. `:common` source split into KMP layout:
   - 6 files → `commonMain/kotlin/`: `FlowExtensions.kt`, `exceptions/{DataExistsException,DataNotFoundException}.kt`, `navigation/Destination.kt`, `serializer/Serializer.kt`, `time/Clock.kt`.
   - 10 files → `androidMain/kotlin/`: everything else (Hilt-bound, java.time-bound, SharedPreferences-bound code stays Android-only until Phases 2/4).
   - 4 test files → `androidUnitTest/kotlin/`.
   - `AndroidManifest.xml` → `androidMain/`.
6. `SingleEventEffect` (the only `@Composable` in `:common`) moved to `:common:ui` (the only consumer was `feature/home/.../HomeScreen.kt`). One import line updated.
7. `:common/build.gradle.kts` rewritten as KMP: `kotlin("multiplatform")` + `com.android.library` + `kotlin.ksp` + `kotlinx.serialization`. iOS targets: `iosX64`, `iosArm64`, `iosSimulatorArm64` (Standard set).
8. Convention plugins (`gamedeals.android.library`, `gamedeals.android.library.compose`, `gamedeals.android.ksp`) dropped from `:common` — they apply `kotlin.android` which conflicts with `kotlin.multiplatform`. The other 10 modules keep using them. `:common` is the deliberate odd-one-out until Phase 1.

## Build verification

| Task | Result |
|---|---|
| `:common:assembleDebug` | ✅ |
| `:common:test` | ✅ |
| `:common:compileKotlinIosSimulatorArm64` | ✅ |
| `:common:compileKotlinIosArm64` | ✅ |
| `:common:compileKotlinIosX64` | ✅ |
| `:app:assembleDebug` | ✅ (379 tasks, 57s) |
| `./gradlew test` (whole project) | ✅ |

## Deviations from the plan

- **`SingleEventEffect` moved to `:common:ui`** — not in the original Phase 0 plan, but forced by the toolchain: applying the Compose Compiler plugin to a multiplatform module requires Compose runtime on *every* target's classpath including iOS. Adding CMP runtime to `commonMain` was premature. Moving the single `@Composable` to `:common:ui` (which already has the Compose stack) lets `:common` drop the Compose plugin entirely. Aligns with `docs/patterns/architecture.md` which already says "`:common` has minimal Compose deps".

## Lessons (candidates for `.claude/lessons.md` retrospective)

- **Versionless plugin alias.** When the plugin's gradle artifact is already on the buildscript classpath via `build-logic`'s `kotlin-gradle-plugin` dep, the catalog alias must be versionless (`{ id = "..." }` with no `version.ref`). Otherwise gradle errors with "plugin already on the classpath with an unknown version, so compatibility cannot be checked". This is why the existing convention-plugin aliases in this repo are also versionless.
- **`platform(...)` inside KMP source-set DSL is deprecated** (KT-58759). Declare BOMs at the top-level `dependencies { add("androidMainImplementation", platform(...)) }` block instead.
- **Use `extensions.configure<LibraryExtension> { ... }` for the Android block in KMP modules**, not the `android { }` DSL — the latter resolves to a deprecated overload under `kotlin.multiplatform`.
- **Compose Compiler plugin in KMP requires Compose runtime on every target.** If a module has only one `@Composable` file, moving that file to a Compose-native module is cheaper than pulling in CMP runtime on every target.

## Golden-journey status

Manual smoke pass on Android device — **PENDING** (requires user to launch app). No iOS app exists yet (Phase 6).

## Next phase

Phase 1 — convention plugin rewrite. With one KMP module proven, generalize to a `KotlinMultiplatformLibraryConventionPlugin` so the remaining 10 modules can be migrated without per-module boilerplate.
