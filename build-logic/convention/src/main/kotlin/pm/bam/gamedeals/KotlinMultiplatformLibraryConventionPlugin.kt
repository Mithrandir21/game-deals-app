package pm.bam.gamedeals

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for Kotlin Multiplatform library modules with an Android target.
 *
 * - Applies `kotlin.multiplatform` and `com.android.library`.
 * - Registers Android + iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
 * - `jvmToolchain(21)`, Android `compileSdk=36`, `minSdk=26`, JDK 21 source/target.
 * - Mockk needs `-XX:+EnableDynamicAgentLoading` for inline-mock byte-buddy
 *   agent attach on JDK 21+ — applied to every `Test` task.
 *
 * Does NOT apply Compose or KSP. Modules opt into those by also applying
 * `pm.bam.gamedeals.kmp.library.compose` and/or `pm.bam.gamedeals.kmp.ksp`.
 */
class KotlinMultiplatformLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(21)

            androidTarget()
            iosX64()
            iosArm64()
            iosSimulatorArm64()
        }

        extensions.configure<LibraryExtension> {
            compileSdk = 36
            defaultConfig {
                minSdk = 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }

            buildTypes.named("release").configure {
                isMinifyEnabled = false
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            }

            packaging.resources.apply {
                excludes += "/META-INF/LICENSE.md"
                excludes += "/META-INF/LICENSE-notice.md"
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
                // Temporary fix for OSGi issue with org.jspecify:jspecify:1.0.0
                // and com.squareup.okhttp3:logging-interceptor:5.2.1
                excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }

        // Required by Mockk's inline mock-maker / byte-buddy agent attach on JDK 21+.
        tasks.withType(Test::class.java).configureEach {
            jvmArgs("-XX:+EnableDynamicAgentLoading")
        }
    }
}
