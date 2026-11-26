package pm.bam.gamedeals.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SaleCountdownTimeLeftTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    @Test
    fun two_days_or_more_count_whole_days_rounded_down() {
        assertEquals(TimeLeft.Days(2), timeLeft(0, 2 * day))
        assertEquals(TimeLeft.Days(5), timeLeft(0, 5 * day + 23 * hour))
    }

    @Test
    fun under_two_days_counts_hours() {
        assertEquals(TimeLeft.Hours(47), timeLeft(0, 2 * day - 1))
        assertEquals(TimeLeft.Hours(1), timeLeft(0, hour))
    }

    @Test
    fun the_last_hour_and_anything_past_say_under_an_hour() {
        assertEquals(TimeLeft.UnderAnHour, timeLeft(0, hour - 1))
        assertEquals(TimeLeft.UnderAnHour, timeLeft(10, 0))
    }
}
