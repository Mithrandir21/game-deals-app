@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.common.datetime.formatting

import kotlin.time.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.systemTimeZone

internal actual fun formatLocaleAwareDate(instant: Instant): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "MMM dd, yyyy"
        locale = NSLocale.currentLocale
        timeZone = NSTimeZone.systemTimeZone
    }
    return formatter.stringFromDate(instant.toNSDate())
}
