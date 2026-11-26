package pm.bam.gamedeals

import com.skydoves.compose.stability.gradle.StabilityCheckTask
import com.skydoves.compose.stability.gradle.StabilityDumpTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Workaround for compose-stability-analyzer 0.13.0 (the latest published version at time of writing).
 *
 * `StabilityCheckTask` / `StabilityDumpTask` read `<module>/build/stability`, a directory the Kotlin compile tasks also write, without declaring the
 * dependency. Under Gradle 9.7 that is a hard validation failure ("Property has implicit dependency"), so `./gradlew build` — the CI gate in
 * `.github/workflows/android.yml` — dies during task-graph validation before the stability check ever runs.
 *
 * It only fires when a compile task and a stability task land in the *same* task graph, which is why `:app:debugStabilityCheck` and a single module's
 * `stabilityCheck` both pass in isolation while `build` fails. That makes it easy to miss in targeted local runs.
 *
 * Ordering the stability tasks after every Kotlin compilation is solution 3 from Gradle's own error message. `mustRunAfter` rather than `dependsOn` is
 * deliberate: it constrains ordering only when both tasks are already scheduled, so it adds no work to the graph — in particular it does not drag the iOS
 * compilations into an Android-only `check` run (which would break `build` on Linux CI).
 *
 * Remove once upstream declares the dependency properly. Analyzer 0.7.5 is not a fallback — it fails with an internal compiler error under Kotlin 2.4.x.
 */
internal fun Project.orderStabilityTasksAfterKotlinCompilation() {
    val kotlinCompilations = tasks.withType<KotlinCompilationTask<*>>()
    tasks.withType<StabilityCheckTask>().configureEach { mustRunAfter(kotlinCompilations) }
    tasks.withType<StabilityDumpTask>().configureEach { mustRunAfter(kotlinCompilations) }
}
