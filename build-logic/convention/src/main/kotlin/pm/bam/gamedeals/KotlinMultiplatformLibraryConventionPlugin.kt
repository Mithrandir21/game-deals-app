package pm.bam.gamedeals

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

/** Serializes Kotlin/Native iOS-simulator test tasks across modules. Concurrent macOS simulator sessions are flaky, so we cap [maxParallelUsages] at 1. */
abstract class IosSimulatorTestSerializer : BuildService<BuildServiceParameters.None>

/**
 * Convention plugin for KMP library modules with an Android target.
 *
 * Applies `kotlin.multiplatform` + `com.android.kotlin.multiplatform.library` (AGP 9's KMP library plugin), registers `iosArm64` + `iosSimulatorArm64`, sets
 * `jvmToolchain(21)`, `compileSdk=36`, `minSdk=26`, and derives the Android namespace from `project.path`. Does NOT apply Compose or KSP — modules opt into
 * those by also applying `pm.bam.gamedeals.kmp.library.compose` and/or `pm.bam.gamedeals.kmp.ksp`.
 *
 * Test source-set names under the new plugin: `androidUnitTest` -> `androidHostTest`, `androidInstrumentedTest` -> `androidDeviceTest`.
 */
class KotlinMultiplatformLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(21)

            compilerOptions {
                freeCompilerArgs.addAll(sharedFreeCompilerArgs)
            }

            iosArm64()
            iosSimulatorArm64()

            // `withDeviceTestBuilder { }` creates `connectedAndroidDeviceTest` even for modules with no device-test source files, which then fails at
            // runtime with `ClassNotFoundException: AndroidJUnitRunner`. Gate the opt-in on a real `src/androidDeviceTest/` so non-test modules skip the
            // device-test pipeline cleanly.
            val hasDeviceTests = project.file("src/androidDeviceTest").exists()

            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                namespace = "pm.bam.gamedeals" + project.path.replace(":", ".")
                compileSdk = 36
                minSdk = 26

                // Enable the Android resources pipeline (off by default in AGP 9's KMP-library plugin). Compose Multiplatform resources need it; without
                // it, `CopyResourcesToAndroidAssetsTask` has no outputDirectory and `androidDeviceTest` fails to configure.
                androidResources {
                    enable = true
                }

                withHostTestBuilder {}
                if (hasDeviceTests) {
                    withDeviceTestBuilder {
                        sourceSetTreeName = "test"
                    }.configure {
                        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                        // JaCoCo offline instrumentation for device tests. Kover 0.9.x doesn't consume the resulting `.ec` files (kotlinx-kover#96), so
                        // the root `jacocoAndroidTestReport` task aggregates them separately.
                        enableCoverage = true
                    }
                }

                packaging.resources.excludes.addAll(sharedPackagingExcludes)
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
            // KGP 2.3 ↔ Gradle 9 incompatibility
            reports.html.required.set(false)
            reports.junitXml.required.set(false)
        }

        // `allTests` reuses Gradle 9's generic report generator, which mis-deserializes KGP's output-events.bin — same bug as KotlinNativeTest.reports above.
        tasks.withType(TestReport::class.java).configureEach {
            enabled = false
        }

        configureKover()
    }
}
