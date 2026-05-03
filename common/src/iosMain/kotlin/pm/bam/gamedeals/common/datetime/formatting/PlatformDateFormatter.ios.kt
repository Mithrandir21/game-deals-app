package pm.bam.gamedeals.common.datetime.formatting

import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.systemTimeZone

private val iosFormatter = NSDateFormatter().apply {
    dateFormat = "MMM dd, yyyy"
    locale = NSLocale.currentLocale
    timeZone = NSTimeZone.systemTimeZone
}

internal actual fun formatLocaleAwareDate(instant: Instant): String =
    iosFormatter.stringFromDate(instant.toNSDate())
