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
 * Adds Compose Multiplatform on top of `pm.bam.gamedeals.kmp.library`. Applies the Compose Compiler + JetBrains Compose Multiplatform plugins, wires the
 * universal Compose runtime artifacts (runtime / foundation / material3 / ui / icons / resources / preview) into commonMain, and configures
 * `compose.resources` with a path-derived `packageOfResClass`.
 */
class KotlinMultiplatformLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("com.github.skydoves.compose.stability.analyzer")

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
