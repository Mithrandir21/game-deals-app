package pm.bam.gamedeals.feature.bundles.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Formats a bundle expiry epoch-ms as a short local date, e.g. "Jul 7, 2026" (epic #205 Phase 3c). */
internal fun formatBundleExpiry(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${MONTH_ABBREV[date.month.ordinal]} ${date.dayOfMonth}, ${date.year}"
}

private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
