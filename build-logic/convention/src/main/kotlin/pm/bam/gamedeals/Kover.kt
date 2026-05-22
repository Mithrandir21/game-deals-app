package pm.bam.gamedeals

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applies Kover and the project-wide coverage filters from [CoverageFilters]. The root project re-applies the same filter set via
 * [RootCoverageConventionPlugin] so per-module and aggregate reports agree.
 */
internal fun Project.configureKover() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")

    extensions.configure<KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    packages(*CoverageFilters.excludedPackages.toTypedArray())
                    classes(*CoverageFilters.excludedClassGlobs.toTypedArray())
                    CoverageFilters.excludedAnnotations.forEach { annotatedBy(it) }
                }
            }
        }
    }
}
