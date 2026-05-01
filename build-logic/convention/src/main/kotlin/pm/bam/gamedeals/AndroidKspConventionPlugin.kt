package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies KSP for modules that use annotation-processor-style code generation
 * (Dagger Hilt's compiler, androidx Hilt compiler, Room compiler, etc.).
 *
 * Deliberately does NOT apply the Hilt Gradle plugin
 * (`com.google.dagger.hilt.android`). In the pre-refactor codebase that Gradle
 * plugin was only applied to `:base` and `:app` — every other module used
 * Hilt purely through the KSP processor. To keep this PR structural we
 * preserve that boundary: modules that need the Hilt Gradle plugin continue
 * to apply it directly in their `build.gradle.kts`.
 *
 * Per-module dependency declarations (`hilt-android`, `hilt-compiler`,
 * `hilt-navigation-compose`, `hilt-androidx-compiler`, `room-compiler`, …)
 * stay in each module's `build.gradle.kts` because the set varies (e.g.
 * `:logging` doesn't use `hilt-navigation-compose`).
 */
class AndroidKspConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.google.devtools.ksp")
    }
}
