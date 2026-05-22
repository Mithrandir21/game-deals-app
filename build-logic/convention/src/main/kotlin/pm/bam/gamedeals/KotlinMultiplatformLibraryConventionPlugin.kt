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
 * Marker [BuildService] used to serialize Kotlin/Native iOS-simulator test tasks across modules. Originally introduced as a workaround for what looked like
 * a parallel test-result XML race; later diagnosis showed the actual failure was Gradle 9's `GenericHtmlTestReportGenerator` mis-deserializing the binary
 * event stream written by KGP's `TCServiceMessagesClient` (an `ArrayIndexOutOfBoundsException` reading `TestOutputEvent.Destination` ordinals). That
 * report-generator crash is now mitigated separately by disabling `reports.html.required` on every `KotlinNativeTest` task below. The serializer is kept
 * because forcing sequential iOS-sim test execution is still useful (concurrent simulator sessions are flaky on macOS) — but it is no longer the
 * load-bearing fix for the test-report failure.
 */
abstract class IosSimulatorTestSerializer : BuildService<BuildServiceParameters.None>

/**
 * Convention plugin for Kotlin Multiplatform library modules with an Android target.
 *
 * - Applies `kotlin.multiplatform` and `com.android.kotlin.multiplatform.library` (the AGP 9 KMP library plugin; the AGP-8 `androidTarget()` +
 *   `com.android.library` combo is incompatible with AGP 9 + Kotlin 2.3).
 * - Registers iOS targets (`iosArm64`, `iosSimulatorArm64`). The Android target is added automatically by the KMP-library plugin; it's configured
 *   below via `targets.withType<KotlinMultiplatformAndroidLibraryTarget>`. `iosX64` (Intel Mac simulator) is not supported under CMP 1.11 + Kotlin 2.3;
 *   Apple-Silicon Mac sim is covered by `iosSimulatorArm64`.
 * - `jvmToolchain(21)`, Android `compileSdk=36`, `minSdk=26`.
 * - Namespace is path-derived in the convention plugin (`:feature:home` -> `pm.bam.gamedeals.feature.home`), so per-module
 *   `extensions.configure<LibraryExtension> { namespace = ... }` blocks should be dropped.
 * - Mockk needs `-XX:+EnableDynamicAgentLoading` for inline-mock byte-buddy agent attach on JDK 21+ — applied to every `Test` task.
 * - Test source sets are renamed by the new plugin: `androidUnitTest` -> `androidHostTest`, `androidInstrumentedTest` -> `androidDeviceTest`.
 *
 * Does NOT apply Compose or KSP. Modules opt into those by also applying `pm.bam.gamedeals.kmp.library.compose` and/or `pm.bam.gamedeals.kmp.ksp`.
 */
class KotlinMultiplatformLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(21)

            compilerOptions {
                freeCompilerArgs.add("-Xexplicit-backing-fields")
                freeCompilerArgs.add("-Xreturn-value-checker=full")
            }

            iosArm64()
            iosSimulatorArm64()

            // The Android target is added automatically by `com.android.kotlin.multiplatform.library`. From a precompiled convention plugin we can't
            // use the `android { }` / `androidLibrary { }` DSL accessor (those are project-script auto-accessors); instead we reach it via
            // `targets.withType<KotlinMultiplatformAndroidLibraryTarget>()`. The new KMP-library plugin's `withDeviceTestBuilder { }` opt-in creates a
            // `connectedAndroidDeviceTest` task even for modules with no device-test source files; that empty test APK then fails on the device with
            // `ClassNotFoundException: androidx.test.runner.AndroidJUnitRunner` because androidx-runner is only declared in the feature convention's
            // androidDeviceTest deps. Gate the opt-in on a real `src/androidDeviceTest/` directory so non-test library modules (e.g. :common, :domain,
            // :logging, :testing) skip the device-test pipeline cleanly.
            val hasDeviceTests = project.file("src/androidDeviceTest").exists()

            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                namespace = "pm.bam.gamedeals" + project.path.replace(":", ".")
                compileSdk = 36
                minSdk = 26

                // AGP 9's KMP-library plugin disables Android resources by default. Compose Multiplatform resources (used in modules applying the
                // compose convention) require the pipeline to be enabled — without this, the CopyResourcesToAndroidAssetsTask for androidDeviceTest is
                // generated without an outputDirectory and the connected tests fail to configure. Enabling here unconditionally keeps the convention
                // plugin uniform; modules that don't use Compose Resources just have an empty pipeline.
                androidResources {
                    enable = true
                }

                withHostTestBuilder {}
                if (hasDeviceTests) {
                    withDeviceTestBuilder {
                        sourceSetTreeName = "test"
                    }.configure {
                        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                        // JaCoCo offline instrumentation for the device-test variant. Kover 0.9.x doesn't consume the resulting `.ec` files (tracked at
                        // kotlinx-kover#96), so the root project's `jacocoAndroidTestReport` task aggregates them into a parallel report alongside
                        // Kover's JVM coverage.
                        enableCoverage = true
                    }
                }

                // Packaging excludes for the test APK. Mokkery 3.x + JUnit 4 pull in transitive junit-jupiter artifacts that each ship their own
                // META-INF/LICENSE.md, colliding at merge time. The AGP-9 KMP-library plugin's `packaging { resources { ... } }` block sets these on
                // the target's outputs.
                packaging.resources.excludes.addAll(
                    listOf(
                        "META-INF/LICENSE.md",
                        "META-INF/LICENSE-notice.md",
                        "META-INF/{AL2.0,LGPL2.1}",
                        "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                    )
                )
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

        configureKover()
    }
}
