package pm.bam.gamedeals.domain.repositories.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.source.ItadAccountSource

/**
 * The user's ITAD notifications (epic #272, P2 #277). Unlike the waitlist/collection there is **no Room
 * cache** (the data is cheap to refetch and transient): the list is held in memory, (re)loaded on demand
 * via [getNotifications]. [observeUnreadCount] derives the unread tally from that list and is auth-gated
 * — it emits `0` whenever logged out, mirroring
 * [WaitlistRepository.observeWaitlistIds][pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository.observeWaitlistIds].
 * Writes are **remote-first** (confirm the ITAD mark-read before flipping the in-memory state).
 */
interface NotificationsRepository {
    /** The loaded notifications, auth-gated (empty when logged out). Reactive — reflects mark-read edits. */
    fun observeNotifications(): Flow<List<ItadNotification>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun getNotifications(): List<ItadNotification>
    suspend fun markRead(id: String)
    suspend fun markAllRead()

    /** The games a waitlist notification refers to, for deep-linking (#288). Empty when logged out. */
    suspend fun getWaitlistGames(notificationId: String): List<NotificationGame>
}

internal class NotificationsRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
) : NotificationsRepository {

    private val notifications = MutableStateFlow<List<ItadNotification>>(emptyList())

    override fun observeNotifications(): Flow<List<ItadNotification>> =
        combine(authTokenStore.observeAuthState(), notifications) { authState, list ->
            if (authState is AuthState.LoggedIn) list else emptyList()
        }

    override fun observeUnreadCount(): Flow<Int> =
        observeNotifications().map { list -> list.count { !it.read } }

    override suspend fun getNotifications(): List<ItadNotification> {
        if (!loggedIn()) {
            notifications.value = emptyList()
            return emptyList()
        }
        val list = accountSource.getNotifications()
        notifications.value = list
        return list
    }

    override suspend fun markRead(id: String) {
        if (!loggedIn()) return
        // Remote-first: confirm the ITAD write before updating the in-memory state.
        accountSource.markNotificationRead(id)
        notifications.update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
    }

    override suspend fun markAllRead() {
        if (!loggedIn()) return
        accountSource.markAllNotificationsRead()
        notifications.update { list -> list.map { it.copy(read = true) } }
    }

    override suspend fun getWaitlistGames(notificationId: String): List<NotificationGame> {
        if (!loggedIn()) return emptyList()
        return accountSource.getWaitlistNotificationGames(notificationId)
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
