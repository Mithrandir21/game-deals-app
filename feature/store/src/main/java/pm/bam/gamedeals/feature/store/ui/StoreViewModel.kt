package pm.bam.gamedeals.feature.store.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.withMinimumDuration
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import javax.inject.Inject

@HiltViewModel
internal class StoreViewModel @Inject constructor(
    private val logger: Logger,
    private val dealsRepository: DealsRepository,
    private val storesRepository: StoresRepository
) : ViewModel() {

    // We store and react to the StoreId changes so that only a single 'deals' flow can exists
    private val storeIdFlow = MutableStateFlow<Int?>(null)

    private val _storeDetails = MutableStateFlow<Store?>(null)
    val storeDetails: StateFlow<Store?> = _storeDetails.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _dealDetails = MutableStateFlow<DealBottomSheetData?>(null)
    val dealDetails: StateFlow<DealBottomSheetData?> = _dealDetails.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

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

    fun setStoreId(storeId: Int) = storeIdFlow.update { storeId }

    @OptIn(ExperimentalCoroutinesApi::class)
    val deals = storeIdFlow
        .filterNotNull() // Skip our initial null value
        .distinctUntilChanged() // Skip fetching if storeId is the same, like on orientation change
        .flatMapLatest { dealsRepository.getPagingStoreDeals(it) }
        // cachedIn() shares the paging state across multiple consumers of posts,
        // e.g. different generations of UI across rotation config change
        .cachedIn(viewModelScope)
        .catch { logger.fatalThrowable(it) }
        .logFlow(logger)

    fun loadDealDetails(
        dealId: String,
        dealStoreId: Int,
        dealTitle: String,
        dealPriceDenominated: String
    ) {
        viewModelScope.launch {
            try {
                _dealDetails.emit(
                    DealBottomSheetData.DealDetailsLoading(
                        store = storesRepository.getStore(dealStoreId),
                        gameName = dealTitle,
                        dealId = dealId,
                        gameSalesPriceDenominated = dealPriceDenominated
                    )
                )

                val data = withMinimumDuration(750L) {
                    val dealDetails = dealsRepository.getDeal(dealId)
                    val store = storesRepository.getStore(dealStoreId)

                    DealBottomSheetData.DealDetailsData(
                        store = store,
                        gameName = dealTitle,
                        dealId = dealId,
                        gameSalesPriceDenominated = dealPriceDenominated,
                        gameInfo = dealDetails.gameInfo,
                        cheapestPrice = dealDetails.cheapestPrice,
                        cheaperStores = dealDetails.cheaperStores.map { storesRepository.getStore(it.storeID) to it }
                    )
                }
                _dealDetails.emit(data)
            } catch (t: Throwable) {
                fatal(logger, t)
                try {
                    _dealDetails.emit(
                        DealBottomSheetData.DealDetailsError(
                            store = storesRepository.getStore(dealStoreId),
                            gameName = dealTitle,
                            dealId = dealId,
                            gameSalesPriceDenominated = dealPriceDenominated
                        )
                    )
                } catch (inner: Throwable) {
                    fatal(logger, inner)
                    dismissDealDetails()
                }
            }
        }
    }

    fun dismissDealDetails() {
        viewModelScope.launch {
            _dealDetails.emit(null)
        }
    }
}
