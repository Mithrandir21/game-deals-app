package pm.bam.gamedeals.feature.store.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.deal.DealDetailsController
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import javax.inject.Inject

@HiltViewModel
internal class StoreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val dealsRepository: DealsRepository,
    private val storesRepository: StoresRepository
) : ViewModel() {

    // We store and react to the StoreId changes so that only a single 'deals' flow can exists.
    // Seeded from the typed [Destination.Store] route. nav-compose populates SavedStateHandle
    // with the @Serializable property name as key for primitive args, so reading by key works
    // here without going through the toRoute<>() Bundle round-trip (keeps unit tests JVM-only).
    private val storeIdFlow = MutableStateFlow<Int?>(savedStateHandle.get<Int>("storeId")!!)

    private val _storeDetails = MutableStateFlow<Store?>(null)
    val storeDetails: StateFlow<Store?> = _storeDetails.asStateFlow()

    private val dealDetailsController = DealDetailsController(dealsRepository, storesRepository, logger)
    val dealDetails: StateFlow<DealBottomSheetData?> = dealDetailsController.dealDetails

    init {
        viewModelScope.launch {
            storeIdFlow
                .filterNotNull() // Skip our initial null value
                .distinctUntilChanged() // Skip fetching if storeId is the same, like on orientation change
                .map { storesRepository.getStore(it) }
                .logFlow(logger)
                .catch { logger.fatalThrowable(it) }
                .collect { _storeDetails.emit(it) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val deals = storeIdFlow
        .filterNotNull() // Skip our initial null value
        .distinctUntilChanged() // Skip fetching if storeId is the same, like on orientation change
        .flatMapLatest { dealsRepository.getPagingStoreDeals(it) }
        // cachedIn() shares the paging state across multiple consumers of posts,
        // e.g. different generations of UI across rotation config change
        .cachedIn(viewModelScope)
        // No .catch here: Paging surfaces load errors via LoadState.Error so the UI can
        // recover via retry(). A .catch after .cachedIn would swallow construction-time
        // exceptions and leave LazyPagingItems stuck on the last-cached PagingData.
        .logFlow(logger)

    fun loadDealDetails(dealId: String, dealStoreId: Int, dealTitle: String, dealPriceDenominated: String) {
        dealDetailsController.load(viewModelScope, dealId, dealStoreId, dealTitle, dealPriceDenominated)
    }

    fun dismissDealDetails() {
        dealDetailsController.dismiss(viewModelScope)
    }
}
