package pm.bam.gamedeals.common.datetime.formatting

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.util.Locale

private val androidFormatter = java.time.format.DateTimeFormatter
    .ofPattern("MMM dd, yyyy")
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

internal actual fun formatLocaleAwareDate(instant: Instant): String =
    androidFormatter.format(instant.toJavaInstant())
