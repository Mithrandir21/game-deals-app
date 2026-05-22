package pm.bam.gamedeals

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Convention plugin for the single `:app` Android application module.
 *
 * Applies `com.android.application`, the Compose compiler plugin, and KSP.
 * The Kotlin Android plugin (`org.jetbrains.kotlin.android`) is no longer
 * applied here: AGP 9 has built-in Kotlin support enabled by default
 * (paired with dropping `android.builtInKotlin=false` from
 * `gradle.properties`), so applying the standalone plugin would conflict.
 *
 * Per-app concerns (signing config, version metadata, dependency wiring)
 * stay in `:app/build.gradle.kts`.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("com.github.skydoves.compose.stability.analyzer")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            buildFeatures.compose = true
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(21)

            compilerOptions {
                freeCompilerArgs.add("-Xexplicit-backing-fields")
                freeCompilerArgs.add("-Xreturn-value-checker=full")
            }
        }

        configureComposeCompilerReports()
        configureKover()
    }
}
