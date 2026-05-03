package pm.bam.gamedeals.common.datetime.parsing

import java.time.Instant
import java.time.LocalDateTime

interface DatetimeParsing {

    fun parseLocalDateTime(seconds: Long): Instant

    fun parseDatetime(value: String): LocalDateTime

    fun datetimeToString(localDateTime: LocalDateTime): String

}