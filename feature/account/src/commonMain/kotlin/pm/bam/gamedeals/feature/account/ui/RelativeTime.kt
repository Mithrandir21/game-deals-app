@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_added_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_day_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_days_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_month_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_months_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_today
import pm.bam.gamedeals.feature.account.generated.resources.account_time_week_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_weeks_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_year_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_time_years_ago
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlisted_ago

internal enum class RelativeTimeBucket { TODAY, DAY, DAYS, WEEK, WEEKS, MONTH, MONTHS, YEAR, YEARS }

internal data class RelativeTime(val bucket: RelativeTimeBucket, val count: Int)

internal enum class AgoPrefix { WAITLISTED, ADDED }

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Pure, testable "how long ago" bucketing. Clamps negative diffs (clock skew) to today. Months/years are
 * approximate (30/365 days) — good enough for an "ago" label. Days are always ≥ 2 (a single day reads as
 * [RelativeTimeBucket.DAY]); weeks/months/years collapse to their singular bucket at a count of 1.
 */
internal fun relativeTimeSince(epochMs: Long, nowMs: Long): RelativeTime {
    val days = ((nowMs - epochMs).coerceAtLeast(0L)) / MILLIS_PER_DAY
    return when {
        days < 1 -> RelativeTime(RelativeTimeBucket.TODAY, 0)
        days < 2 -> RelativeTime(RelativeTimeBucket.DAY, 1)
        days < 7 -> RelativeTime(RelativeTimeBucket.DAYS, days.toInt())
        days < 30 -> (days / 7).toInt().let { w -> RelativeTime(if (w == 1) RelativeTimeBucket.WEEK else RelativeTimeBucket.WEEKS, w) }
        days < 365 -> (days / 30).toInt().coerceAtLeast(1).let { m -> RelativeTime(if (m == 1) RelativeTimeBucket.MONTH else RelativeTimeBucket.MONTHS, m) }
        else -> (days / 365).toInt().coerceAtLeast(1).let { y -> RelativeTime(if (y == 1) RelativeTimeBucket.YEAR else RelativeTimeBucket.YEARS, y) }
    }
}

/** "Waitlisted 3 months ago" / "Added 2 years ago" — null when [epochMs] is absent (omit the line). */
@Composable
internal fun addedAgoLabel(epochMs: Long?, prefix: AgoPrefix): String? {
    if (epochMs == null) return null
    val now = remember(epochMs) { Clock.System.now().toEpochMilliseconds() }
    val rt = relativeTimeSince(epochMs, now)
    val duration = when (rt.bucket) {
        RelativeTimeBucket.TODAY -> stringResource(Res.string.account_time_today)
        RelativeTimeBucket.DAY -> stringResource(Res.string.account_time_day_ago)
        RelativeTimeBucket.DAYS -> stringResource(Res.string.account_time_days_ago, rt.count)
        RelativeTimeBucket.WEEK -> stringResource(Res.string.account_time_week_ago)
        RelativeTimeBucket.WEEKS -> stringResource(Res.string.account_time_weeks_ago, rt.count)
        RelativeTimeBucket.MONTH -> stringResource(Res.string.account_time_month_ago)
        RelativeTimeBucket.MONTHS -> stringResource(Res.string.account_time_months_ago, rt.count)
        RelativeTimeBucket.YEAR -> stringResource(Res.string.account_time_year_ago)
        RelativeTimeBucket.YEARS -> stringResource(Res.string.account_time_years_ago, rt.count)
    }
    return when (prefix) {
        AgoPrefix.WAITLISTED -> stringResource(Res.string.account_waitlisted_ago, duration)
        AgoPrefix.ADDED -> stringResource(Res.string.account_added_ago, duration)
    }
}
