package pm.bam.gamedeals

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for non-feature Android library modules (e.g. :common, :logging,
 * :base, :remote, :remote:cheapshark, :remote:gamerpower, :testing).
 *
 * Applies the Android library + Kotlin Android plugins and the shared compile /
 * packaging configuration. Compose and Hilt are NOT applied here — modules that
 * need those should also apply `pm.bam.gamedeals.android.library.compose` and/or
 * `pm.bam.gamedeals.android.hilt` (or use the broader `pm.bam.gamedeals.android.feature`
 * convention).
 *
 * Per-module dependency declarations stay in each module's build.gradle.kts; this
 * plugin intentionally does not auto-add coordinates to keep convention scope tight.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)

            buildTypes.named("release").configure {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt")
                )
            }
        }
    }
}
