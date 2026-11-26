package pm.bam.gamedeals

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Opt-in Compose compiler stability reports and metrics.
 *
 * Run any assemble or compile task with `-Pgamedeals.composeReports=true` to
 * emit per-module Compose stability reports under `build/compose-reports/`
 * (classes.txt and composables.txt) and metrics under `build/compose-metrics/`
 * (module.json). The classes report confirms whether each data class is
 * inferred `stable`; the composables report confirms whether each composable is
 * `skippable`. Off by default to keep day-to-day compile times unaffected.
 */
internal fun Project.configureComposeCompilerReports() {
    if (findProperty("gamedeals.composeReports") != "true") return
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
    }
}
