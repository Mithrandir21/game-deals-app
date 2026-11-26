@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.common.datetime.parsing

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime

interface DatetimeParsing {

    fun parseLocalDateTime(seconds: Long): Instant

    fun parseDatetime(value: String): LocalDateTime

    fun datetimeToString(localDateTime: LocalDateTime): String

}
