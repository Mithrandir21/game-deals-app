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

/**
 * `/notifications/waitlist/v1` detail (epic #272, #288). Only [games] is consumed — the rest of the
 * `obj.notification.waitlist` payload (timestamp/read) is dropped via `ignoreUnknownKeys`.
 */
@Serializable
data class RemoteItadNotificationWaitlist(
    @SerialName("id") val id: String,
    @SerialName("games") val games: List<RemoteItadNotificationGame> = emptyList(),
)

/**
 * Minimal `obj.notification.game` subset (#288): the game id + title needed to deep-link. The rest of the
 * game payload (slug/type/mature/prices/deals) is dropped via `ignoreUnknownKeys`.
 */
@Serializable
data class RemoteItadNotificationGame(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
)
