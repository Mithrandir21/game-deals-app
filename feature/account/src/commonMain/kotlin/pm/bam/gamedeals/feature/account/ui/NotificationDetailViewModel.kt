package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
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
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the notification **detail** screen — the deals inside one daily notification, mirroring the ITAD
 * website (#272 follow-up). On open it loads the full deal content ([NotificationsRepository.getNotificationDetail],
 * cached + art-joined) and **marks the notification read** (the read state is the act of opening the detail).
 *
 * Each game card collapses to its best deal and expands to all shop deals on tap ([onToggleExpanded]); the
 * card's `Detail →` action deep-links to the in-app game page ([NotificationDetailEvent.OpenGame]).
 */
internal class NotificationDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val notificationsRepository: NotificationsRepository,
    private val logger: Logger,
) : ViewModel() {

    private val notificationId: String? = savedStateHandle.get<String>("notificationId")

    val uiState: StateFlow<NotificationDetailScreenData>
        field = MutableStateFlow(NotificationDetailScreenData(loading = true))

    val events: SharedFlow<NotificationDetailEvent>
        field = MutableSharedFlow<NotificationDetailEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        load()
    }

    private fun load() {
        val id = notificationId
        if (id == null) {
            uiState.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val games = runCatching { notificationsRepository.getNotificationDetail(id).games }
                .getOrElse { fatal(logger, it); emptyList() }
            uiState.update { it.copy(loading = false, games = games.toImmutableList()) }
        }
        // Opening the detail is the read action (#272 P2 — mark read on open).
        viewModelScope.launch {
            runCatching { notificationsRepository.markRead(id) }.onFailure { fatal(logger, it) }
        }
    }

    fun onToggleExpanded(gameId: String) {
        uiState.update { state ->
            state.copy(expandedGameIds = state.expandedGameIds.toggle(gameId))
        }
    }

    fun onOpenGame(gameId: String) {
        events.tryEmit(NotificationDetailEvent.OpenGame(gameId))
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value

    @Immutable
    data class NotificationDetailScreenData(
        val loading: Boolean = false,
        val games: ImmutableList<NotificationDealGame> = persistentListOf(),
        /** Game ids whose card is expanded to show every shop deal (collapsed shows only the best deal). */
        val expandedGameIds: Set<String> = emptySet(),
    )

    sealed interface NotificationDetailEvent {
        data class OpenGame(val gameId: String) : NotificationDetailEvent
    }
}
