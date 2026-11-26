package pm.bam.gamedeals.logging.featureflags

import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The Sentry context that lists every switch with "on" or "off". */
internal const val SWITCHES_CONTEXT = "Feature switches"

/** The Sentry tag listing the switches that are off ("none" when all are on), so issues can be searched by it. */
internal const val SWITCHES_OFF_TAG = "switches_off"

/** What a crash report says about the switches. */
internal data class SwitchReport(val context: Map<String, String>, val off: String)

internal fun switchReport(values: Map<FeatureFlag, Boolean>): SwitchReport {
    val flags = FeatureFlag.entries.map { it to (values[it] ?: it.default) }
    return SwitchReport(
        context = flags.associate { (flag, on) -> flag.key to if (on) "on" else "off" },
        off = flags.filterNot { it.second }.joinToString(",") { it.first.key }.ifEmpty { "none" },
    )
}

/**
 * Keeps every Sentry report stamped with the current switch values: a "Feature switches" context listing each one,
 * and a `switches_off` tag naming those that are off. Collects [flags] for as long as the caller's coroutine runs, so
 * a switch flipped mid-session shows on later reports. Returns at once when Sentry is off (debug builds, no DSN).
 * The values are the app's own settings; nothing about the user.
 */
suspend fun reportSwitchesToSentry(flags: FeatureFlags) {
    if (!Sentry.isEnabled()) return
    combine(FeatureFlag.entries.map { flag -> flags.observe(flag).map { flag to it } }) { it.toMap() }
        .distinctUntilChanged()
        .collect { values ->
            val report = switchReport(values)
            Sentry.configureScope { scope ->
                scope.setContext(SWITCHES_CONTEXT, report.context)
                scope.setTag(SWITCHES_OFF_TAG, report.off)
            }
        }
}
