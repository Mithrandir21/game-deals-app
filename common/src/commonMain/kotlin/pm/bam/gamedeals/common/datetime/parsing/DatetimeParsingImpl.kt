@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.common.datetime.parsing

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char

internal class DatetimeParsingImpl : DatetimeParsing {

    private val format = LocalDateTime.Format {
        year(); char('-'); monthNumber(); char('-'); dayOfMonth()
        char(' ')
        hour(); char(':'); minute(); char(':'); second()
    }

    override fun parseLocalDateTime(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

    override fun parseDatetime(value: String): LocalDateTime = LocalDateTime.parse(value, format)

    override fun datetimeToString(localDateTime: LocalDateTime): String = localDateTime.format(format)

}
