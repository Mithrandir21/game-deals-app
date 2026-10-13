package pm.bam.gamedeals.domain.repositories.notifications

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository

/**
 * A notification that should be surfaced to the OS tray (background delivery — epic #272 follow-up). An
 * ITAD waitlist alert carries the [games] (with their best deals) it references so the platform layer can
 * build rich tray text and deep-link to that notification's in-app detail screen. A followed-franchise deal
 * alert (client-side [FollowedFranchiseChecker][pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker])
 * instead sets [gameId] and self-describes in [title], so the tap deep-links straight to that game's page
 * ([NotificationPresenter]).
 */
@Immutable
data class PendingNotificationAlert(
    val notificationId: String,
    val title: String,
    val games: List<NotificationDealGame>,
    val gameId: String? = null,
)

/**
 * The shared, platform-agnostic core of background notification delivery: works out which ITAD
 * notifications are *new* since the last run and returns them for the platform layer to post.
 *
 * "New" = unread **and** not already surfaced (tracked in [SurfacedNotificationStore]); see
 * [NotificationSyncImpl] for the dedup + prune logic. Games the user no longer follows are left out of waitlist alerts. Login-gated (no-op when logged out). The Android
 * WorkManager worker and the iOS BGTask handler both drive this then hand the result to a
 * [NotificationPresenter].
 */
interface NotificationSync {
    suspend fun syncAndCollectNew(): List<PendingNotificationAlert>
}

internal class NotificationSyncImpl(
    private val repository: NotificationsRepository,
    private val waitlistRepository: WaitlistRepository,
    private val authTokenStore: AuthTokenStore,
    private val surfacedStore: SurfacedNotificationStore,
) : NotificationSync {

    override suspend fun syncAndCollectNew(): List<PendingNotificationAlert> {
        if (authTokenStore.getAccessToken() == null) return emptyList() // login gate

        val all = repository.getNotifications(force = true) // the background poll is what keeps the kept list current
        val surfaced = surfacedStore.get()
        val new = all.filter { !it.read && it.id !in surfaced }
        val followed = if (new.any { it.type == WAITLIST_TYPE }) followedGameIds() else emptySet()

        val alerts = coroutineScope {
            new.map { notification ->
                async {
                    if (notification.type != WAITLIST_TYPE) return@async PendingNotificationAlert(notification.id, notification.title, emptyList())
                    // Fetch the full deal content for rich tray text; this also warms the repository's
                    // detail cache so opening the in-app detail screen from the tap is instant.
                    val games = runCatching { repository.getNotificationDetail(notification.id).games }.getOrDefault(emptyList())
                    val followedGames = games.filter { it.gameId in followed }
                    // ITAD can still notify about a game unfollowed since; an alert left with no followed games is dropped.
                    if (games.isNotEmpty() && followedGames.isEmpty()) null
                    else PendingNotificationAlert(notification.id, notification.title, followedGames)
                }
            }.awaitAll().filterNotNull()
        }

        // Remember everything currently on the server as "surfaced", pruning ids the server no longer
        // returns. This bounds the set's growth and lets a removed-then-re-added notification alert again.
        surfacedStore.replace(all.map { it.id }.toSet())

        return alerts
    }

    /** The remote waitlist (also reconciling the local id set), or the last-known local set when the fetch fails. */
    private suspend fun followedGameIds(): Set<String> =
        runCatching { waitlistRepository.getWaitlist().mapTo(mutableSetOf()) { it.gameId } }
            .getOrElse { waitlistRepository.observeWaitlistIds().first() }

    private companion object {
        const val WAITLIST_TYPE = "waitlist"
    }
}
