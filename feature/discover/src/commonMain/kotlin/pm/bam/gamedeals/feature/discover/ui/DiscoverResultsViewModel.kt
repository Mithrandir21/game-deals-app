package pm.bam.gamedeals.feature.discover.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.deal.GamePeekController
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.domain.repositories.discovery.DISCOVERY_PAGE_SIZE
import pm.bam.gamedeals.domain.repositories.discovery.TagDiscoveryRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.logging.info

/**
 * Drives the tag-discovery results screen (epic #307, Phase 5). Reconstructs the [IgdbTagFilter] from
 * the route args, loads the first page, and appends pages on scroll-end via offset paging — mirroring
 * [pm.bam.gamedeals.feature.deals.ui.DealsViewModel]. An empty page surfaces as [Status.EMPTY].
 *
 * Results render with the shared `DealListRow` (same as Home/Deals), so the screen also exposes the
 * waitlist ids + store-icon-by-name map that row needs, and a waitlist toggle.
 */
internal class DiscoverResultsViewModel(
    private val logger: Logger,
    private val tagDiscoveryRepository: TagDiscoveryRepository,
    gamesRepository: GamesRepository,
    storesRepository: StoresRepository,
    private val waitlistRepository: WaitlistRepository,
    private val ignoredRepository: IgnoredRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val filter: IgdbTagFilter = savedStateHandle.toFilter()

    /** Games on the user's waitlist — drives the inline row heart (matches Deals/Home). */
    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Games on the user's ignore list — drives the peek sheet's ignore state. */
    val ignoredIds: StateFlow<ImmutableSet<String>> = ignoredRepository.observeIgnoredIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Shop name → icon URL, so a priced row can show the store logo like a deal row does. */
    val storeIconsByName: StateFlow<Map<String, String?>> = storesRepository.observeStores()
        .map { stores -> stores.associate { it.storeName to it.iconUrl } }
        .onStart { emit(emptyMap()) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // The shared game-centric peek sheet (same as Home/Deals). A priced row carries the gameId +
    // title + cover the controller needs; the controller fetches deals/stores itself.
    private val gamePeekController = GamePeekController(gamesRepository, storesRepository, logger)
    val gamePeek: StateFlow<GamePeekSheetData?> = gamePeekController.data

    val uiState: StateFlow<ResultsScreenData>
        field = MutableStateFlow(ResultsScreenData())

    val events: SharedFlow<DiscoverResultsUiEvent>
        field = MutableSharedFlow<DiscoverResultsUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var appendJob: Job? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        appendJob?.cancel()
        viewModelScope.launch {
            uiState.update { ResultsScreenData(status = ResultsScreenData.Status.LOADING) }
            try {
                val page = tagDiscoveryRepository.discover(filter, offset = 0)
                uiState.update {
                    ResultsScreenData(
                        status = if (page.isEmpty()) ResultsScreenData.Status.EMPTY else ResultsScreenData.Status.DATA,
                        results = page.toImmutableList(),
                        endReached = page.size < DISCOVERY_PAGE_SIZE,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                fatal(logger, t) { "Failed to load tag-discovery results (filter=$filter)" }
                uiState.update { ResultsScreenData(status = ResultsScreenData.Status.ERROR) }
            }
        }
    }

    fun loadNextPage() {
        val current = uiState.value
        if (current.status != ResultsScreenData.Status.DATA || current.appending || current.endReached) return
        appendJob?.cancel()
        appendJob = viewModelScope.launch {
            uiState.update { it.copy(appending = true) }
            try {
                val page = tagDiscoveryRepository.discover(filter, offset = current.results.size)
                uiState.update { state ->
                    state.copy(
                        results = (state.results + page).toImmutableList(),
                        appending = false,
                        endReached = page.size < DISCOVERY_PAGE_SIZE,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                fatal(logger, t) { "Failed to load more tag-discovery results" }
                uiState.update { it.copy(appending = false) }
                events.tryEmit(DiscoverResultsUiEvent.LoadMoreError)
            }
        }
    }

    fun retry() = loadFirstPage()

    /** Toggle a game on/off the waitlist from the inline row heart or peek sheet; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(DiscoverResultsUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game on/off the ignore list from the peek sheet; prompts sign-in when logged out. */
    fun toggleIgnore(gameId: String) {
        viewModelScope.launch {
            if (ignoredRepository.toggleIgnored(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(DiscoverResultsUiEvent.SignInRequired)
            }
        }
    }

    /** Open the game-centric peek sheet for a priced row (it carries the ITAD gameId + title + cover). */
    fun peekGame(gameId: String, gameName: String, thumb: String?) {
        gamePeekController.load(viewModelScope, gameId, gameName, thumb)
    }

    fun dismissPeek() {
        gamePeekController.dismiss(viewModelScope)
    }

    fun retryPeek() {
        gamePeek.value?.let { peek -> peekGame(peek.gameId, peek.gameName, peek.thumb) }
    }

    fun onShareClicked(data: GamePeekSheetData.Data) {
        val best = data.bestDeal ?: return
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = best.deal.priceDenominated,
            storeName = best.store.storeName,
            dealUrl = best.deal.url,
        )
        info(logger, tag = "discover_deal_shared") { "gameId=${data.gameId} store=${best.store.storeName}" }
        events.tryEmit(DiscoverResultsUiEvent.ShareDeal(text))
    }

    data class ResultsScreenData(
        val status: Status = Status.LOADING,
        val results: ImmutableList<TagDiscoveryResult> = persistentListOf(),
        val appending: Boolean = false,
        val endReached: Boolean = false,
    ) {
        enum class Status { LOADING, ERROR, EMPTY, DATA }
    }

    sealed interface DiscoverResultsUiEvent {
        data object LoadMoreError : DiscoverResultsUiEvent
        data object SignInRequired : DiscoverResultsUiEvent
        data class ShareDeal(val text: String) : DiscoverResultsUiEvent
    }
}

/** Decodes the comma-joined per-dimension id args (see [pm.bam.gamedeals.common.navigation.Destination.DiscoverResults]). */
private fun SavedStateHandle.toFilter(): IgdbTagFilter = IgdbTagFilter(
    genreIds = idList("genreIds"),
    themeIds = idList("themeIds"),
    gameModeIds = idList("gameModeIds"),
    perspectiveIds = idList("perspectiveIds"),
    keywordIds = idList("keywordIds"),
)

private fun SavedStateHandle.idList(key: String): kotlinx.collections.immutable.ImmutableList<Long> =
    get<String>(key).orEmpty()
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .toImmutableList()
