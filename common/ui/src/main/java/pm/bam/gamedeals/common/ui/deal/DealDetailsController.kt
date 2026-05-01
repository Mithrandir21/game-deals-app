package pm.bam.gamedeals.common.ui.deal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.withMinimumDuration
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

class DealDetailsController(
    private val dealsRepository: DealsRepository,
    private val storesRepository: StoresRepository,
    private val logger: Logger,
) {
    private val _dealDetails = MutableStateFlow<DealBottomSheetData?>(null)
    val dealDetails: StateFlow<DealBottomSheetData?> = _dealDetails.asStateFlow()

    fun load(
        scope: CoroutineScope,
        dealId: String,
        dealStoreId: Int,
        dealTitle: String,
        dealPriceDenominated: String,
    ): Job = scope.launch {
        try {
            _dealDetails.emit(
                DealBottomSheetData.DealDetailsLoading(
                    store = storesRepository.getStore(dealStoreId),
                    gameName = dealTitle,
                    dealId = dealId,
                    gameSalesPriceDenominated = dealPriceDenominated,
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
                    cheaperStores = dealDetails.cheaperStores.map { storesRepository.getStore(it.storeID) to it },
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
                        gameSalesPriceDenominated = dealPriceDenominated,
                    )
                )
            } catch (inner: Throwable) {
                fatal(logger, inner)
                _dealDetails.emit(null)
            }
        }
    }

    fun dismiss(scope: CoroutineScope): Job = scope.launch { _dealDetails.emit(null) }
}
