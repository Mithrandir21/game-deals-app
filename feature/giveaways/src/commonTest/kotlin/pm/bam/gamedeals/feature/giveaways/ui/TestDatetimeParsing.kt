@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.feature.giveaways.ui

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing

/**
 * A real [DatetimeParsing] test double that mirrors `DatetimeParsingImpl`'s strict
 * "yyyy-MM-dd HH:mm:ss" format, so giveaway end-date parsing is exercised deterministically without
 * depending on the (internal) production implementation.
 */
internal val testDatetimeParsing: DatetimeParsing = object : DatetimeParsing {
    private val format = LocalDateTime.Format {
        year(); char('-'); monthNumber(); char('-'); dayOfMonth()
        char(' ')
        hour(); char(':'); minute(); char(':'); second()
    }

    override fun parseLocalDateTime(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)
    override fun parseDatetime(value: String): LocalDateTime = LocalDateTime.parse(value, format)
    override fun datetimeToString(localDateTime: LocalDateTime): String = localDateTime.format(format)
}
