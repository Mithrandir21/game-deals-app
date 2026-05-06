package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the KSP Gradle plugin to a KMP module.
 *
 * Modules declare per-target processor coordinates themselves
 * (`dependencies { add("kspAndroid", libs.room.compiler) }` etc.) so each
 * module controls which targets get which processors.
 */
class KotlinMultiplatformKspConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.google.devtools.ksp")
    }
}
