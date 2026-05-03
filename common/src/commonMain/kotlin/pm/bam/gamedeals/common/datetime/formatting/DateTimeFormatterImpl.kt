package pm.bam.gamedeals.common.datetime.formatting

import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing

internal class DateTimeFormatterImpl(
    private val datetimeParsing: DatetimeParsing
) : DateTimeFormatter {

    override fun formatToISODate(seconds: Long): String =
        formatLocaleAwareDate(datetimeParsing.parseLocalDateTime(seconds))

    override fun formatToISODateNullable(seconds: Long): String? =
        seconds.takeIf { it > 0 }?.let { formatLocaleAwareDate(datetimeParsing.parseLocalDateTime(it)) }

}
