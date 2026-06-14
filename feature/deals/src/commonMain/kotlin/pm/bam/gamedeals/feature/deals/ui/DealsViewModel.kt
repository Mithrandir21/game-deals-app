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
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.deal.DealDetailsController
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSort
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.logging.info

/** Minimum time the search spinner stays up so a fast result doesn't flash (ported from the Search screen). */
private const val SEARCH_MIN_LOADING_MILLIS = 1000L

/**
 * Drives the Deals tab (epic #219, Phase 4): a sorted, all-stores page over `/deals/v2` with
 * offset-based load-more. The deals are fetched fresh (not Room-cached) via [DealsRepository.getDeals].
 *
 * The list reloads from offset 0 whenever the [sort] filter, the shop filter, the [mature] toggle or
 * the user's selected region changes; [loadNextPage] appends the next page until the source returns a
 * short (final) page. The row heart writes to the ITAD waitlist ([WaitlistRepository]) and the row tap
 * opens the shared `DealBottomSheet` via [DealDetailsController] — mirroring the Store screen.
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
    private val regionRepository: RegionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val gamesRepository: GamesRepository,
) : ViewModel() {

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
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

    private val sort = MutableStateFlow(DealsSort.TopDiscount)
    private val selectedShopIds = MutableStateFlow<Set<Int>>(emptySet())
    private val matureFlow = MutableStateFlow(false)

    /** The currently selected shop ids (empty = all stores). */
    val selectedShops: StateFlow<ImmutableSet<Int>> = selectedShopIds
        .map { it.toImmutableSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Whether adult titles are included (`mature=true` on `/deals/v2`); default off. */
    val mature: StateFlow<Boolean> = matureFlow.asStateFlow()

    val uiState: StateFlow<DealsScreenData>
        field = MutableStateFlow(DealsScreenData())

    // --- Title search (merged from the former Search screen) ---

    private val searchQueryState = MutableStateFlow("")

    /** The raw title-search text; blank means browse mode (the sorted/filtered deals list). */
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()

    val searchResults: StateFlow<SearchResultsState>
        field = MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)

    private val dealDetailsController = DealDetailsController(dealsRepository, storesRepository, logger)
    val dealDetails: StateFlow<DealBottomSheetData?> = dealDetailsController.dealDetails

    val events: SharedFlow<DealsUiEvent>
        field = MutableSharedFlow<DealsUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var appendJob: Job? = null

    init {
        // First load + reload whenever the sort, the shop filter, the mature toggle or the region
        // changes. The region flow emits its seeded value immediately, so this also performs the initial
        // load. `collectLatest` cancels an in-flight first-page load if a filter changes again first.
        viewModelScope.launch {
            combine(
                sort,
                selectedShopIds,
                matureFlow,
                regionRepository.observeSelectedCountry().map { it.code }.distinctUntilChanged(),
            ) { selectedSort, shops, mature, _ -> BrowseParams(selectedSort, shops, mature) }
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

    fun setSort(newSort: DealsSort) = sort.update { newSort }

    /** Toggle a shop in/out of the filter; an empty selection means "all stores". */
    fun toggleShop(storeId: Int) = selectedShopIds.update { if (storeId in it) it - storeId else it + storeId }

    fun clearShopFilter() = selectedShopIds.update { emptySet() }

    /** Toggle whether adult titles are included in the browse list. */
    fun setMature(enabled: Boolean) = matureFlow.update { enabled }

    /** Update the title-search text; blank returns to browse mode. */
    fun setSearchQuery(query: String) = searchQueryState.update { query }

    fun clearSearch() = searchQueryState.update { "" }

    fun retry() {
        viewModelScope.launch { loadFirstPage(BrowseParams(sort.value, selectedShopIds.value, matureFlow.value)) }
    }

    private suspend fun loadFirstPage(params: BrowseParams) {
        appendJob?.cancel()
        uiState.update {
            DealsScreenData(
                status = DealsScreenData.Status.LOADING,
                sort = params.sort,
                shopIds = params.shopIds.toImmutableSet(),
                mature = params.mature,
            )
        }
        try {
            val page = dealsRepository.getDeals(DealsQuery(sort = params.sort, shopIds = params.shopIds.toList(), mature = params.mature, offset = 0))
            uiState.update {
                DealsScreenData(
                    status = DealsScreenData.Status.DATA,
                    sort = params.sort,
                    shopIds = params.shopIds.toImmutableSet(),
                    mature = params.mature,
                    deals = page.toImmutableList(),
                    endReached = page.size < DealsQuery.DEALS_PAGE_SIZE,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            fatal(logger, t) { "Failed to load deals (sort=${params.sort}, shops=${params.shopIds}, mature=${params.mature})" }
            uiState.update {
                DealsScreenData(status = DealsScreenData.Status.ERROR, sort = params.sort, shopIds = params.shopIds.toImmutableSet(), mature = params.mature)
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
                val page = dealsRepository.getDeals(DealsQuery(sort = current.sort, shopIds = current.shopIds.toList(), mature = current.mature, offset = current.deals.size))
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
                fatal(logger, t) { "Failed to load more deals" }
                uiState.update { it.copy(appending = false) }
                events.tryEmit(DealsUiEvent.LoadMoreError)
            }
        }
    }

    fun loadDealDetails(dealId: String, dealStoreId: Int, dealGameId: String, dealTitle: String, dealPriceDenominated: String, dealUrl: String) {
        dealDetailsController.load(viewModelScope, dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl)
    }

    fun dismissDealDetails() {
        dealDetailsController.dismiss(viewModelScope)
    }

    fun toggleWaitlistFromDeal(data: DealBottomSheetData.DealDetailsData) = toggleWaitlist(data.gameId)

    /** Toggle a game on/off the waitlist from an inline row heart; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(DealsUiEvent.SignInRequired)
            }
        }
    }

    fun toggleIgnoreFromDeal(data: DealBottomSheetData.DealDetailsData) = toggleIgnore(data.gameId)

    /** Toggle a game on/off the ignore list from the deal sheet; prompts sign-in when logged out. */
    fun toggleIgnore(gameId: String) {
        viewModelScope.launch {
            if (ignoredRepository.toggleIgnored(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(DealsUiEvent.SignInRequired)
            }
        }
    }

    fun onShareDealClicked(data: DealBottomSheetData) {
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = data.gameSalesPriceDenominated,
            storeName = data.store.storeName,
            dealUrl = data.dealUrl,
        )
        info(logger, tag = "deal_shared") { "dealId=${data.dealId} store=${data.store.storeName}" }
        events.tryEmit(DealsUiEvent.ShareDeal(text))
    }

    private data class BrowseParams(val sort: DealsSort, val shopIds: Set<Int>, val mature: Boolean)

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
        val sort: DealsSort = DealsSort.TopDiscount,
        val shopIds: ImmutableSet<Int> = persistentSetOf(),
        val mature: Boolean = false,
        val deals: ImmutableList<Deal> = persistentListOf(),
        val appending: Boolean = false,
        val endReached: Boolean = false,
    ) {
        enum class Status { LOADING, ERROR, DATA }
    }
}
