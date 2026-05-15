package pm.bam.gamedeals

import com.android.build.api.dsl.LibraryExtension
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
 * Multiplatform Gradle plugin, flips Android `buildFeatures.compose = true`,
 * wires the universal Compose runtime quartet (runtime / foundation /
 * material3 / ui) plus components.resources into commonMain, and configures
 * `compose.resources` to derive `packageOfResClass` from each module's
 * Android namespace (`<namespace>.generated.resources`).
 */
class KotlinMultiplatformLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }

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

        afterEvaluate {
            val androidNamespace = extensions.getByType<LibraryExtension>().namespace
                ?: error("$path: android namespace must be set before compose.resources can derive packageOfResClass")
            val compose = extensions.getByType<ComposeExtension>() as ExtensionAware
            compose.extensions.configure<ResourcesExtension> {
                publicResClass = true
                packageOfResClass = "$androidNamespace.generated.resources"
                generateResClass = ResourcesExtension.ResourceClassGeneration.Auto
            }
        }

        configureComposeCompilerReports()
    }
}
