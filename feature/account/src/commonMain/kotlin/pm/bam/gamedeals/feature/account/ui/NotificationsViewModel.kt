package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the Notifications sub-screen (epic #272, P2.2 #278): observes the reactive notifications list
 * (so mark-read edits reflect immediately) and triggers the remote-as-truth load on open. [onMarkAllRead]
 * goes through the repository (remote-first); the observed list then updates.
 *
 * Tapping a notification ([onNotificationClick], #288) marks it read and deep-links to the game(s) it
 * references via the waitlist-detail endpoint: a single game emits [NotificationsUiEvent.OpenGame]; several
 * games raise an in-screen chooser ([NotificationsScreenData.chooser]); none just marks it read.
 */
internal class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository,
    private val logger: Logger,
) : ViewModel() {

    // NOTE (per-game collapsing — deferred): notifications cannot be grouped into one row per game.
    // An ITAD notification is a per-day digest spanning ALL of the user's waitlisted games, not a
    // per-game event: the /notifications/v1 payload carries no game id and only a generic title
    // (e.g. "Price drop"). The referenced games surface only via the separate /notifications/waitlist/v1
    // detail call, and one notification maps to many games — so "same game" has no key in the list and
    // collapsing-by-game isn't expressible against the current API. Revisit if ITAD adds per-game items.

    val uiState: StateFlow<NotificationsScreenData>
        field = MutableStateFlow(NotificationsScreenData(loading = true))

    val events: SharedFlow<NotificationsUiEvent>
        field = MutableSharedFlow<NotificationsUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

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

    fun onNotificationClick(notification: ItadNotification) {
        onMarkRead(notification.id)
        // Only waitlist notifications carry a game detail; other types just get marked read.
        if (notification.type != WAITLIST_TYPE) return
        viewModelScope.launch {
            val games = runCatching { notificationsRepository.getWaitlistGames(notification.id) }
                .getOrElse { fatal(logger, it); emptyList() }
            when (games.size) {
                0 -> Unit // nothing to navigate to
                1 -> events.tryEmit(NotificationsUiEvent.OpenGame(games.first().gameId))
                else -> uiState.update { it.copy(chooser = games.toImmutableList()) }
            }
        }
    }

    fun onChooserGameClick(gameId: String) {
        uiState.update { it.copy(chooser = persistentListOf()) }
        events.tryEmit(NotificationsUiEvent.OpenGame(gameId))
    }

    fun onChooserDismiss() {
        uiState.update { it.copy(chooser = persistentListOf()) }
    }

    fun onMarkRead(id: String) {
        viewModelScope.launch {
            runCatching { notificationsRepository.markRead(id) }.onFailure { fatal(logger, it) }
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
        /** Non-empty while a multi-game waitlist notification is awaiting the user's pick (#288). */
        val chooser: ImmutableList<NotificationGame> = persistentListOf(),
    ) {
        val hasUnread: Boolean get() = notifications.any { !it.read }
    }

    sealed interface NotificationsUiEvent {
        data class OpenGame(val gameId: String) : NotificationsUiEvent
    }

    private companion object {
        const val WAITLIST_TYPE = "waitlist"
    }
}
