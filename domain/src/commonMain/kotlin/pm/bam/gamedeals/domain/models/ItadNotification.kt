package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A user ITAD notification (epic #272, P2 — from `/notifications/v1`). [read] is false while unread.
 * [type] is currently always `"waitlist"`; kept as a string for forward-compatibility. [timestamp] is
 * the raw ISO date-time as returned by ITAD (formatted at the UI layer in #278).
 */
@Immutable
data class ItadNotification(
    val id: String,
    val type: String,
    val title: String,
    val timestamp: String,
    val read: Boolean,
)
