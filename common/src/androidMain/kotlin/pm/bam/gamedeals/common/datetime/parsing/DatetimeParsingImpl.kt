package pm.bam.gamedeals.common.datetime.parsing

import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal class DatetimeParsingImpl @Inject constructor() : DatetimeParsing {

    private var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun parseLocalDateTime(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

    override fun parseDatetime(value: String): LocalDateTime = LocalDateTime.parse(value, formatter)

    override fun datetimeToString(localDateTime: LocalDateTime): String = localDateTime.format(formatter)

}