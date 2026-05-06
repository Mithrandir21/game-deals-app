package pm.bam.gamedeals

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KotlinMultiplatformFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
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
                    implementation(lib("androidx-ui-tooling"))
                    implementation(lib("androidx-compose-material3"))
                    implementation(lib("androidx-compose-material3-window"))
                    implementation(lib("androidx-compose-material3-adaptive"))

                    implementation(lib("koin-android"))
                    implementation(lib("koin-androidx-compose"))
                }

                commonTest.dependencies {
                    implementation(kotlin("test"))
                    implementation(project(":testing"))
                    implementation(lib("coroutines-testing"))
                }

                getByName("androidUnitTest").dependencies {
                    implementation(lib("junit"))
                    implementation(lib("mockk"))
                }

                getByName("androidInstrumentedTest").dependencies {
                    implementation(lib("mockk-android"))
                    implementation(lib("androidx-junit"))
                    implementation(lib("androidx-runner"))
                    implementation(lib("androidx-espresso-core"))
                    implementation(lib("androidx-compose-junit4"))
                }
            }
        }

        dependencies.add("debugImplementation", lib("androidx-compose-test"))

        extensions.configure<LibraryExtension> {
            @Suppress("UnstableApiUsage")
            testOptions.emulatorControl.enable = true
        }
    }
}
