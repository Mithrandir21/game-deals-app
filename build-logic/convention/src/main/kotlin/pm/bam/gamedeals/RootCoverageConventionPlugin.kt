package pm.bam.gamedeals

import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
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

                // Coverage floor for the aggregated host-test report — `koverVerify` fails the build below it.
                // A ratchet, not a target: it guards against logic tests being deleted or uncovered code
                // growing back. The aggregate is diluted by device-tested Compose UI (0% under host Kover),
                // so the number is intentionally low; raise it whenever a coverage-improving change lands.
                verify {
                    rule {
                        minBound(COVERAGE_FLOOR_PERCENT, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
                    }
                }
            }
        }
    }

    private companion object {
        // Current aggregated line coverage is ~33%; start a few points under so the floor holds without
        // failing today's build, then ratchet up on every coverage-improving PR.
        const val COVERAGE_FLOOR_PERCENT = 30
    }
}
