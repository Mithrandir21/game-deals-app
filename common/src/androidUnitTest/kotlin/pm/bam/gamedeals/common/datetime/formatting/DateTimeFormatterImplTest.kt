package pm.bam.gamedeals.common.datetime.formatting

import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import java.util.Locale
import java.util.TimeZone

class DateTimeFormatterImplTest {

    private val datetimeParsing: DatetimeParsing = mockk()
    private lateinit var dateTimeFormatter: DateTimeFormatterImpl

    @Before
    fun setUp() {
        // Set fixed locale and timezone for predictable tests — the Android actual of
        // `formatLocaleAwareDate` reads `Locale.getDefault()` and `ZoneId.systemDefault()`.
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        dateTimeFormatter = DateTimeFormatterImpl(datetimeParsing)
    }

    @Test
    fun `formatToISODate formats valid seconds correctly`() {
        val seconds = 1767225600L // 2026-01-01 00:00:00 UTC
        every { datetimeParsing.parseLocalDateTime(seconds) } returns Instant.fromEpochSeconds(seconds)

        val result = dateTimeFormatter.formatToISODate(seconds)

        assertEquals("Jan 01, 2026", result)
    }

    @Test
    fun `formatToISODateNullable returns null for zero or negative seconds`() {
        assertNull(dateTimeFormatter.formatToISODateNullable(0L))
        assertNull(dateTimeFormatter.formatToISODateNullable(-1L))
    }

    @Test
    fun `formatToISODateNullable returns formatted date for positive seconds`() {
        val seconds = 1767225600L
        every { datetimeParsing.parseLocalDateTime(seconds) } returns Instant.fromEpochSeconds(seconds)

        val result = dateTimeFormatter.formatToISODateNullable(seconds)

        assertEquals("Jan 01, 2026", result)
    }
}
