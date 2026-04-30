package pm.bam.gamedeals.domain.repositories.deals.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.LoadType.APPEND
import androidx.paging.LoadType.PREPEND
import androidx.paging.LoadType.REFRESH
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealPage
import pm.bam.gamedeals.domain.models.toDeal
import pm.bam.gamedeals.domain.transformations.CurrencyTransformation
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSource
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsSortBy

// Note: Not injected as created anew each time a store is required, as per guidelines and best practice.
@OptIn(ExperimentalPagingApi::class)
internal class DealsMediator(
    private val domainDatabase: DomainDatabase,
    private val cheapsharkSource: CheapsharkSource,
    private val currencyTransformation: CurrencyTransformation,
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
                query = RemoteDealsQuery(
                    storeID = storeId,
                    pageSize = pageSize,
                    pageNumber = pageNumber,
                    sortBy = RemoteDealsSortBy.DEALRATING
                )
            ).map { it.toDeal(currencyTransformation) }


            debug(logger) { "deals size: ${deals.size} - ${deals.map { it.gameID }}" }

            domainDatabase.withTransaction {
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

            return MediatorResult.Success(endOfPaginationReached = deals.isEmpty())
        } catch (e: Exception) {
            fatal(logger, e)
            return MediatorResult.Error(e)
        }
    }
}
