# Phase 1 — Convention plugin rewrite

**Branch:** `feature/kmp-migration-phase-1-conventions`
**Started:** 2026-05-03
**Scope:** A (minimal — convention plugins + catalog additions + `:common` migration only). 10 other modules stay on legacy `pm.bam.gamedeals.android.*` conventions; they migrate phase-by-phase as their content is touched.

## What was done

### 4 new convention plugins
- `KotlinMultiplatformLibraryConventionPlugin` (id: `pm.bam.gamedeals.kmp.library`) — KMP + `com.android.library`, jvmToolchain(21), iOS targets (iosX64/iosArm64/iosSimulatorArm64), compileSdk=36, minSdk=26, JDK 21, packaging excludes, mockk JVM args. Mirrors the per-module wiring proven in Phase 0.
- `KotlinMultiplatformLibraryComposeConventionPlugin` (id: `pm.bam.gamedeals.kmp.library.compose`) — adds `kotlin.plugin.compose` + `org.jetbrains.compose` (CMP plugin, exposes `compose.runtime` etc. for commonMain) + `buildFeatures.compose = true`. Unused in this phase; will be applied in Phase 5 by feature/UI modules.
- `KotlinMultiplatformKspConventionPlugin` (id: `pm.bam.gamedeals.kmp.ksp`) — applies `com.google.devtools.ksp`. Same shape as the legacy android.ksp convention.
- `KotlinMultiplatformFeatureConventionPlugin` (id: `pm.bam.gamedeals.kmp.feature`) — composes the three above. No universal feature deps wired yet — the legacy convention bakes in Hilt/Coil 2/Material3/Paging which all swap in Phases 4/5; Phase 5 will refine this convention's surface area.

Build-logic registers all four; plugin ids exported via `gradle/libs.versions.toml`.

### Catalog additions
- `compose-multiplatform = "1.10.3"`, `ktor = "3.0.3"`, `koin = "4.0.0"`, `koin-compose = "4.0.0"`, `sentry-kmp = "0.13.0"`, `kotlinx-datetime = "0.6.1"`, `coil3 = "3.0.4"`, `sandwich-ktor = "2.1.3"`.
- Library aliases for: Ktor (core, content-negotiation, logging, okhttp, darwin, mock, kotlinx-json), Koin (core, android, androidx-compose, compose-viewmodel, test), Sentry KMP, kotlinx-datetime, Coil 3 (core, compose, network-ktor), AndroidX paging-common (multiplatform-capable), Room runtime (KMP-ready at 2.7+), sandwich-ktor.
- Versions chosen from training-cutoff knowledge; **may need a version bump when each phase actually consumes them**. Cheap to update.

### `:common` migration
- `common/build.gradle.kts` rewritten from manual KMP setup to `alias(libs.plugins.gamedeals.kmp.library)` + `kmp.ksp` + `kotlinx.serialization`. Drops ~50 LOC of inline KMP/Android boilerplate.
- The convention plugin handles iOS targets, jvmToolchain, compileSdk/minSdk, JDK 21, packaging excludes, release buildType, mockk JVM args. `:common`'s build script now contains only what's actually module-specific: source-set deps, `namespace`, KSP processors.

## Build verification

| Task | Result |
|---|---|
| `:common:assembleDebug` | ✅ 18s |
| `:common:test` | ✅ |
| `:common:compileKotlinIosSimulatorArm64` | ✅ |
| `:common:compileKotlinIosArm64` | ✅ |
| `:common:compileKotlinIosX64` | ✅ |
| `:app:assembleDebug` | ✅ 45s, 379 tasks |
| `./gradlew test` (whole project) | DEFERRED — run before merge |

## Build hiccup worth noting

First iOS-targets run after the libs.versions.toml change appeared to "stall" — actually it was `:common:downloadKotlinNativeDistribution` (~1GB Kotlin/Native sysroot fetch) running silently. The misleading "stall" was amplified by my own command piping output through `tail -30`, which buffers everything until input closes. Lesson for future phases: when running an iOS-touching gradle task for the first time, stream output with `tee` (not `tail`) so progress is visible.

## Deviations from the plan

- **Plan's Phase 1 said "every existing module compiles under the new conventions".** Pragmatically downscoped to only `:common`. Migrating the other 10 modules now would require a "no-content-change but new build wiring" pass on each, which is mostly busywork that Phases 2/3/5 redo anyway. Each module migrates to KMP conventions when its *content* migrates.

## Lessons (candidates for `.claude/lessons.md` retrospective)

- **Convention plugin coexistence is fine.** Old `android.library` plugins and new `kmp.library` plugins can live side by side in the same project. Modules switch one at a time. No big-bang gradle rewrite needed.
- **`compose-multiplatform-gradle-plugin` artifact** lives at `org.jetbrains.compose:compose-gradle-plugin` (not `compose-multiplatform-gradle-plugin`). Caught at convention-plugin compile time.
- **Configuration cache invalidates on `libs.versions.toml` change.** Expected, but adds ~10s to first build after a catalog edit. Plan iOS toolchain downloads to happen on a fresh-cache build so the noise is concentrated.

## Next phase

Phase 2 — `:domain` storage + datetime swap. Move `:domain` to KMP module (using the new `kmp.library` convention), migrate `java.time` → `kotlinx-datetime`, migrate Room → Room KMP. Per the plan, this is the first phase that *changes runtime behavior* (datetime parsing/formatting).
