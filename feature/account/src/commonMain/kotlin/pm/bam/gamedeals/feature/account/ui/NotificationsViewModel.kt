package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the Notifications **list** sub-screen (epic #272, P2.2 #278): observes the reactive notifications
 * list (so mark-read edits reflect immediately) and triggers the remote-as-truth load on open.
 * [onMarkAllRead] goes through the repository (remote-first); the observed list then updates.
 *
 * Each row is a daily digest; tapping it opens the [NotificationDetailScreen] (the deals inside that
 * notification), which is where the notification is marked read — so this VM no longer fetches per-game
 * detail or marks read on tap (the row click is a pure navigation callback in the screen).
 */
internal class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<NotificationsScreenData>
        field = MutableStateFlow(NotificationsScreenData(loading = true))

    init {
        viewModelScope.launch {
            notificationsRepository.observeNotifications().collect { list ->
                uiState.update { it.copy(notifications = list.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            runCatching { notificationsRepository.getNotifications() }.onFailure { fatal(logger, it) }
            uiState.update { it.copy(loading = false) }
        }
    }

    fun onMarkAllRead() {
        viewModelScope.launch {
            runCatching { notificationsRepository.markAllRead() }.onFailure { fatal(logger, it) }
        }
    }

    @Immutable
    data class NotificationsScreenData(
        val loading: Boolean = false,
        val notifications: ImmutableList<ItadNotification> = persistentListOf(),
    ) {
        val hasUnread: Boolean get() = notifications.any { !it.read }
    }
}
