package pm.bam.gamedeals

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for the single `:app` Android application module.
 *
 * Mirrors [AndroidLibraryConventionPlugin] but for `com.android.application`,
 * and additionally applies the Compose compiler plugin and KSP because the app
 * always needs both. Hilt / Firebase / signing-config / version metadata stay
 * in `:app/build.gradle.kts` because they are inherently single-module
 * concerns.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            buildFeatures.compose = true
        }
    }
}
