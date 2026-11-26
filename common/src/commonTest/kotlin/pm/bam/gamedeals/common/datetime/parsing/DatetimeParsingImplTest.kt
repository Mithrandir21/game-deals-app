@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.common.datetime.parsing

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month

class DatetimeParsingImplTest {

    private lateinit var datetimeParsing: DatetimeParsingImpl

    @BeforeTest
    fun setUp() {
        datetimeParsing = DatetimeParsingImpl()
    }

    @Test
    fun parseLocalDateTime_converts_seconds_to_Instant() {
        val seconds = 1767225600L // 2026-01-01 00:00:00 UTC
        val result = datetimeParsing.parseLocalDateTime(seconds)
        assertEquals(Instant.fromEpochSeconds(seconds), result)
    }

    @Test
    fun parseDatetime_parses_string_correctly() {
        val value = "2026-01-01 12:30:45"
        val result = datetimeParsing.parseDatetime(value)

        assertEquals(2026, result.year)
        assertEquals(Month.JANUARY, result.month)
        assertEquals(1, result.dayOfMonth)
        assertEquals(12, result.hour)
        assertEquals(30, result.minute)
        assertEquals(45, result.second)
    }

    @Test
    fun datetimeToString_formats_LocalDateTime_correctly() {
        val localDateTime = LocalDateTime(2026, 1, 1, 12, 30, 45)
        val result = datetimeParsing.datetimeToString(localDateTime)

        assertEquals("2026-01-01 12:30:45", result)
    }
}
