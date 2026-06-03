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
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

internal const val DEAL_PAGE_COUNT = 60
internal val DEALS_TTL_MILLIS = millisInHour * 8

interface DealsRepository {
    fun observeAllDeals(): Flow<List<Deal>>
    fun observeStoreDeals(storeId: Int): Flow<List<Deal>>
    suspend fun getStoreDeals(storeId: Int): List<Deal>
    suspend fun getStoreDeals(storeId: Int, limit: Int): List<Deal>
    suspend fun getDeal(dealId: String): DealDetails
    suspend fun refreshDeals(storeId: Int, force: Boolean = false)

    /**
     * Drops every cached deal so the next observe/refresh re-fetches. Used when the region changes
     * (#212): cached rows hold the previous region's prices/currency and must be invalidated.
     */
    suspend fun clearCachedDeals()
}

internal class DealsRepositoryImpl(
    private val logger: Logger,
    private val dealsDao: DealsDao,
    private val domainDatabase: DomainDatabase,
    private val dealsSource: DealsSource,
    private val clock: Clock,
) : DealsRepository {

    override fun observeAllDeals(): Flow<List<Deal>> =
        dealsDao.observeAllDeals()

    /**
     * Live stream of deals for [storeId]. Triggers a TTL-aware refresh against
     * CheapShark on subscribe; subsequent emissions follow Room's change-tracking
     * as the cache row turns over. Pagination is intentionally out of scope until
     * AndroidX ships a multiplatform `paging-compose`.
     */
    override fun observeStoreDeals(storeId: Int): Flow<List<Deal>> =
        dealsDao.observeStoreDeals(storeId)
            .onStart { refreshDeals(storeId) }


    override suspend fun getStoreDeals(storeId: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId)
    }

    override suspend fun getStoreDeals(storeId: Int, limit: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId, limit)
    }

    override suspend fun getDeal(dealId: String): DealDetails =
        dealsSource.fetchDealDetails(dealId)

    override suspend fun clearCachedDeals() = dealsDao.clearAllDeals()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun refreshDeals(storeId: Int, force: Boolean) {
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
                    dealsSource.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = DEAL_PAGE_COUNT))
                        .map { it.copy(expires = expiresAt) }
                        .let { dealsDao.addDeals(*it.toTypedArray()) }
                }
            }
        }
    )
}
