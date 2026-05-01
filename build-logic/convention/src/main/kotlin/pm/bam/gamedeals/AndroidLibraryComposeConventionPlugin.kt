package pm.bam.gamedeals

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Adds Jetpack Compose to a module that already applies an Android library
 * convention (or `com.android.library` directly).
 *
 * Applies the Kotlin Compose Compiler Gradle plugin and flips
 * `buildFeatures.compose = true`. Under Kotlin 2.x with that plugin applied
 * `composeOptions.kotlinCompilerExtensionVersion` is unused / ignored — that
 * dead config is intentionally not carried forward (issue #22 acceptance
 * criterion).
 *
 * Compose runtime / UI / Material3 coordinates stay as per-module dependency
 * declarations because not every Compose-using module wants the same surface
 * area (see :common:ui vs :feature:webview).
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
    }
}
