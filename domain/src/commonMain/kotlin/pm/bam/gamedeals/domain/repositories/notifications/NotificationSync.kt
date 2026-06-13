package pm.bam.gamedeals.domain.repositories.notifications

import androidx.compose.runtime.Immutable
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.NotificationGame

/**
 * A notification that should be surfaced to the OS tray (background delivery — epic #272 follow-up). Carries
 * the [games] referenced by a waitlist notification so the platform layer can build a deep-link
 * ([NotificationPresenter]): a single game → that game's detail, several → the in-app Notifications screen.
 */
@Immutable
data class PendingNotificationAlert(
    val notificationId: String,
    val title: String,
    val games: List<NotificationGame>,
)

/**
 * The shared, platform-agnostic core of background notification delivery: works out which ITAD
 * notifications are *new* since the last run and returns them for the platform layer to post.
 *
 * "New" = unread **and** not already surfaced (tracked in [SurfacedNotificationStore]); see
 * [NotificationSyncImpl] for the dedup + prune logic. Login-gated (no-op when logged out). The Android
 * WorkManager worker and the iOS BGTask handler both drive this then hand the result to a
 * [NotificationPresenter].
 */
interface NotificationSync {
    suspend fun syncAndCollectNew(): List<PendingNotificationAlert>
}

internal class NotificationSyncImpl(
    private val repository: NotificationsRepository,
    private val authTokenStore: AuthTokenStore,
    private val surfacedStore: SurfacedNotificationStore,
) : NotificationSync {

    override suspend fun syncAndCollectNew(): List<PendingNotificationAlert> {
        if (authTokenStore.getAccessToken() == null) return emptyList() // login gate

        val all = repository.getNotifications()
        val surfaced = surfacedStore.get()
        val new = all.filter { !it.read && it.id !in surfaced }

        val alerts = new.map { notification ->
            val games = if (notification.type == WAITLIST_TYPE) {
                runCatching { repository.getWaitlistGames(notification.id) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            PendingNotificationAlert(notification.id, notification.title, games)
        }

        // Remember everything currently on the server as "surfaced", pruning ids the server no longer
        // returns. This bounds the set's growth and lets a removed-then-re-added notification alert again.
        surfacedStore.replace(all.map { it.id }.toSet())

        return alerts
    }

    private companion object {
        const val WAITLIST_TYPE = "waitlist"
    }
}
