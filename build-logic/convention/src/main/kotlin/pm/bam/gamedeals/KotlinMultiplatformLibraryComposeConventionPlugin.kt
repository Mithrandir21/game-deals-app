package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds Compose Multiplatform to a Kotlin Multiplatform library that already
 * applies `pm.bam.gamedeals.kmp.library`.
 *
 * Applies the Kotlin Compose Compiler plugin and the JetBrains Compose
 * Multiplatform Gradle plugin, wires the universal Compose runtime quartet
 * (runtime / foundation / material3 / ui) plus components.resources into
 * commonMain, and configures `compose.resources` to derive
 * `packageOfResClass` from the module's path-derived namespace.
 *
 * Under the AGP 9 KMP library plugin there is no `LibraryExtension.buildFeatures.compose`
 * to flip — the Compose plugin alone enables composition. Namespace is
 * derived from `project.path` (matches the KMP-library convention plugin).
 */
class KotlinMultiplatformLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            val compose = ComposePlugin.Dependencies(target)
            sourceSets.commonMain.dependencies {
                @Suppress("DEPRECATION")
                implementation(compose.runtime)
                @Suppress("DEPRECATION")
                implementation(compose.foundation)
                @Suppress("DEPRECATION")
                implementation(compose.material3)
                @Suppress("DEPRECATION")
                implementation(compose.ui)
                @Suppress("DEPRECATION")
                implementation(compose.materialIconsExtended)
                @Suppress("DEPRECATION")
                implementation(compose.components.resources)
                @Suppress("DEPRECATION")
                implementation(compose.components.uiToolingPreview)
            }
        }

        val androidNamespace = "pm.bam.gamedeals" + project.path.replace(":", ".")
        val compose = extensions.getByType<ComposeExtension>() as ExtensionAware
        compose.extensions.configure<ResourcesExtension> {
            publicResClass = true
            packageOfResClass = "$androidNamespace.generated.resources"
            generateResClass = ResourcesExtension.ResourceClassGeneration.Auto
        }

        configureComposeCompilerReports()
    }
}
