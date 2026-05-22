package pm.bam.gamedeals

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Convention plugin for the single `:app` Android application module.
 *
 * Applies `com.android.application`, the Compose compiler plugin, and KSP. The Kotlin Android plugin (`org.jetbrains.kotlin.android`) is no longer applied
 * here: AGP 9 has built-in Kotlin support enabled by default, so applying the standalone plugin would conflict.
 *
 * Per-app concerns (signing config, version metadata, dependency wiring) stay in `:app/build.gradle.kts`. KMP-library modules configure their own Android
 * compile/packaging defaults inline via [KotlinMultiplatformLibraryConventionPlugin] — they don't share this plugin's code path.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("com.github.skydoves.compose.stability.analyzer")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<ApplicationExtension> {
            compileSdk = 36
            defaultConfig.minSdk = 26
            compileOptions.sourceCompatibility = JavaVersion.VERSION_21
            compileOptions.targetCompatibility = JavaVersion.VERSION_21
            packaging.resources.excludes.addAll(sharedPackagingExcludes)
            buildFeatures.compose = true
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(21)

            compilerOptions {
                freeCompilerArgs.addAll(sharedFreeCompilerArgs)
            }
        }

        // Mockk inline mock-maker on JDK 21+ needs byte-buddy agent attach.
        tasks.withType(Test::class.java).configureEach {
            jvmArgs("-XX:+EnableDynamicAgentLoading")
        }

        configureComposeCompilerReports()
        configureKover()
    }
}
