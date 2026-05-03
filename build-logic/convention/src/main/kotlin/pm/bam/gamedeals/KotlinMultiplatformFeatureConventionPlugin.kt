package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for KMP feature modules. Composes the KMP library + Compose
 * conventions and applies KSP.
 *
 * Phase 1 keeps this minimal — feature modules only migrate in Phase 5, at
 * which point we'll know whether the universal feature deps (Material3, Paging,
 * Coil 3, Koin) belong here or stay per-module. The existing
 * `AndroidFeatureConventionPlugin` baked Hilt + Coil 2 + Material3 + Paging in;
 * we'll mirror that surface area in Phase 5 once the underlying libs (Koin,
 * Coil 3, paging-multiplatform) are actually in use.
 */
class KotlinMultiplatformFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("pm.bam.gamedeals.kmp.library")
        pluginManager.apply("pm.bam.gamedeals.kmp.library.compose")
        pluginManager.apply("pm.bam.gamedeals.kmp.ksp")
    }
}
