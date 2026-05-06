package pm.bam.gamedeals

import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinMultiplatformKspConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.google.devtools.ksp")
    }
}
