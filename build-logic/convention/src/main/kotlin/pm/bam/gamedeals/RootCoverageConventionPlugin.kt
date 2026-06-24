package pm.bam.gamedeals

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

/**
 * Applied at the root project. Wires the Kover aggregator and the JaCoCo plugin, then configures the root-level Kover filters from [CoverageFilters].
 *
 * Module aggregation (`kover(project(":x"))`) and the `jacocoAndroidTestReport` task body stay in the root `build.gradle.kts` — both are project-shape data,
 * not reusable convention. The JaCoCo task pulls its excludes from [CoverageFilters.jacocoPathGlobs].
 */
class RootCoverageConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlinx.kover")
        pluginManager.apply("jacoco")

        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.13"
        }

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
}
