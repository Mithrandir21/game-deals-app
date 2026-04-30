package pm.bam.gamedeals.domain.repositories.deals

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.toDeal
import pm.bam.gamedeals.domain.models.toDealDetails
import pm.bam.gamedeals.domain.repositories.deals.paging.DealsMediator
import pm.bam.gamedeals.domain.transformations.CurrencyTransformation
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.verbose
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSource
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import javax.inject.Inject

internal const val DEAL_PAGE_COUNT = 60

internal class DealsRepositoryImpl @Inject constructor(
    private val logger: Logger,
    private val dealsDao: DealsDao,
    private val domainDatabase: DomainDatabase,
    private val cheapsharkSource: CheapsharkSource,
    private val currencyTransformation: CurrencyTransformation,
    private val datetimeFormatter: DateTimeFormatter
) : DealsRepository {

    override fun observeAllDeals(): Flow<List<Deal>> =
        dealsDao.observeAllDeals()

    @OptIn(ExperimentalPagingApi::class)
    override fun getPagingStoreDeals(storeId: Int): Flow<PagingData<Deal>> = Pager(
        config = PagingConfig(
            pageSize = DEAL_PAGE_COUNT,
            enablePlaceholders = false
        ),
        remoteMediator = DealsMediator(domainDatabase, cheapsharkSource, currencyTransformation, storeId, DEAL_PAGE_COUNT, logger)
    ) {
        dealsDao.getPagingStoreDeals(storeId)
    }.flow


    override suspend fun getStoreDeals(storeId: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId)
    }

    override suspend fun getStoreDeals(storeId: Int, limit: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId, limit)
    }

    override suspend fun getDeal(dealId: String): DealDetails =
        cheapsharkSource.fetchDealDetails(dealId).toDealDetails(currencyTransformation, datetimeFormatter)

    override suspend fun refreshDeals(storeId: Int, force: Boolean) {
        val refresh = force || refreshNeeded(storeId)

        debug(logger) { "Store Deals refresh needed: $refresh" }

        if (refresh) {
            domainDatabase.withTransaction {
                dealsDao.clearDealsForStore(storeId)
                cheapsharkSource.fetchDealsForStore(RemoteDealsQuery(storeID = storeId, pageSize = DEAL_PAGE_COUNT))
                    .map { remoteDeal -> remoteDeal.toDeal(currencyTransformation) }
                    .let { dealsDao.addDeals(*it.toTypedArray()) }
            }
        }
    }


    private suspend fun refreshNeeded(storeId: Int): Boolean =
        dealsDao.getStoreDeals(storeId)
            .let { deals -> deals.isEmpty() || deals.any { it.expires < System.currentTimeMillis() } }
            .apply { verbose(logger) { "Store($storeId) Deals Expiration logic returned: $this" } }
}
