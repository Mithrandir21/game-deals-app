package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies KSP for Kotlin Multiplatform modules that use annotation-processor-style
 * code generation (Hilt's compiler in androidMain during the migration; later
 * Room-KMP across multiple targets in Phase 2).
 *
 * Mirrors the existing `pm.bam.gamedeals.android.ksp` convention: just applies the
 * KSP Gradle plugin. Modules declare per-target processor coordinates with
 * `dependencies { add("kspAndroid", libs.hilt.compiler) }` etc.
 *
 * Deliberately does NOT apply the Hilt Gradle plugin — Hilt is a Phase 4 swap to
 * Koin, and during migration only the small number of modules that originally
 * needed the Hilt Gradle plugin (`:base`, `:app`) keep applying it directly.
 */
class KotlinMultiplatformKspConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.google.devtools.ksp")
    }
}
