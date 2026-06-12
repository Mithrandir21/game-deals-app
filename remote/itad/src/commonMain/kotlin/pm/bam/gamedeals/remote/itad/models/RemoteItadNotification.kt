package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `/notifications/v1` item (epic #272, P2.1 #277). [read] is an ISO date-time string once the
 * notification has been read, and `null` while it is still unread.
 */
@Serializable
data class RemoteItadNotification(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("title") val title: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("read") val read: String? = null,
)
