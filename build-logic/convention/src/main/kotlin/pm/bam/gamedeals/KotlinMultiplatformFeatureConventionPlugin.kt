package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for KMP feature modules. Composes the KMP library + Compose conventions, applies KSP, and wires the shared feature-module dependency
 * surface (Koin, lifecycle, Coil, nav, common test stack). Mokkery stays per-module; the device-test deps block is gated on `src/androidDeviceTest/`
 * existing so feature modules without device tests skip the source set entirely.
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

                    // AGP 9's KMP-library plugin is single-variant — no `debug` configuration on library modules. Slight release bloat is acceptable.
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

                // Gate must match the device-test opt-in in [KotlinMultiplatformLibraryConventionPlugin]: if `src/androidDeviceTest/` is absent, the
                // source set doesn't exist and `getByName` would fail.
                if (project.file("src/androidDeviceTest").exists()) {
                    getByName("androidDeviceTest").dependencies {
                        implementation(project(":testing"))
                        implementation(lib("mockk-android"))
                        implementation(lib("androidx-junit"))
                        implementation(lib("androidx-runner"))
                        implementation(lib("androidx-espresso-core"))
                        implementation(lib("androidx-compose-junit4"))
                        implementation(lib("androidx-compose-test"))
                    }
                }
            }
        }
    }
}
