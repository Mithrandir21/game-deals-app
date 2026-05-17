package pm.bam.gamedeals

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

/**
 * Marker [BuildService] used to serialize Kotlin/Native iOS-simulator test
 * tasks across modules. With Gradle 9.1 + Kotlin 2.x, running multiple
 * `iosSimulatorArm64Test` tasks in parallel races on the test-result XML
 * writer and randomly fails one task's report. Holding a single permit on
 * this service forces sequential execution for those tasks while leaving
 * everything else free to parallelize.
 */
abstract class IosSimulatorTestSerializer : BuildService<BuildServiceParameters.None>

/**
 * Convention plugin for Kotlin Multiplatform library modules with an Android target.
 *
 * - Applies `kotlin.multiplatform` and `com.android.kotlin.multiplatform.library`
 *   (the AGP 9 KMP library plugin — replaces the old `androidTarget()` +
 *   `com.android.library` combo, forbidden under AGP 9 + Kotlin 2.3).
 * - Registers iOS targets (`iosArm64`, `iosSimulatorArm64`). The Android
 *   target is added automatically by the KMP-library plugin; it's
 *   configured below via `targets.withType<KotlinMultiplatformAndroidLibraryTarget>`.
 *   `iosX64` was dropped — CMP 1.11 + Kotlin 2.3 no longer support Apple
 *   x86_64 (Intel Mac simulator); Apple-Silicon Mac sim is covered by
 *   `iosSimulatorArm64`.
 * - `jvmToolchain(21)`, Android `compileSdk=36`, `minSdk=26`.
 * - Namespace is path-derived in the convention plugin
 *   (`:feature:home` -> `pm.bam.gamedeals.feature.home`), so per-module
 *   `extensions.configure<LibraryExtension> { namespace = ... }` blocks
 *   should be dropped.
 * - Mockk needs `-XX:+EnableDynamicAgentLoading` for inline-mock byte-buddy
 *   agent attach on JDK 21+ — applied to every `Test` task.
 * - Test source sets are renamed by the new plugin: `androidUnitTest` ->
 *   `androidHostTest`, `androidInstrumentedTest` -> `androidDeviceTest`.
 *
 * Does NOT apply Compose or KSP. Modules opt into those by also applying
 * `pm.bam.gamedeals.kmp.library.compose` and/or `pm.bam.gamedeals.kmp.ksp`.
 */
class KotlinMultiplatformLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(21)

            iosArm64()
            iosSimulatorArm64()

            // The Android target is added automatically by
            // `com.android.kotlin.multiplatform.library`. From a precompiled
            // convention plugin we can't use the `android { }` / `androidLibrary { }`
            // DSL accessor (those are project-script auto-accessors); instead we
            // reach it via `targets.withType<KotlinMultiplatformAndroidLibraryTarget>()`.
            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                namespace = "pm.bam.gamedeals" + project.path.replace(":", ".")
                compileSdk = 36
                minSdk = 26

                withHostTestBuilder {}
                withDeviceTestBuilder {
                    sourceSetTreeName = "test"
                }.configure {
                    instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }
        }

        // Mockk inline mock-maker on JDK 21+ needs byte-buddy agent attach.
        tasks.withType(Test::class.java).configureEach {
            jvmArgs("-XX:+EnableDynamicAgentLoading")
        }

        val iosTestSerializer = gradle.sharedServices.registerIfAbsent(
            "iosSimulatorTestSerializer",
            IosSimulatorTestSerializer::class.java,
        ) {
            maxParallelUsages.set(1)
        }
        tasks.withType(KotlinNativeTest::class.java).configureEach {
            usesService(iosTestSerializer)
        }
    }
}
