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
 * Composes the library + Compose conventions and applies KSP. Adds the
 * feature-module-specific defaults: AndroidJUnitRunner test runner and the
 * Espresso emulator-control test option.
 *
 * Also wires the dependencies that *every one of those six* modules needs
 * (Hilt + Compose + Material3 + Paging + Coil + tracing + the standard test
 * stack). Module-specific deps (project() references, the small handful of
 * extras like `androidx.compose.material3.window` callers, …) stay in each
 * module's build.gradle.kts.
 *
 * `:feature:webview` deliberately does NOT use this convention — it has no
 * Hilt, no Paging and no Coil, so forcing the feature convention onto it
 * would either add unused deps or splinter the convention's contract.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("pm.bam.gamedeals.android.library")
        pluginManager.apply("pm.bam.gamedeals.android.library.compose")
        pluginManager.apply("pm.bam.gamedeals.android.ksp")

        extensions.configure<LibraryExtension> {
            defaultConfig.testInstrumentationRunner =
                "androidx.test.runner.AndroidJUnitRunner"

            @Suppress("UnstableApiUsage")
            testOptions.emulatorControl.enable = true
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        dependencies.apply {
            // Universal Android + Material + Compose surface for feature modules.
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

            // Hilt — every core feature uses Hilt + navigation-compose; the KSP
            // processors are universal too.
            add("implementation", libs.findLibrary("hilt-android").get())
            add("implementation", libs.findLibrary("hilt-navigation-compose").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
            add("ksp", libs.findLibrary("hilt-androidx-compiler").get())

            // Paging + Coil — every core feature consumes paged lists and image
            // loading; coil-test is included on implementation because the
            // pre-refactor modules all did so (used by Compose preview fakes).
            add("implementation", libs.findLibrary("androidx-paging").get())
            add("implementation", libs.findLibrary("androidx-paging-compose").get())
            add("implementation", libs.findLibrary("coil").get())
            add("implementation", libs.findLibrary("coil-compose").get())
            add("implementation", libs.findLibrary("coil-test").get())

            // Standard JVM unit-test stack used by every core feature.
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("coroutines-testing").get())

            // Standard instrumented-test stack used by every core feature.
            add("androidTestImplementation", libs.findLibrary("mockk-android").get())
            add("androidTestImplementation", libs.findLibrary("androidx-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-runner").get())
            add("androidTestImplementation", libs.findLibrary("androidx-espresso-core").get())
            add("androidTestImplementation", libs.findLibrary("androidx-compose-junit4").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-test").get())
        }
    }
}
