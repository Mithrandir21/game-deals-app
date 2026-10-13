package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.runCatchingLogged

/**
 * Backs the Notifications **list** sub-screen (#7 notification revamp). A single ITAD notification is a daily
 * digest that can reference several games, so the list groups notifications by **calendar day** and makes the
 * row about the *games*: the title names the games that dropped and the subtitle is the date + distinct-game
 * count. Tapping a row opens that day's [NotificationDayScreen] (the games + deals notified that day).
 *
 * The day's games are resolved per notification via [NotificationsRepository.getNotificationDetail] (cached;
 * same endpoint as the detail screen, so this also warms it). Read/unread stays reactive off
 * [NotificationsRepository.observeNotifications]; the resolved games are held in [resolution] and combined
 * in. [onMarkAllRead] goes through the repository.
 */
internal class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository,
    private val logger: Logger,
) : ViewModel() {

    /** One flow, so a day never sees "done" before the last entry's games (two flows can be combined out of order). */
    private val resolution = MutableStateFlow(Resolution())

    val uiState: StateFlow<NotificationsScreenData>
        field = MutableStateFlow(NotificationsScreenData(loading = true))

    init {
        // Day rows = the reactive notifications list joined with the resolved games. Read/unread updates
        // (e.g. a mark-read in the day detail) flow through here; names/count come from [resolution].
        viewModelScope.launch {
            combine(notificationsRepository.observeNotifications(), resolution) { list, (gamesByEntry, resolving) ->
                list
                    .groupBy { it.day }
                    .map { (date, entries) ->
                        val games = entries.flatMap { gamesByEntry[it.id].orEmpty() }.distinctBy { it.gameId }
                        NotificationDay(
                            date = date,
                            games = games.toImmutableList(),
                            count = games.size,
                            hasUnread = entries.any { !it.read },
                            pendingEntries = if (resolving) entries.count { it.id !in gamesByEntry } else 0,
                        )
                    }
                    .sortedByDescending { it.date }
                    .toImmutableList()
            }.collect { days -> uiState.update { it.copy(days = days, loading = it.loading && days.isEmpty()) } }
        }
        load()
    }

    private var loadJob: Job? = null

    /** Retries a load that failed with nothing to show. */
    fun onRetry() {
        uiState.update { it.copy(loading = true, error = false) }
        load()
    }

    /**
     * Remote-as-truth reload: rows show as soon as the list is known, each day's games filling in as they resolve. The list
     * already in memory is resolved first, so days whose details are cached (e.g. by the bell) fill in without waiting. A failed
     * reload keeps showing that list; only with nothing to show is it an [NotificationsScreenData.error].
     */
    private fun load() {
        loadJob?.cancel()
        resolution.value = Resolution()
        loadJob = viewModelScope.launch {
            coroutineScope {
                val started = mutableSetOf<String>()
                fun resolve(entries: List<ItadNotification>) = entries.filter { started.add(it.id) }.forEach { entry ->
                    launch {
                        val games = runCatchingLogged(logger) { notificationsRepository.getNotificationDetail(entry.id).games }
                            .getOrElse { emptyList() }
                            .map { NotificationGameThumb(gameId = it.gameId, title = it.title, thumbnailUrl = it.artwork.thumbnail) }
                        resolution.update { it.copy(gamesByEntry = it.gamesByEntry + (entry.id to games)) }
                    }
                }
                val shown = runCatchingLogged(logger) { notificationsRepository.observeNotifications().first() }.getOrElse { emptyList() }
                resolve(shown)
                val list = runCatchingLogged(logger) { notificationsRepository.getNotifications() }.getOrNull()
                uiState.update { it.copy(loading = false, error = list == null && shown.isEmpty()) }
                resolve(list ?: shown)
            }
            resolution.update { it.copy(resolving = false) }
        }
    }

    fun onMarkAllRead() {
        viewModelScope.launch {
            runCatchingLogged(logger) { notificationsRepository.markAllRead() }
        }
    }

    /** entry id → the games that notification refers to, filled in as each resolves; while [resolving], a missing entry is still loading. */
    private data class Resolution(
        val gamesByEntry: Map<String, List<NotificationGameThumb>> = emptyMap(),
        val resolving: Boolean = true,
    )

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
        /** Entries whose games are still loading; while non-zero the row shows the date and placeholders, not a partial count. */
        val pendingEntries: Int = 0,
    ) {
        val loadingGames: Boolean get() = pendingEntries > 0
    }

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
        /** The list couldn't be loaded and there was none to fall back on — distinct from an empty list. */
        val error: Boolean = false,
        val days: ImmutableList<NotificationDay> = persistentListOf(),
    ) {
        val hasUnread: Boolean get() = days.any { it.hasUnread }
    }
}
