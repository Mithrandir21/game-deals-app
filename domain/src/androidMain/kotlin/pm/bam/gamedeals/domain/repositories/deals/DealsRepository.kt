package pm.bam.gamedeals.domain.repositories.deals

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.repositories.deals.paging.DealsMediator
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

internal const val DEAL_PAGE_COUNT = 60
internal val DEALS_TTL_MILLIS = millisInHour * 8

class DealsRepository internal constructor(
    private val logger: Logger,
    private val dealsDao: DealsDao,
    private val domainDatabase: DomainDatabase,
    private val cheapsharkSource: CheapsharkSource,
    private val clock: Clock,
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
        val refreshed = storeDealsCache(storeId).refreshIfNeeded(force)
        debug(logger) { "Store($storeId) Deals refresh needed: $refreshed" }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun storeDealsCache(storeId: Int): CachedResource<Deal> = CachedResource(
        clock = clock,
        read = { dealsDao.getStoreDeals(storeId) },
        expiresAtMillis = { it.expires },
        refresh = {
            val expiresAt = clock.nowMillis() + DEALS_TTL_MILLIS
            domainDatabase.withTransaction {
                dealsDao.clearDealsForStore(storeId)
                cheapsharkSource.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = DEAL_PAGE_COUNT))
                    .map { it.copy(expires = expiresAt) }
                    .let { dealsDao.addDeals(*it.toTypedArray()) }
            }
        }
    )
}
