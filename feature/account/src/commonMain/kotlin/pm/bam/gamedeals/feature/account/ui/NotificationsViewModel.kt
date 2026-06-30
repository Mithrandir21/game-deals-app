package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/**
 * Backs the Notifications **list** sub-screen (#7 notification revamp). A single ITAD notification is a daily
 * digest that can reference several games, so the list groups notifications by **calendar day** and makes the
 * row about the *games*: the title names the games that dropped and the subtitle is the date + distinct-game
 * count. Tapping a row opens that day's [NotificationDayScreen] (the games + deals notified that day).
 *
 * The day's games are resolved per notification via [NotificationsRepository.getNotificationDetail] (cached;
 * same endpoint as the detail screen, so this also warms it). Read/unread stays reactive off
 * [NotificationsRepository.observeNotifications]; the resolved games are held in [gamesByEntry] and combined
 * in. [onMarkAllRead] goes through the repository.
 */
internal class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository,
    private val logger: Logger,
) : ViewModel() {

    /** entry id → the games that notification refers to (resolved once per remote-as-truth load). */
    private val gamesByEntry = MutableStateFlow<Map<String, List<NotificationGameThumb>>>(emptyMap())

    val uiState: StateFlow<NotificationsScreenData>
        field = MutableStateFlow(NotificationsScreenData(loading = true))

    init {
        // Day rows = the reactive notifications list joined with the resolved games. Read/unread updates
        // (e.g. a mark-read in the day detail) flow through here; names/count come from [gamesByEntry].
        viewModelScope.launch {
            combine(notificationsRepository.observeNotifications(), gamesByEntry) { list, gamesByEntry ->
                list
                    .groupBy { it.timestamp.substringBefore('T') }
                    .map { (date, entries) ->
                        val games = entries.flatMap { gamesByEntry[it.id].orEmpty() }.distinctBy { it.gameId }
                        NotificationDay(
                            date = date,
                            games = games.toImmutableList(),
                            count = games.size,
                            hasUnread = entries.any { !it.read },
                        )
                    }
                    .sortedByDescending { it.date }
                    .toImmutableList()
            }.collect { days -> uiState.update { it.copy(days = days) } }
        }
        // Remote-as-truth reload, then resolve each notification's games (cached) before dropping the spinner.
        viewModelScope.launch {
            val list = runCatching { notificationsRepository.getNotifications() }
                .getOrElse { error(logger, it); emptyList() }
            gamesByEntry.value = coroutineScope {
                list.map { entry ->
                    async {
                        entry.id to runCatching { notificationsRepository.getNotificationDetail(entry.id).games }
                            .getOrElse { error(logger, it); emptyList() }
                            .map { NotificationGameThumb(gameId = it.gameId, title = it.title, thumbnailUrl = it.artwork.thumbnail) }
                    }
                }.awaitAll()
            }.toMap()
            uiState.update { it.copy(loading = false) }
        }
    }

    fun onMarkAllRead() {
        viewModelScope.launch {
            runCatching { notificationsRepository.markAllRead() }.onFailure { error(logger, it) }
        }
    }

    /**
     * One calendar day of notifications. [date] is the ISO date (e.g. "2026-06-18") and the nav key;
     * [games] are the distinct games that dropped that day (name + thumbnail) and [count] is their number.
     */
    @Immutable
    data class NotificationDay(
        val date: String,
        val games: ImmutableList<NotificationGameThumb>,
        val count: Int,
        val hasUnread: Boolean,
    )

    /** A distinct game in a day row: its [title] for the names line and a [thumbnailUrl] for the cover strip
     *  (null when no waitlist art was joinable — the row falls back to the placeholder drawable). */
    @Immutable
    data class NotificationGameThumb(
        val gameId: String,
        val title: String,
        val thumbnailUrl: String?,
    )

    @Immutable
    data class NotificationsScreenData(
        val loading: Boolean = false,
        val days: ImmutableList<NotificationDay> = persistentListOf(),
    ) {
        val hasUnread: Boolean get() = days.any { it.hasUnread }
    }
}
