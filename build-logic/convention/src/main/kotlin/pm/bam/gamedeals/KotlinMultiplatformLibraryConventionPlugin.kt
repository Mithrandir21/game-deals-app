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
 * Mirrors the per-module wiring proven in Phase 0 on `:common`:
 * - Applies `kotlin.multiplatform` and `com.android.library`.
 * - Registers Android + iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64` — the
 *   "Standard" set chosen for this migration).
 * - jvmToolchain(21), Android compileSdk=36, minSdk=26, JDK 21 source/target.
 * - Packaging excludes carried over from the Android-only convention.
 * - Mockk's `-XX:+EnableDynamicAgentLoading` JVM arg for unit tests on JDK 21+.
 *
 * Notably does NOT apply Compose. Modules that need Compose should also apply
 * `pm.bam.gamedeals.kmp.library.compose`. Modules that need KSP should apply
 * `pm.bam.gamedeals.kmp.ksp`.
 *
 * Coexists with the legacy `pm.bam.gamedeals.android.library` convention. Modules
 * migrate from the Android-only convention to this one as part of phases 2/3/5.
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
