package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for KMP feature modules. Composes the KMP library + Compose
 * conventions and applies KSP, then wires the dependency surface area every
 * feature module needs (Koin + lifecycle + Coil + nav + the standard test
 * stack).
 *
 * `compose.*` runtime declarations and the module-level mokkery plugin stay in
 * each module's build.gradle.kts — the compose accessor isn't available from a
 * precompiled plugin, and mokkery is already declared per-module.
 *
 * Test source sets under the AGP 9 KMP library plugin: `androidUnitTest` was
 * renamed to `androidHostTest` and `androidInstrumentedTest` to
 * `androidDeviceTest`. The old `testOptions.emulatorControl.enable` block
 * (used by `androidx.test.espresso.device 1.1.0` for landscape-orientation
 * tests) is not exposed by the new plugin; if a regression surfaces in the
 * espresso-device tests we'll need to find the replacement DSL or skip those
 * tests on CI.
 */
class KotlinMultiplatformFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("pm.bam.gamedeals.kmp.library")
        pluginManager.apply("pm.bam.gamedeals.kmp.library.compose")
        pluginManager.apply("pm.bam.gamedeals.kmp.ksp")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun lib(alias: String) = libs.findLibrary(alias).get()

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.apply {
                commonMain.dependencies {
                    implementation(project(":logging"))
                    implementation(project(":domain"))
                    implementation(project(":common"))
                    implementation(project(":common:ui"))

                    implementation(lib("kotlinx-collections-immutable"))
                    implementation(lib("androidx-lifecycle-viewmodel"))
                    implementation(lib("androidx-lifecycle-runtime-compose"))

                    implementation(lib("koin-core"))
                    implementation(lib("koin-compose-viewmodel"))
                    implementation(lib("androidx-compose-navigation"))

                    implementation(lib("coil3"))
                    implementation(lib("coil3-compose"))
                }

                androidMain.dependencies {
                    implementation(lib("androidx-ktx"))
                    implementation(lib("androidx-appcompat"))
                    implementation(lib("material"))
                    implementation(lib("androidx-lifecycle-viewmodel-compose"))
                    implementation(lib("androidx-ui"))
                    implementation(lib("androidx-ui-graphics"))
                    implementation(lib("androidx-ui-tooling-preview"))
                    implementation(lib("androidx-compose-material3"))
                    implementation(lib("androidx-compose-material3-window"))
                    implementation(lib("androidx-compose-material3-adaptive"))

                    implementation(lib("koin-android"))
                    implementation(lib("koin-androidx-compose"))

                    // Compose tooling moved here from debugImplementation:
                    // the new KMP-library plugin is single-variant so there's
                    // no `debug` configuration on library modules. Slight
                    // binary bloat in release is acceptable for libraries.
                    implementation(lib("androidx-ui-tooling"))
                }

                commonTest.dependencies {
                    implementation(kotlin("test"))
                    implementation(project(":testing"))
                    implementation(lib("coroutines-testing"))
                }

                getByName("androidHostTest").dependencies {
                    implementation(lib("junit"))
                    implementation(lib("mockk"))
                }

                // The androidDeviceTest source set only exists when the
                // library convention plugin opted into withDeviceTestBuilder,
                // which it does only when `src/androidDeviceTest/` exists.
                // Feature modules without device tests (e.g. :feature:favourites)
                // therefore have no source set to configure, and `getByName`
                // would fail. Gate this block on the same condition.
                if (project.file("src/androidDeviceTest").exists()) {
                    getByName("androidDeviceTest").dependencies {
                        implementation(project(":testing"))
                        implementation(lib("mockk-android"))
                        implementation(lib("androidx-junit"))
                        implementation(lib("androidx-runner"))
                        implementation(lib("androidx-espresso-core"))
                        implementation(lib("androidx-compose-junit4"))
                        // Compose UI test manifest moved here from debugImplementation:
                        // the new KMP-library plugin is single-variant, so there's no
                        // `debug` configuration on library modules. The test manifest
                        // is only needed for the androidDeviceTest variant anyway.
                        implementation(lib("androidx-compose-test"))
                    }
                }
            }
        }
    }
}
