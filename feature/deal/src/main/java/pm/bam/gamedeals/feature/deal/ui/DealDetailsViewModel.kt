package pm.bam.gamedeals.feature.deal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.mapDelayAtLeast
import pm.bam.gamedeals.common.onError
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

class DealDetailsViewModel(
    private val logger: Logger,
    private val dealsRepository: DealsRepository,
    private val storesRepository: StoresRepository,
) : ViewModel() {


    private val _dealDetails = MutableStateFlow<DealBottomSheetData?>(null)
    val dealDealDetails: StateFlow<DealBottomSheetData?> = _dealDetails.asStateFlow()

    private var loadJob: Job? = null

    fun loadDealDetails(
        dealId: String,
        dealStoreId: Int,
        dealTitle: String,
        dealPriceDenominated: String
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            flowOf(true)
                .mapDelayAtLeast(750) {
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
                .onStart<DealBottomSheetData> {
                    DealBottomSheetData.DealDetailsLoading(
                        store = storesRepository.getStore(dealStoreId),
                        gameName = dealTitle,
                        dealId = dealId,
                        gameSalesPriceDenominated = dealPriceDenominated
                    ).let { emit(it) }
                }
                .logFlow(logger)
                .catch {
                    DealBottomSheetData.DealDetailsError(
                        store = storesRepository.getStore(dealStoreId),
                        gameName = dealTitle,
                        dealId = dealId,
                        gameSalesPriceDenominated = dealPriceDenominated
                    ).let { emit(it) }
                }
                // Exceptions caused by the above "catch" block are caught here
                .onError { fatal(logger, it) }
                .catch { dismissDealDetails() }
                .collect { _dealDetails.emit(it) }
        }
    }

    fun dismissDealDetails() {
        viewModelScope.launch {
            _dealDetails.emit(null)
        }
    }

}