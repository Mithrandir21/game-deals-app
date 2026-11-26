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
 * `obj.notification.game` (#288, extended): the game id + title (for deep-linking) plus the deal content
 * surfaced by the in-app notification detail screen — the all-time-[historyLow] and the per-shop [deals].
 * `obj.notification.deal` is a superset of [RemoteItadDealEntry] (it additionally carries drm/platforms/
 * timestamp/expiry), so the extra fields drop via `ignoreUnknownKeys`. `slug/type/mature/lastPrice` are
 * not consumed and likewise dropped.
 */
@Serializable
data class RemoteItadNotificationGame(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("historyLow") val historyLow: RemoteItadPrice? = null,
    @SerialName("deals") val deals: List<RemoteItadDealEntry> = emptyList(),
)
