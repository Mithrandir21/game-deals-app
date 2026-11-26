@file:OptIn(ExperimentalTime::class)

package pm.bam.gamedeals.feature.game.ui

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pm.bam.gamedeals.domain.models.PriceHistory.PricePoint

/**
 * Pure (Compose-free) data helpers for [PriceHistoryChart], split out so they can be unit-tested on
 * the JVM without a Compose/Vico runtime (the project avoids Robolectric).
 */

internal const val MILLIS_PER_DAY: Long = 86_400_000L

/**
 * The user-selectable time windows for the chart. [days] `null` means the full tracked history.
 * [bucketDays] is the downsampling granularity for long ranges (see [downsamplePrevailingPerBucket]):
 * daily for 3M (no coarsening), ~weekly for 1Y, ~monthly for All.
 */
internal enum class PriceHistoryRange(val days: Int?, val bucketDays: Int) {
    THREE_MONTHS(90, 1),
    ONE_YEAR(365, 7),
    ALL(null, 30),
}

/** UTC day index for an epoch-millisecond timestamp. Used as the chart's (integer) x-value. */
internal fun Long.toEpochDay(): Long = this.floorDiv(MILLIS_PER_DAY)

/** Epoch milliseconds at the start of a UTC day index — the inverse used by axis/tooltip formatting. */
internal fun Long.epochDayToMillis(): Long = this * MILLIS_PER_DAY

/**
 * Prepares [points] for plotting within [range], relative to [nowEpochMs].
 *
 * 1. Collapses to one entry per UTC day (latest price that day wins), since the chart's x-axis is
 *    day-resolution and ITAD can emit several changes in a day.
 * 2. For a bounded [range], carries the prevailing price forward to the window's start as an anchor
 *    (re-timestamped to the cutoff day) so a window that opens mid-flat-period still renders the
 *    price that was in effect, instead of going blank.
 * 3. Downsamples long ranges into [PriceHistoryRange.bucketDays]-day buckets ([downsamplePrevailingPerBucket])
 *    so a multi-year history isn't a dense, unreadable band; 3M stays daily.
 * 4. Extends a flat segment to "now" (a synthetic point at [nowEpochMs]'s day carrying the latest
 *    price) so the line reflects that the current price still holds today.
 *
 * Returns chronological (oldest → newest) points with strictly increasing day x-values.
 */
internal fun windowedPriceHistory(
    points: List<PricePoint>,
    range: PriceHistoryRange,
    nowEpochMs: Long,
): List<PricePoint> {
    if (points.isEmpty()) return emptyList()

    // One point per day. Input is oldest → newest, so later same-day points overwrite earlier ones
    // while the day's first-seen insertion position (its chronological slot) is preserved.
    val perDay = LinkedHashMap<Long, PricePoint>()
    points.forEach { perDay[it.timestampEpochMs.toEpochDay()] = it }
    val dayPoints = perDay.values.toList()

    val nowDay = nowEpochMs.toEpochDay()
    val cutoffDay = range.days?.let { nowDay - it }

    val result = mutableListOf<PricePoint>()
    if (cutoffDay != null) {
        dayPoints.lastOrNull { it.timestampEpochMs.toEpochDay() <= cutoffDay }
            ?.let { anchor -> result += anchor.copy(timestampEpochMs = cutoffDay.epochDayToMillis()) }
        result += dayPoints.filter { it.timestampEpochMs.toEpochDay() > cutoffDay }
    } else {
        result += dayPoints
    }

    if (result.isEmpty()) return emptyList()

    val downsampled = downsamplePrevailingPerBucket(result, range.bucketDays).toMutableList()

    val last = downsampled.last()
    if (last.timestampEpochMs.toEpochDay() < nowDay) {
        downsampled += last.copy(timestampEpochMs = nowDay.epochDayToMillis())
    }
    return downsampled
}

/**
 * Collapses [points] to one entry per [bucketDays]-day bucket, keeping the price **prevailing at the
 * bucket's end** (its latest point) — the same way a price-over-time line samples a step series, as
 * ITAD's own history chart does. Deliberately *not* the bucket's lowest: that would make the past
 * look systematically cheaper than it actually was. The window's first and last points and the
 * global all-time-low are always kept, so the line spans the full window and the all-time-low /
 * current highlight markers stay exact (the historic low still shows as a dip + dot). A no-op for
 * daily ([bucketDays] <= 1) or trivially short series, so 3M and sparse histories are untouched.
 */
private fun downsamplePrevailingPerBucket(points: List<PricePoint>, bucketDays: Int): List<PricePoint> {
    if (bucketDays <= 1 || points.size <= 2) return points

    val firstDay = points.first().timestampEpochMs.toEpochDay()
    val byBucket = LinkedHashMap<Long, PricePoint>()
    points.forEach { point ->
        val bucket = (point.timestampEpochMs.toEpochDay() - firstDay) / bucketDays
        byBucket[bucket] = point // chronological input → last write wins = the bucket-end price
    }

    val keep = LinkedHashSet(byBucket.values)
    keep += points.first() // preserve the window-start anchor so the line has no left gap
    lowestPoint(points)?.let { keep += it } // the all-time-low still shows (its highlight dot)
    keep += points.last() // keep the true latest point so "now" / the current marker is accurate
    return keep.sortedBy { it.timestampEpochMs }
}

/** The latest known regular (non-sale) price across [points], for the MSRP reference line; `null` if none. */
internal fun latestRegular(points: List<PricePoint>): Double? =
    points.lastOrNull { it.regularValue != null }?.regularValue

/** The lowest-priced point in [points] (first on ties), or `null` when empty. */
internal fun lowestPoint(points: List<PricePoint>): PricePoint? = points.minByOrNull { it.priceValue }

private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "Mon YYYY" — used for axis ticks and the tooltip's secondary line. */
internal fun formatMonthYear(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date
    return "${MONTH_ABBREV[date.month.ordinal]} ${date.year}"
}

/** "D Mon YYYY" — used for the scrub tooltip's primary line. */
internal fun formatFullDate(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date
    return "${date.dayOfMonth} ${MONTH_ABBREV[date.month.ordinal]} ${date.year}"
}
