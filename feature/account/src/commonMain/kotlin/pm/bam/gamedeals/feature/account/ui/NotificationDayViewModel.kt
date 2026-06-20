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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the notification **day** detail screen (#7 notification revamp) — the games + deals notified on one
 * calendar [date]. ITAD emits one entry per game, so a day aggregates several entries: each entry's deal
 * content is loaded ([NotificationsRepository.getNotificationDetail], cached + art-joined) and merged, with
 * every game tagged with its source entry id.
 *
 * Read state is **per-game**: there's no "mark the whole day read on open". A game's source entry is marked
 * read when its card is *viewed* ([onGameViewed], fired as the card scrolls into view), deduped so each
 * entry is marked at most once. Each game card lists **all** its shop deals with the best (lowest) price
 * highlighted as "the deal you were notified about".
 */
internal class NotificationDayViewModel(
    savedStateHandle: SavedStateHandle,
    private val notificationsRepository: NotificationsRepository,
    private val logger: Logger,
) : ViewModel() {

    private val date: String? = savedStateHandle.get<String>("date")

    /** Source entry ids already marked read this session — keeps [onGameViewed] idempotent. */
    private val markedRead = mutableSetOf<String>()

    val uiState: StateFlow<NotificationDayScreenData>
        field = MutableStateFlow(NotificationDayScreenData(loading = true))

    val events: SharedFlow<NotificationDayEvent>
        field = MutableSharedFlow<NotificationDayEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        load()
    }

    private fun load() {
        val day = date
        if (day == null) {
            uiState.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val entries = runCatching { notificationsRepository.observeNotifications().first() }
                .getOrElse { fatal(logger, it); emptyList() }
                .filter { it.timestamp.substringBefore('T') == day }
            val games = entries.flatMap { entry ->
                runCatching { notificationsRepository.getNotificationDetail(entry.id).games }
                    .getOrElse { fatal(logger, it); emptyList() }
                    .map { it.copy(sourceNotificationId = entry.id) }
            }
            uiState.update { it.copy(loading = false, games = games.toImmutableList()) }
        }
    }

    /** A game's card became visible — mark *its* notification entry read (once). */
    fun onGameViewed(gameId: String) {
        val sourceId = uiState.value.games.firstOrNull { it.gameId == gameId }?.sourceNotificationId ?: return
        if (!markedRead.add(sourceId)) return
        viewModelScope.launch {
            runCatching { notificationsRepository.markRead(sourceId) }.onFailure { fatal(logger, it) }
        }
    }

    fun onOpenGame(gameId: String) {
        events.tryEmit(NotificationDayEvent.OpenGame(gameId))
    }

    @Immutable
    data class NotificationDayScreenData(
        val loading: Boolean = false,
        val games: ImmutableList<NotificationDealGame> = persistentListOf(),
    )

    sealed interface NotificationDayEvent {
        data class OpenGame(val gameId: String) : NotificationDayEvent
    }
}
