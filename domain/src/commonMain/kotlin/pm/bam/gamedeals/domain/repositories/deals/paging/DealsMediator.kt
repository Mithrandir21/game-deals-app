package pm.bam.gamedeals.domain.repositories.deals.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.LoadType.APPEND
import androidx.paging.LoadType.PREPEND
import androidx.paging.LoadType.REFRESH
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealPage
import pm.bam.gamedeals.domain.models.DealsSortBy
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.fatal

// Note: Not injected as created anew each time a store is required, as per guidelines and best practice.
@OptIn(ExperimentalPagingApi::class, ExperimentalSerializationApi::class)
internal class DealsMediator(
    private val domainDatabase: DomainDatabase,
    private val cheapsharkSource: CheapsharkSource,
    private val storeId: Int,
    private val pageSize: Int,
    private val logger: Logger
) : RemoteMediator<Int, Deal>() {

    private val dealsDao = domainDatabase.getDealsDao()
    private val pagingDao = domainDatabase.getPagingDao()

    override suspend fun load(loadType: LoadType, state: PagingState<Int, Deal>): MediatorResult {
        try {
            debug(logger) { "Loading: $loadType" }

            val pageNumber = when (loadType) {
                REFRESH -> 0
                PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                APPEND -> pagingDao.getStorePage(storeId)?.page ?: 0
            }

            debug(logger) { "pageNumber: $pageNumber - $loadType" }

            val deals = cheapsharkSource.fetchDealsForStore(
                query = SearchParameters(
                    storeID = storeId,
                    pageSize = pageSize,
                    pageNumber = pageNumber,
                    sortBy = DealsSortBy.DEALRATING
                )
            )


            debug(logger) { "deals size: ${deals.size} - ${deals.map { it.gameID }}" }

            domainDatabase.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    if (loadType == REFRESH) {
                        debug(logger) { "Domain Refreshing" }
                        dealsDao.clearDealsForStore(storeId)
                        pagingDao.clearStorePage(storeId)
                    }

                    val newDealPage = DealPage(storeId, pageNumber + 1)
                    debug(logger) { "New DealPage: $newDealPage" }

                    pagingDao.insert(newDealPage)
                    dealsDao.addDeals(*deals.toTypedArray())
                    debug(logger) { "Stored new Deals" }
                }
            }

            return MediatorResult.Success(endOfPaginationReached = deals.isEmpty())
        } catch (e: CancellationException) {
            // Honour structured concurrency: never swallow cancellation. Paging /
            // viewModelScope cancels mid-load on normal navigation, and reporting
            // that as `MediatorResult.Error` surfaces a fake load failure to the UI
            // and a fatal log entry to Crashlytics.
            throw e
        } catch (e: Exception) {
            fatal(logger, e)
            return MediatorResult.Error(e)
        }
    }
}
