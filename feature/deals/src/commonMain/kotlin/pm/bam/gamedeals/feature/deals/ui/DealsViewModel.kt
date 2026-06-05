package pm.bam.gamedeals.feature.deals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.deal.DealDetailsController
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSort
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistToggleResult
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.logging.info

/**
 * Drives the Deals tab (epic #219, Phase 4): a sorted, all-stores page over `/deals/v2` with
 * offset-based load-more. The deals are fetched fresh (not Room-cached) via [DealsRepository.getDeals].
 *
 * The list reloads from offset 0 whenever the [sort] filter or the user's selected region changes;
 * [loadNextPage] appends the next page until the source returns a short (final) page. The row heart
 * writes to the ITAD waitlist ([WaitlistRepository]) and the row tap opens the shared `DealBottomSheet`
 * via [DealDetailsController] — mirroring the Store screen.
 */
internal class DealsViewModel(
    private val logger: Logger,
    private val dealsRepository: DealsRepository,
    storesRepository: StoresRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val waitlistRepository: WaitlistRepository,
    private val regionRepository: RegionRepository,
) : ViewModel() {

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
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

    /** The currently selected shop ids (empty = all stores). */
    val selectedShops: StateFlow<ImmutableSet<Int>> = selectedShopIds
        .map { it.toImmutableSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val uiState: StateFlow<DealsScreenData>
        field = MutableStateFlow(DealsScreenData())

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
        // First load + reload whenever the sort, the shop filter or the region changes. The region flow
        // emits its seeded value immediately, so this also performs the initial load. `collectLatest`
        // cancels an in-flight first-page load if a filter changes again before it finishes.
        viewModelScope.launch {
            combine(
                sort,
                selectedShopIds,
                regionRepository.observeSelectedCountry().map { it.code }.distinctUntilChanged(),
            ) { selectedSort, shops, _ -> selectedSort to shops }
                .collectLatest { (selectedSort, shops) -> loadFirstPage(selectedSort, shops) }
        }
    }

    fun setSort(newSort: DealsSort) = sort.update { newSort }

    /** Toggle a shop in/out of the filter; an empty selection means "all stores". */
    fun toggleShop(storeId: Int) = selectedShopIds.update { if (storeId in it) it - storeId else it + storeId }

    fun clearShopFilter() = selectedShopIds.update { emptySet() }

    fun retry() {
        viewModelScope.launch { loadFirstPage(sort.value, selectedShopIds.value) }
    }

    private suspend fun loadFirstPage(selectedSort: DealsSort, shopIds: Set<Int>) {
        appendJob?.cancel()
        uiState.update { DealsScreenData(status = DealsScreenData.Status.LOADING, sort = selectedSort, shopIds = shopIds.toImmutableSet()) }
        try {
            val page = dealsRepository.getDeals(DealsQuery(sort = selectedSort, shopIds = shopIds.toList(), offset = 0))
            uiState.update {
                DealsScreenData(
                    status = DealsScreenData.Status.DATA,
                    sort = selectedSort,
                    shopIds = shopIds.toImmutableSet(),
                    deals = page.toImmutableList(),
                    endReached = page.size < DealsQuery.DEALS_PAGE_SIZE,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            fatal(logger, t) { "Failed to load deals (sort=$selectedSort, shops=$shopIds)" }
            uiState.update { DealsScreenData(status = DealsScreenData.Status.ERROR, sort = selectedSort, shopIds = shopIds.toImmutableSet()) }
        }
    }

    fun loadNextPage() {
        val current = uiState.value
        if (current.status != DealsScreenData.Status.DATA || current.appending || current.endReached) return
        appendJob?.cancel()
        appendJob = viewModelScope.launch {
            uiState.update { it.copy(appending = true) }
            try {
                val page = dealsRepository.getDeals(DealsQuery(sort = current.sort, shopIds = current.shopIds.toList(), offset = current.deals.size))
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

    fun toggleWaitlistFromDeal(data: DealBottomSheetData.DealDetailsData) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(data.gameId) == WaitlistToggleResult.NOT_LOGGED_IN) {
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

    internal sealed interface DealsUiEvent {
        data class ShareDeal(val text: String) : DealsUiEvent
        data object LoadMoreError : DealsUiEvent
        data object SignInRequired : DealsUiEvent
    }

    data class DealsScreenData(
        val status: Status = Status.LOADING,
        val sort: DealsSort = DealsSort.TopDiscount,
        val shopIds: ImmutableSet<Int> = persistentSetOf(),
        val deals: ImmutableList<Deal> = persistentListOf(),
        val appending: Boolean = false,
        val endReached: Boolean = false,
    ) {
        enum class Status { LOADING, ERROR, DATA }
    }
}
