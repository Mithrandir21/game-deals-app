package pm.bam.gamedeals

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Adds Compose Multiplatform to a Kotlin Multiplatform library that already
 * applies `pm.bam.gamedeals.kmp.library` (or the underlying KMP + Android-library
 * plugins).
 *
 * Applies:
 * - `org.jetbrains.kotlin.plugin.compose` — the Kotlin Compose Compiler plugin.
 *   Required for `@Composable` processing on every target. Note: applying this
 *   to a multiplatform module requires Compose runtime on every target's
 *   classpath (proven the hard way during Phase 0). The CMP plugin below pulls
 *   that in via the `compose.runtime` shorthand.
 * - `org.jetbrains.compose` — the JetBrains Compose Multiplatform Gradle plugin.
 *   Exposes `compose.runtime`, `compose.material3`, `compose.foundation`, etc.
 *   for use in commonMain `dependencies { implementation(compose.runtime) }`.
 *
 * Also flips `buildFeatures.compose = true` on the Android library extension so
 * Android-side previews / tooling pick up Compose.
 *
 * Compose runtime / Material3 / Coil / etc. coordinates are NOT auto-added
 * here — modules pick the surface area they want, the same way the legacy
 * `AndroidLibraryComposeConventionPlugin` worked.
 */
class KotlinMultiplatformLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
    }
}
