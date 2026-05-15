package pm.bam.gamedeals.feature.store.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.deal.DealDetailsController
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.info

internal class StoreViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val dealsRepository: DealsRepository,
    private val storesRepository: StoresRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {

    val favouriteIds: StateFlow<Set<Int>> = favouritesRepository.observeFavouriteIds()
        .onStart { emit(emptySet()) }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // We store and react to the StoreId changes so that only a single 'deals' flow can exists.
    private val storeIdFlow = MutableStateFlow(savedStateHandle.get<Int>("storeId"))

    // Retry counter: every increment re-runs the store-details load and re-subscribes the
    // hot 'observeStoreDeals' source. `combine` fires whenever either upstream emits, and
    // `flatMapLatest` cancels the in-flight inner flow before relaunching it.
    private val retryTrigger = MutableStateFlow(0)

    private val dealDetailsController = DealDetailsController(dealsRepository, storesRepository, logger)
    val dealDetails: StateFlow<DealBottomSheetData?> = dealDetailsController.dealDetails

    private val _events = MutableSharedFlow<StoreUiEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<StoreUiEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StoreScreenData> = combine(storeIdFlow, retryTrigger) { id, _ -> id }
        .flatMapLatest { id: Int? ->
            when (id) {
                null -> flowOf<StoreScreenData>(StoreScreenData.Error)
                else -> flowOf(id)
                    .map { storesRepository.getStore(id) }
                    .map<Store, StoreScreenData> { StoreScreenData.Data(it) }
                    .logFlow(logger)
                    .catch { emit(StoreScreenData.Error) }
                    .onStart { emit(StoreScreenData.Loading) }
            }
        }
        .logFlow(logger)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StoreScreenData.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val deals: StateFlow<ImmutableList<Deal>> = combine(storeIdFlow, retryTrigger) { id, attempt -> id to attempt }
        .filter { (id, _) -> id != null } // Skip our initial null value
        .distinctUntilChanged() // Skip refetching if neither the storeId nor the retry counter changed (e.g. orientation change)
        .flatMapLatest { (id, _) -> dealsRepository.observeStoreDeals(id!!) }
        .map { it.toImmutableList() }
        .logFlow(logger)
        .catch { emit(persistentListOf()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun loadDealDetails(dealId: String, dealStoreId: Int, dealGameId: Int, dealTitle: String, dealPriceDenominated: String) {
        dealDetailsController.load(viewModelScope, dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated)
    }

    fun toggleFavouriteFromDeal(data: DealBottomSheetData.DealDetailsData) {
        viewModelScope.launch {
            favouritesRepository.toggleFavourite(
                gameId = data.gameId,
                title = data.gameName,
                thumb = data.gameInfo.thumb,
            )
        }
    }

    fun dismissDealDetails() {
        dealDetailsController.dismiss(viewModelScope)
    }

    fun onShareDealClicked(data: DealBottomSheetData) {
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = data.gameSalesPriceDenominated,
            storeName = data.store.storeName,
            dealId = data.dealId,
        )
        info(logger, tag = "deal_shared") { "dealId=${data.dealId} store=${data.store.storeName}" }
        _events.tryEmit(StoreUiEvent.ShareDeal(text))
    }

    internal sealed interface StoreUiEvent {
        data class ShareDeal(val text: String) : StoreUiEvent
    }

    sealed class StoreScreenData {
        data object Loading : StoreScreenData()
        data object Error : StoreScreenData()
        data class Data(val store: Store) : StoreScreenData()
    }
}
