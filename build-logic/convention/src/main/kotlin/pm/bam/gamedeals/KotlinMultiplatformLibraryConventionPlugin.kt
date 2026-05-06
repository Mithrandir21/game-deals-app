package pm.bam.gamedeals

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest

/**
 * Marker [BuildService] used to serialize Kotlin/Native iOS-simulator test
 * tasks across modules. With Gradle 9.1 + Kotlin 2.2.21, running multiple
 * `iosSimulatorArm64Test` tasks in parallel races on the test-result XML
 * writer and randomly fails one task's report. Holding a single permit on
 * this service forces sequential execution for those tasks while leaving
 * everything else free to parallelize.
 */
abstract class IosSimulatorTestSerializer : BuildService<BuildServiceParameters.None>

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
            configureAndroidCommon(this)

            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            buildTypes.named("release").configure {
                isMinifyEnabled = false
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            }
        }

        val iosTestSerializer = gradle.sharedServices.registerIfAbsent(
            "iosSimulatorTestSerializer",
            IosSimulatorTestSerializer::class.java,
        ) {
            maxParallelUsages.set(1)
        }
        tasks.withType(KotlinNativeHostTest::class.java).configureEach {
            usesService(iosTestSerializer)
        }
    }
}
