@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.common.datetime.formatting

import kotlin.time.Instant
import kotlin.time.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual fun formatLocaleAwareDate(instant: Instant): String {
    val formatter = DateTimeFormatter
        .ofPattern("MMM dd, yyyy")
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant.toJavaInstant())
}
