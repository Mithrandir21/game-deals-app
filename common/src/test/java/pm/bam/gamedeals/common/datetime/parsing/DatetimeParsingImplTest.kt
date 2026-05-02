package pm.bam.gamedeals.common.datetime.parsing

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.Month

class DatetimeParsingImplTest {

    private lateinit var datetimeParsing: DatetimeParsingImpl

    @Before
    fun setUp() {
        datetimeParsing = DatetimeParsingImpl()
    }

    @Test
    fun `parseLocalDateTime converts seconds to Instant`() {
        val seconds = 1767225600L // 2026-01-01 00:00:00 UTC
        val result = datetimeParsing.parseLocalDateTime(seconds)
        assertEquals(Instant.ofEpochSecond(seconds), result)
    }

    @Test
    fun `parseDatetime parses string correctly`() {
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
    fun `datetimeToString formats LocalDateTime correctly`() {
        val localDateTime = LocalDateTime.of(2026, Month.JANUARY, 1, 12, 30, 45)
        val result = datetimeParsing.datetimeToString(localDateTime)
        
        assertEquals("2026-01-01 12:30:45", result)
    }
}
