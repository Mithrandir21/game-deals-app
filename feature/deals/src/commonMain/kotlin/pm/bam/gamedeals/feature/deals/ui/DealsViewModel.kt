package pm.bam.gamedeals.feature.deals.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.flatMapLatestDelayAtLeast
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.ui.deal.GamePeekController
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealFlag
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSortDirection
import pm.bam.gamedeals.domain.models.DealsSortField
import pm.bam.gamedeals.domain.models.ProductType
import pm.bam.gamedeals.domain.models.ReleaseWindow
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.logging.info

/** Minimum time the search spinner stays up so a fast result doesn't flash (ported from the Search screen). */
private const val SEARCH_MIN_LOADING_MILLIS = 1000L

/**
 * Drives the Deals tab (epic #219, Phase 4): a sorted, all-stores page over `/deals/v2` with
 * offset-based load-more. The deals are fetched fresh (not Room-cached) via [DealsRepository.getDeals].
 *
 * The list reloads from offset 0 whenever the [sort] filter, the shop filter, the [mature] toggle or
 * the user's selected region changes; [loadNextPage] appends the next page until the source returns a
 * short (final) page. Rows show passive waitlist/collection badges ([WaitlistRepository] /
 * [CollectionRepository]); a row tap opens the shared game-centric peek sheet (`GamePeekController` /
 * `GamePeekSheet`), where the waitlist / collection / ignore / share actions live — like Home.
 *
 * Title search (merged from the former Search screen — epic #291 follow-up) is overlaid on the same
 * screen: a non-blank [searchQuery] switches the list to grouped results from
 * [GamesRepository.searchGames]; a blank query falls back to the browse list. Search ignores the
 * sort/shop/mature filters (ITAD's title search has no such parameters).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
internal class DealsViewModel(
    private val logger: Logger,
    private val dealsRepository: DealsRepository,
    storesRepository: StoresRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val regionRepository: RegionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val gamesRepository: GamesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val collectionIds: StateFlow<ImmutableSet<String>> = collectionRepository.observeCollectionIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Games on the user's ignore list — hidden from the deals list (#280). */
    val ignoredIds: StateFlow<ImmutableSet<String>> = ignoredRepository.observeIgnoredIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Active stores offered as the shop filter (empty selection = all stores). */
    val stores: StateFlow<ImmutableList<Store>> = storesRepository.observeStores()
        .map { stores -> stores.filter { it.isActive }.toImmutableList() }
        .onStart { emit(persistentListOf()) }
        .catch { emit(persistentListOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    // Field + direction held in a single flow so the init `combine` below stays within its 5-source arity.
    private val sortSelection = MutableStateFlow(SortSelection(DealsSortField.Hottest, DealsSortField.Hottest.defaultDirection))
    private val selectedShopIds = MutableStateFlow<Set<Int>>(emptySet())

    /** The currently selected shop ids (empty = all stores). */
    val selectedShops: StateFlow<ImmutableSet<Int>> = selectedShopIds
        .map { it.toImmutableSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /**
     * The active server-side deal filters (discount, price, type, DRM-free, deal flag, Steam %, release
     * recency) applied to `/deals/v2`; default empty. Persisted via [SettingsRepository] so it survives
     * relaunches (Deals-only — not shared with Bundles). Changing it reloads the list from offset 0.
     */
    val filter: StateFlow<DealsFilter> = settingsRepository.observeDealsFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DealsFilter())

    val uiState: StateFlow<DealsScreenData>
        field = MutableStateFlow(DealsScreenData())

    // --- Title search (merged from the former Search screen) ---

    private val searchQueryState = MutableStateFlow("")

    /** The raw title-search text; blank means browse mode (the sorted/filtered deals list). */
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()

    val searchResults: StateFlow<SearchResultsState>
        field = MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)

    private val gamePeekController = GamePeekController(gamesRepository, storesRepository, logger)
    val gamePeek: StateFlow<GamePeekSheetData?> = gamePeekController.data

    val events: SharedFlow<DealsUiEvent>
        field = MutableSharedFlow<DealsUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var appendJob: Job? = null

    init {
        // First load + reload whenever the sort, the shop filter, the mature toggle, the region, or the
        // server-side deal filter changes. The region flow emits its seeded value immediately, so this
        // also performs the initial load. `collectLatest` cancels an in-flight first-page load if a
        // filter changes again first.
        viewModelScope.launch {
            combine(
                sortSelection,
                selectedShopIds,
                settingsRepository.observeMatureOptIn(),
                regionRepository.observeSelectedCountry().map { it.code }.distinctUntilChanged(),
                settingsRepository.observeDealsFilter(),
            ) { selectedSort, shops, mature, _, dealsFilter ->
                BrowseParams(selectedSort.field, selectedSort.direction, shops, mature, dealsFilter)
            }
                .collectLatest { loadFirstPage(it) }
        }

        // Title search: a blank query yields the browse list (Idle); a non-blank query shows a brief
        // spinner then the grouped results, cancelling any in-flight search when the query changes.
        viewModelScope.launch {
            searchQueryState
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(SearchResultsState.Idle)
                    } else {
                        flowOf(query)
                            .onEach { searchResults.emit(SearchResultsState.Loading) }
                            .flatMapLatestDelayAtLeast(SEARCH_MIN_LOADING_MILLIS) {
                                gamesRepository.searchGames(SearchParameters(title = it))
                            }
                            .map { deals ->
                                if (deals.isEmpty()) SearchResultsState.NoResults
                                else SearchResultsState.Results(deals.groupByGame().toImmutableList())
                            }
                            .catch { emit(SearchResultsState.Error) }
                    }
                }
                .logFlow(logger)
                .collect { searchResults.emit(it) }
        }
    }

    /** Select the sort field; resets the direction to that field's default (matching the website). */
    fun setSortField(field: DealsSortField) = sortSelection.update { SortSelection(field, field.defaultDirection) }

    /** Flip the direction (asc/desc) for the currently selected field. */
    fun setSortDirection(direction: DealsSortDirection) = sortSelection.update { it.copy(direction = direction) }

    /** Toggle a shop in/out of the filter; an empty selection means "all stores". */
    fun toggleShop(storeId: Int) = selectedShopIds.update { if (storeId in it) it - storeId else it + storeId }

    fun clearShopFilter() = selectedShopIds.update { emptySet() }

    // --- Server-side deal filters (persisted; each change reloads the list from offset 0) ---

    /** Minimum discount percent, or null for no discount floor. */
    fun setMinCut(percent: Int?) = updateFilter { it.copy(minCutPercent = percent) }

    /** Maximum sale price in the region's currency (0.0 = Free), or null for no price cap. */
    fun setMaxPrice(maxPrice: Double?) = updateFilter { it.copy(maxPrice = maxPrice) }

    /** Toggle a product type in/out of the filter; an empty selection means "all types". */
    fun toggleType(type: ProductType) = updateFilter { current ->
        current.copy(types = if (type in current.types) current.types - type else current.types + type)
    }

    /** Toggle the DRM-free-only constraint. */
    fun setDrmFree(enabled: Boolean) = updateFilter { it.copy(drmFree = enabled) }

    /** Restrict to a deal flag (new/historical/shop low), or null for any. */
    fun setFlag(flag: DealFlag?) = updateFilter { it.copy(flag = flag) }

    /** Minimum Steam review percent, or null for no review floor. */
    fun setMinSteam(percent: Int?) = updateFilter { it.copy(minSteamPercent = percent) }

    /** Restrict to a release-recency window, or null for any. */
    fun setRelease(window: ReleaseWindow?) = updateFilter { it.copy(release = window) }

    /** Clear every server-side deal filter (does not touch sort/shops/mature). */
    fun clearFilters() = updateFilter { DealsFilter() }

    // Reads the authoritative current filter from the repository, applies [transform], and persists it.
    // Persisting flips the reactive `observeDealsFilter` flow → the init combine reloads from offset 0.
    private fun updateFilter(transform: (DealsFilter) -> DealsFilter) {
        viewModelScope.launch { settingsRepository.setDealsFilter(transform(settingsRepository.getDealsFilter())) }
    }

    /** Update the title-search text; blank returns to browse mode. */
    fun setSearchQuery(query: String) = searchQueryState.update { query }

    fun clearSearch() = searchQueryState.update { "" }

    fun retry() {
        viewModelScope.launch {
            loadFirstPage(BrowseParams(sortSelection.value.field, sortSelection.value.direction, selectedShopIds.value, settingsRepository.getMatureOptIn(), filter.value))
        }
    }

    private suspend fun loadFirstPage(params: BrowseParams) {
        appendJob?.cancel()
        uiState.update {
            DealsScreenData(
                status = DealsScreenData.Status.LOADING,
                sortField = params.sortField,
                sortDirection = params.sortDirection,
                shopIds = params.shopIds.toImmutableSet(),
                mature = params.mature,
                filter = params.filter,
            )
        }
        try {
            val page = dealsRepository.getDeals(DealsQuery(sortField = params.sortField, sortDirection = params.sortDirection, shopIds = params.shopIds.toList(), mature = params.mature, filter = params.filter, offset = 0))
            uiState.update {
                DealsScreenData(
                    status = DealsScreenData.Status.DATA,
                    sortField = params.sortField,
                    sortDirection = params.sortDirection,
                    shopIds = params.shopIds.toImmutableSet(),
                    mature = params.mature,
                    filter = params.filter,
                    deals = page.toImmutableList(),
                    endReached = page.size < DealsQuery.DEALS_PAGE_SIZE,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            error(logger, t) { "Failed to load deals (sort=${params.sortField}/${params.sortDirection}, shops=${params.shopIds}, mature=${params.mature}, filter=${params.filter})" }
            uiState.update {
                DealsScreenData(status = DealsScreenData.Status.ERROR, sortField = params.sortField, sortDirection = params.sortDirection, shopIds = params.shopIds.toImmutableSet(), mature = params.mature, filter = params.filter)
            }
        }
    }

    fun loadNextPage() {
        val current = uiState.value
        if (current.status != DealsScreenData.Status.DATA || current.appending || current.endReached) return
        appendJob?.cancel()
        appendJob = viewModelScope.launch {
            uiState.update { it.copy(appending = true) }
            try {
                val page = dealsRepository.getDeals(DealsQuery(sortField = current.sortField, sortDirection = current.sortDirection, shopIds = current.shopIds.toList(), mature = current.mature, filter = current.filter, offset = current.deals.size))
                uiState.update { state ->
                    state.copy(
                        deals = (state.deals + page).toImmutableList(),
                        appending = false,
                        endReached = page.size < DealsQuery.DEALS_PAGE_SIZE,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                error(logger, t) { "Failed to load more deals" }
                uiState.update { it.copy(appending = false) }
                events.tryEmit(DealsUiEvent.LoadMoreError)
            }
        }
    }

    /** Open the game-centric peek sheet for a deal/search row. */
    fun peekGame(gameId: String, gameName: String, thumb: String?) {
        gamePeekController.load(viewModelScope, gameId, gameName, thumb)
    }

    fun dismissPeek() {
        gamePeekController.dismiss(viewModelScope)
    }

    /** Toggle a game on/off the waitlist from the peek sheet; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(DealsUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game in/out of the collection from the peek sheet; prompts sign-in when logged out. */
    fun toggleCollection(gameId: String) {
        viewModelScope.launch {
            if (collectionRepository.toggleCollection(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(DealsUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game on/off the ignore list from the peek sheet; prompts sign-in when logged out. */
    fun toggleIgnore(gameId: String) {
        viewModelScope.launch {
            if (ignoredRepository.toggleIgnored(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(DealsUiEvent.SignInRequired)
            }
        }
    }

    fun onShareClicked(data: GamePeekSheetData.Data) {
        val best = data.bestDeal ?: return
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = best.deal.priceDenominated,
            storeName = best.store.storeName,
            dealUrl = best.deal.url,
        )
        info(logger, tag = "deal_shared") { "gameId=${data.gameId} store=${best.store.storeName}" }
        events.tryEmit(DealsUiEvent.ShareDeal(text))
    }

    private data class SortSelection(val field: DealsSortField, val direction: DealsSortDirection)

    private data class BrowseParams(val sortField: DealsSortField, val sortDirection: DealsSortDirection, val shopIds: Set<Int>, val mature: Boolean, val filter: DealsFilter)

    internal sealed interface DealsUiEvent {
        data class ShareDeal(val text: String) : DealsUiEvent
        data object LoadMoreError : DealsUiEvent
        data object SignInRequired : DealsUiEvent
    }

    /** Result state for the overlaid title search; [Idle] means browse mode (no active query). */
    sealed interface SearchResultsState {
        data object Idle : SearchResultsState
        data object Loading : SearchResultsState
        data object NoResults : SearchResultsState
        data object Error : SearchResultsState

        @Immutable
        data class Results(val results: ImmutableList<GroupedSearchResult>) : SearchResultsState
    }

    data class DealsScreenData(
        val status: Status = Status.LOADING,
        val sortField: DealsSortField = DealsSortField.Hottest,
        val sortDirection: DealsSortDirection = DealsSortField.Hottest.defaultDirection,
        val shopIds: ImmutableSet<Int> = persistentSetOf(),
        val mature: Boolean = false,
        val filter: DealsFilter = DealsFilter(),
        val deals: ImmutableList<Deal> = persistentListOf(),
        val appending: Boolean = false,
        val endReached: Boolean = false,
    ) {
        enum class Status { LOADING, ERROR, DATA }
    }
}
