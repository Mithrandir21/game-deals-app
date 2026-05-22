package pm.bam.gamedeals

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applies Kover and the project-wide coverage filters.
 *
 * Filters drop code that isn't worth measuring — DI modules, Compose
 * Multiplatform resource accessors, BuildConfig, Compose-compiler synthetics.
 * The root `build.gradle.kts` re-applies the same filter set on the
 * aggregated report so per-module and aggregate views agree.
 */
internal fun Project.configureKover() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")

    extensions.configure<KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    packages(
                        "*.di",
                        "*.generated.resources",
                    )
                    classes(
                        "*BuildConfig",
                        "*ComposableSingletons*",
                        "*\$\$serializer",
                    )
                    annotatedBy("*Generated*")
                }
            }
        }
    }
}
