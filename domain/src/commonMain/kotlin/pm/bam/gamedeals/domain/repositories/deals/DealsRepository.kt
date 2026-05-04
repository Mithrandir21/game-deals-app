package pm.bam.gamedeals.domain.repositories.deals

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
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

    /**
     * Live stream of deals for [storeId]. Triggers a TTL-aware refresh against
     * CheapShark on subscribe; subsequent emissions follow Room's change-tracking
     * as the cache row turns over. Replaces the previous Paging 3 `Pager` +
     * `RemoteMediator` flow that didn't have a working multiplatform compose
     * binding (paging-compose is Android-only; Cash App's
     * `paging-compose-common` shim ran into runtime klib symbol mismatches on
     * Native). Pagination can be reintroduced when AndroidX ships
     * multiplatform paging-compose.
     */
    fun observeStoreDeals(storeId: Int): Flow<List<Deal>> =
        dealsDao.observeStoreDeals(storeId)
            .onStart { refreshDeals(storeId) }


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
            domainDatabase.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    dealsDao.clearDealsForStore(storeId)
                    cheapsharkSource.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = DEAL_PAGE_COUNT))
                        .map { it.copy(expires = expiresAt) }
                        .let { dealsDao.addDeals(*it.toTypedArray()) }
                }
            }
        }
    )
}
