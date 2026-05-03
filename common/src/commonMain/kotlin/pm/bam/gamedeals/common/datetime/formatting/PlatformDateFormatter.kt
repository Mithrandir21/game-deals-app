package pm.bam.gamedeals.common.datetime.formatting

import kotlinx.datetime.Instant

/**
 * Locale-aware date formatter producing strings of the form `"MMM dd, yyyy"` (e.g. "Jan 15, 2026")
 * with the system locale's localized month abbreviation. kotlinx-datetime's format builder cannot
 * yet produce locale-aware month names, so this is delegated to the platform's native formatter:
 * `java.time.format.DateTimeFormatter` on Android, `NSDateFormatter` on iOS.
 */
internal expect fun formatLocaleAwareDate(instant: Instant): String
