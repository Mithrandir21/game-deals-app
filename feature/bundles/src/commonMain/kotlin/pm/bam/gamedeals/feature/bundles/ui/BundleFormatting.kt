package pm.bam.gamedeals.feature.bundles.ui

import pm.bam.gamedeals.common.ui.components.formatBundleShortDate

/** The bundle's expiry as a short local date (detail footer). Delegates to the shared `:common:ui` formatter. */
internal fun formatBundleExpiry(epochMs: Long): String = formatBundleShortDate(epochMs)

/** The bundle's publish date as a short local date (detail footer). */
internal fun formatBundlePublished(epochMs: Long): String = formatBundleShortDate(epochMs)

/**
 * Formats a remaining duration (ms) as the detail screen's live countdown, e.g. "11d 16h 32m 05s".
 * The day segment is dropped once under a day; seconds are zero-padded so the trailing digits don't jump.
 */
internal fun formatCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (days > 0) append(days).append("d ")
        append(hours).append("h ")
        append(minutes).append("m ")
        append(seconds.toString().padStart(2, '0')).append('s')
    }
}
