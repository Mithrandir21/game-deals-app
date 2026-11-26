package pm.bam.gamedeals.feature.account.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val day = 86_400_000L
    private val now = 1_000L * day // an arbitrary "now" well past the epoch

    private fun ago(days: Long) = relativeTimeSince(now - days * day, now)

    @Test fun same_instant_is_today() = assertEquals(RelativeTime(RelativeTimeBucket.TODAY, 0), ago(0))

    @Test fun one_day_is_singular_day() = assertEquals(RelativeTime(RelativeTimeBucket.DAY, 1), ago(1))

    @Test fun several_days() = assertEquals(RelativeTime(RelativeTimeBucket.DAYS, 3), ago(3))

    @Test fun one_week_is_singular() = assertEquals(RelativeTime(RelativeTimeBucket.WEEK, 1), ago(10))

    @Test fun multiple_weeks() = assertEquals(RelativeTime(RelativeTimeBucket.WEEKS, 2), ago(20))

    @Test fun months() = assertEquals(RelativeTime(RelativeTimeBucket.MONTHS, 2), ago(60))

    @Test fun one_year_is_singular() = assertEquals(RelativeTime(RelativeTimeBucket.YEAR, 1), ago(400))

    @Test fun multiple_years() = assertEquals(RelativeTime(RelativeTimeBucket.YEARS, 2), ago(800))

    @Test fun future_timestamp_clamps_to_today() =
        assertEquals(RelativeTime(RelativeTimeBucket.TODAY, 0), relativeTimeSince(now + 5 * day, now))
}
