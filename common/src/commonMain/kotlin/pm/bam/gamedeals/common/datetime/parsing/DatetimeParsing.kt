package pm.bam.gamedeals.common.datetime.parsing

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime

interface DatetimeParsing {

    fun parseLocalDateTime(seconds: Long): Instant

    fun parseDatetime(value: String): LocalDateTime

    fun datetimeToString(localDateTime: LocalDateTime): String

}
