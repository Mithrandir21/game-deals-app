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
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.runCatchingLogged

/**
 * Backs the notification **day** detail screen (#7 notification revamp) — the games + deals notified on one
 * calendar [date]. ITAD emits one entry per game, so a day aggregates several entries: each entry's deal
 * content is loaded ([NotificationsRepository.getNotificationDetail], cached + art-joined) and merged. The
 * **same game can be referenced by more than one entry that day** (e.g. it dropped twice), so games are
 * de-duplicated by id for display (one card per game — a unique LazyColumn key) while every source entry is
 * remembered ([sourceIdsByGameId]).
 *
 * Read state is **per-game**: there's no "mark the whole day read on open". When a game's card is *viewed*
 * ([onGameViewed], fired as it scrolls into view) **all** that game's source entries are marked read, deduped
 * so each entry is marked at most once. Each card lists **all** its shop deals with the best (lowest) price
 * highlighted as "the deal you were notified about".
 *
 * ITAD prices alerts in the region set on the user's website account, which the API can't read or change. When
 * the alerts come in another currency than the app's region, [NotificationDayScreenData.regionNote] says so,
 * since the game page the user opens next shows the app region's prices.
 */
internal class NotificationDayViewModel(
    savedStateHandle: SavedStateHandle,
    private val notificationsRepository: NotificationsRepository,
    private val regionRepository: RegionRepository,
    private val logger: Logger,
) : ViewModel() {

    private val date: String? = savedStateHandle.get<String>("date")

    /** Source entry ids already marked read this session — keeps [onGameViewed] idempotent. */
    private val markedRead = mutableSetOf<String>()

    /** game id → the ids of every notification entry that referenced it this day (for per-game read). */
    private var sourceIdsByGameId: Map<String, List<String>> = emptyMap()

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
            val entries = runCatchingLogged(logger) { notificationsRepository.observeNotifications().first() }
                .getOrElse { emptyList() }
                .filter { it.day == day }
            // (entryId, game) pairs across all the day's entries.
            val pairs = entries.flatMap { entry ->
                runCatchingLogged(logger) { notificationsRepository.getNotificationDetail(entry.id).games }
                    .getOrElse { emptyList() }
                    .map { entry.id to it }
            }
            sourceIdsByGameId = pairs.groupBy({ it.second.gameId }, { it.first })
            // One card per game (a game may be referenced by several entries the same day) — distinct ids
            // also keep the LazyColumn keys unique.
            val games = pairs.map { it.second }.distinctBy { it.gameId }
            uiState.update { it.copy(loading = false, games = games.toImmutableList(), regionNote = regionNote(games)) }
        }
    }

    /** A game's card became visible — mark every entry that referenced it read (once each). */
    fun onGameViewed(gameId: String) {
        sourceIdsByGameId[gameId]?.forEach { sourceId ->
            if (markedRead.add(sourceId)) {
                viewModelScope.launch {
                    runCatchingLogged(logger) { notificationsRepository.markRead(sourceId) }
                }
            }
        }
    }

    private suspend fun regionNote(games: List<NotificationDealGame>): RegionNote? {
        val alertCurrency = games.flatMap { it.deals }.firstNotNullOfOrNull { deal -> deal.currency.takeIf { it.isNotBlank() } }
            ?: return null
        val appCurrency = runCatchingLogged(logger) { regionRepository.observeSelectedCountry().first().currency }.getOrNull()
            ?: return null
        return if (alertCurrency.equals(appCurrency, ignoreCase = true)) null else RegionNote(alertCurrency, appCurrency)
    }

    fun onOpenGame(gameId: String) {
        events.tryEmit(NotificationDayEvent.OpenGame(gameId))
    }

    @Immutable
    data class NotificationDayScreenData(
        val loading: Boolean = false,
        val games: ImmutableList<NotificationDealGame> = persistentListOf(),
        val regionNote: RegionNote? = null,
    )

    /** The alerts are priced in [alertCurrency] (the ITAD website account's region), the app in [appCurrency]. */
    @Immutable
    data class RegionNote(val alertCurrency: String, val appCurrency: String)

    sealed interface NotificationDayEvent {
        data class OpenGame(val gameId: String) : NotificationDayEvent
    }
}
