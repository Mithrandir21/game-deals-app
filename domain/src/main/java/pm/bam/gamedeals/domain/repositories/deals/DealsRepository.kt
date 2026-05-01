package pm.bam.gamedeals.domain.repositories.deals

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.repositories.deals.paging.DealsMediator
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.verbose
import javax.inject.Inject
import javax.inject.Singleton

internal const val DEAL_PAGE_COUNT = 60

@Singleton
class DealsRepository @Inject internal constructor(
    private val logger: Logger,
    private val dealsDao: DealsDao,
    private val domainDatabase: DomainDatabase,
    private val cheapsharkSource: CheapsharkSource
) {

    fun observeAllDeals(): Flow<List<Deal>> =
        dealsDao.observeAllDeals()

    @OptIn(ExperimentalPagingApi::class)
    fun getPagingStoreDeals(storeId: Int): Flow<PagingData<Deal>> = Pager(
        config = PagingConfig(
            pageSize = DEAL_PAGE_COUNT,
            enablePlaceholders = false
        ),
        remoteMediator = DealsMediator(domainDatabase, cheapsharkSource, storeId, DEAL_PAGE_COUNT, logger)
    ) {
        dealsDao.getPagingStoreDeals(storeId)
    }.flow


    suspend fun getStoreDeals(storeId: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId)
    }

    suspend fun getStoreDeals(storeId: Int, limit: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId, limit)
    }

    suspend fun getDeal(dealId: String): DealDetails =
        cheapsharkSource.fetchDealDetails(dealId)

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun refreshDeals(storeId: Int, force: Boolean = false) {
        val refresh = force || refreshNeeded(storeId)

        debug(logger) { "Store Deals refresh needed: $refresh" }

        if (refresh) {
            domainDatabase.withTransaction {
                dealsDao.clearDealsForStore(storeId)
                cheapsharkSource.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = DEAL_PAGE_COUNT))
                    .let { dealsDao.addDeals(*it.toTypedArray()) }
            }
        }
    }


    private suspend fun refreshNeeded(storeId: Int): Boolean =
        dealsDao.getStoreDeals(storeId)
            .let { deals -> deals.isEmpty() || deals.any { it.expires < System.currentTimeMillis() } }
            .apply { verbose(logger) { "Store($storeId) Deals Expiration logic returned: $this" } }
}
