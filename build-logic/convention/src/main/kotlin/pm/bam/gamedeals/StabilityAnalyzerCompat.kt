package pm.bam.gamedeals

import org.gradle.api.Project

/**
 * Workaround for compose-stability-analyzer 0.7.1: its `<variant>StabilityCheck` and
 * `<variant>StabilityDump` tasks read the output of `compile<Variant>UnitTestKotlin[Android]`
 * (and the AndroidTest equivalents) without declaring the dependency.
 *
 * Gradle 9.x's task validator rejects this implicit-input pattern with
 * `BUILD FAILED` whenever the stability task runs in the same graph as a test-compile —
 * which happens any time `./gradlew build` is invoked, because the plugin wires its
 * `*StabilityCheck` tasks into the `check` lifecycle.
 *
 * We wire the missing `dependsOn` so the implicit edge becomes explicit. The stability
 * tasks don't actually need the test classes for analysis, but Gradle wants the
 * declaration. Remove this helper once skydoves/compose-stability-analyzer fixes the
 * upstream input declaration (track via the project's tech-debt issue referencing
 * the plugin upgrade).
 */
internal fun Project.wireStabilityAnalyzerTaskDependencies() {
    tasks.matching { task ->
        val n = task.name
        n.endsWith("StabilityCheck") || n.endsWith("StabilityDump")
    }.configureEach {
        dependsOn(
            tasks.matching { dep ->
                val dn = dep.name
                dn.startsWith("compile") && (dn.contains("UnitTest") || dn.contains("AndroidTest"))
            }
        )
    }
}
