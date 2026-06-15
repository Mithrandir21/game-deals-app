@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.feature.giveaways.ui

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pm.bam.gamedeals.testing.fixtures.giveaway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiveawayExpiryTest {

    @Test
    fun parses_a_valid_utc_end_date_to_epoch_millis() {
        val expected = LocalDateTime(2026, 7, 1, 18, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
        assertEquals(expected, parseGiveawayEndDateMillis("2026-07-01 18:00:00", testDatetimeParsing))
    }

    @Test
    fun returns_null_for_na_blank_or_unparseable_end_dates() {
        assertNull(parseGiveawayEndDateMillis(null, testDatetimeParsing))
        assertNull(parseGiveawayEndDateMillis("N/A", testDatetimeParsing))
        assertNull(parseGiveawayEndDateMillis("", testDatetimeParsing))
        assertNull(parseGiveawayEndDateMillis("   ", testDatetimeParsing))
        assertNull(parseGiveawayEndDateMillis("not-a-date", testDatetimeParsing))
    }

    @Test
    fun live_when_active_and_no_expiry() {
        assertTrue(isLive(giveaway(status = "Active"), endDateMillis = null, nowMillis = 1_000L))
    }

    @Test
    fun live_when_active_and_expiry_in_future() {
        assertTrue(isLive(giveaway(status = "Active"), endDateMillis = 2_000L, nowMillis = 1_000L))
    }

    @Test
    fun expired_when_active_but_end_date_has_passed() {
        assertFalse(isLive(giveaway(status = "Active"), endDateMillis = 500L, nowMillis = 1_000L))
    }

    @Test
    fun expired_when_status_is_not_active() {
        assertFalse(isLive(giveaway(status = "Expired"), endDateMillis = null, nowMillis = 1_000L))
    }
}
