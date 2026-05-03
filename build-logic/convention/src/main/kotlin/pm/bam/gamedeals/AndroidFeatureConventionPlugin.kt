package pm.bam.gamedeals

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for the six "core" `:feature:*` modules
 * (home, deal, game, giveaways, search, store).
 *
 * Composes the library + Compose conventions. Adds the feature-module-specific
 * defaults: AndroidJUnitRunner test runner and the Espresso emulator-control
 * test option.
 *
 * Wires the dependencies that *every one of those six* modules needs
 * (Koin + Compose + Material3 + Paging + Coil + tracing + the standard test
 * stack). Module-specific deps stay in each module's build.gradle.kts.
 *
 * `:feature:webview` deliberately does NOT use this convention — it has no
 * Koin, no Paging and no Coil, so forcing the feature convention onto it
 * would either add unused deps or splinter the convention's contract.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("pm.bam.gamedeals.android.library")
        pluginManager.apply("pm.bam.gamedeals.android.library.compose")

        extensions.configure<LibraryExtension> {
            defaultConfig.testInstrumentationRunner =
                "androidx.test.runner.AndroidJUnitRunner"

            @Suppress("UnstableApiUsage")
            testOptions.emulatorControl.enable = true
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        dependencies.apply {
            add("implementation", libs.findLibrary("androidx-ktx").get())
            add("implementation", libs.findLibrary("androidx-appcompat").get())
            add("implementation", libs.findLibrary("material").get())
            add("implementation", libs.findLibrary("androidx-compose-navigation").get())
            add("implementation", libs.findLibrary("androidx-compose-runtime").get())
            add("implementation", libs.findLibrary("androidx-ui").get())
            add("implementation", libs.findLibrary("androidx-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-ui-tooling").get())
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("implementation", libs.findLibrary("androidx-compose-material3-window").get())
            add("implementation", libs.findLibrary("androidx-compose-material3-adaptive").get())

            add("implementation", libs.findLibrary("koin-core").get())
            add("implementation", libs.findLibrary("koin-android").get())
            add("implementation", libs.findLibrary("koin-androidx-compose").get())
            add("implementation", libs.findLibrary("koin-compose-viewmodel").get())

            add("implementation", libs.findLibrary("androidx-paging").get())
            add("implementation", libs.findLibrary("androidx-paging-compose").get())
            add("implementation", libs.findLibrary("coil").get())
            add("implementation", libs.findLibrary("coil-compose").get())
            add("implementation", libs.findLibrary("coil-test").get())

            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("coroutines-testing").get())

            add("androidTestImplementation", libs.findLibrary("mockk-android").get())
            add("androidTestImplementation", libs.findLibrary("androidx-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-runner").get())
            add("androidTestImplementation", libs.findLibrary("androidx-espresso-core").get())
            add("androidTestImplementation", libs.findLibrary("androidx-compose-junit4").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-test").get())
        }
    }
}
