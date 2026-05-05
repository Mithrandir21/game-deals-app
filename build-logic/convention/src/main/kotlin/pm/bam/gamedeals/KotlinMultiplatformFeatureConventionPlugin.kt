package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for KMP feature modules. Composes the KMP library + Compose
 * conventions and applies KSP.
 *
 * Kept minimal — feature modules opt in to module-specific deps (Material3,
 * Coil 3, Koin) directly in their own build.gradle.kts.
 */
class KotlinMultiplatformFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("pm.bam.gamedeals.kmp.library")
        pluginManager.apply("pm.bam.gamedeals.kmp.library.compose")
        pluginManager.apply("pm.bam.gamedeals.kmp.ksp")
    }
}
