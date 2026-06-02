package pm.bam.gamedeals.common.ui.deal

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val dealDetails: StateFlow<DealBottomSheetData?>
        field = MutableStateFlow<DealBottomSheetData?>(null)

    private var loadJob: Job? = null

    fun load(
        scope: CoroutineScope,
        dealId: String,
        dealStoreId: Int,
        dealGameId: String,
        dealTitle: String,
        dealPriceDenominated: String,
        dealUrl: String,
    ) {
        loadJob?.cancel()
        scope.launch {
            try {
                dealDetails.emit(
                    DealBottomSheetData.DealDetailsLoading(
                        store = storesRepository.getStore(dealStoreId),
                        gameId = dealGameId,
                        gameName = dealTitle,
                        dealId = dealId,
                        dealUrl = dealUrl,
                        gameSalesPriceDenominated = dealPriceDenominated,
                    )
                )

                val data = withMinimumDuration(750L) {
                    val dealDetails = dealsRepository.getDeal(dealId)
                    val store = storesRepository.getStore(dealStoreId)

                    DealBottomSheetData.DealDetailsData(
                        store = store,
                        gameId = dealGameId,
                        gameName = dealTitle,
                        dealId = dealId,
                        dealUrl = dealUrl,
                        gameSalesPriceDenominated = dealPriceDenominated,
                        gameInfo = dealDetails.gameInfo,
                        cheapestPrice = dealDetails.cheapestPrice,
                        cheaperStores = dealDetails.cheaperStores.map { StoreCheaperStorePair(store = storesRepository.getStore(it.storeID), cheaperStore = it) }.toImmutableList(),
                    )
                }
                dealDetails.emit(data)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                fatal(logger, t)
                try {
                    dealDetails.emit(
                        DealBottomSheetData.DealDetailsError(
                            store = storesRepository.getStore(dealStoreId),
                            gameId = dealGameId,
                            gameName = dealTitle,
                            dealId = dealId,
                            dealUrl = dealUrl,
                            gameSalesPriceDenominated = dealPriceDenominated,
                        )
                    )
                } catch (inner: CancellationException) {
                    throw inner
                } catch (inner: Throwable) {
                    fatal(logger, inner)
                    dealDetails.emit(null)
                }
            }
        }.also { loadJob = it }
    }

    fun dismiss(scope: CoroutineScope) {
        loadJob?.cancel()
        scope.launch { dealDetails.emit(null) }
    }
}
