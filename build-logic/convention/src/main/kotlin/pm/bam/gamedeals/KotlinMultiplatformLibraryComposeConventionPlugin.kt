package pm.bam.gamedeals

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension

class KotlinMultiplatformLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
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
    }
}
