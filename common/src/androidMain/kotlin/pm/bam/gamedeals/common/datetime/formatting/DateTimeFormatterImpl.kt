package pm.bam.gamedeals.common.datetime.formatting

import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject


internal class DateTimeFormatterImpl @Inject constructor(
    private val datetimeParsing: DatetimeParsing
) : DateTimeFormatter {

    private val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    override fun formatToISODate(seconds: Long): String = formatter.format(datetimeParsing.parseLocalDateTime(seconds))

    override fun formatToISODateNullable(seconds: Long): String? = seconds.takeIf { it > 0 }?.let { formatter.format(datetimeParsing.parseLocalDateTime(seconds)) }

}