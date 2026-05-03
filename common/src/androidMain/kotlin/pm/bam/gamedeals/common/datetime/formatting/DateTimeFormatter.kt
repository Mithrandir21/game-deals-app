package pm.bam.gamedeals.common.datetime.formatting

interface DateTimeFormatter {

    fun formatToISODate(seconds: Long): String

    fun formatToISODateNullable(seconds: Long): String?
}