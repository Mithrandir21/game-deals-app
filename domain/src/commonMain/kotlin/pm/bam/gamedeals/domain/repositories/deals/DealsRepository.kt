package pm.bam.gamedeals.domain.repositories.deals

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
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
     * A sorted/filtered page of all-stores deals for the Deals tab (#219 Phase 4). Fetched fresh from
     * the source per call — deliberately **not** Room-cached: results are paged, filtered and
     * region-sensitive, so persisting them in the store-scoped [Deal] table would be incorrect (and
     * would collide with the store-deals cache). Callers drive offset-based load-more via [DealsQuery].
     */
    suspend fun getDeals(query: DealsQuery): List<Deal>

    /**
     * Drops every cached deal across all regions. **No longer wired to region change** — region
     * keying (D5 / Phase 2) makes the previous #212 clear-on-change unnecessary, since reads filter
     * by the active country and a refetch overwrites the stamped rows. Retained only as an optional
     * maintenance hook.
     */
    suspend fun clearCachedDeals()
}

internal class DealsRepositoryImpl(
    private val logger: Logger,
    private val dealsDao: DealsDao,
    private val domainDatabase: DomainDatabase,
    private val dealsSource: DealsSource,
    private val clock: Clock,
    private val regionRepository: RegionRepository,
) : DealsRepository {

    override fun observeAllDeals(): Flow<List<Deal>> =
        dealsDao.observeAllDeals()

    /**
     * Live stream of deals for [storeId] in the active region. The country is resolved once at
     * subscribe time; the Store screen re-subscribes when the region changes (#212), so the new
     * region's rows are read (or fetched on a miss). Triggers a TTL-aware refresh on subscribe;
     * subsequent emissions follow Room's change-tracking as the cache row turns over.
     */
    override fun observeStoreDeals(storeId: Int): Flow<List<Deal>> =
        flow {
            val country = regionRepository.getSelectedCountryCode()
            emitAll(
                dealsDao.observeStoreDeals(storeId, country)
                    .onStart { refreshDeals(storeId) }
            )
        }


    override suspend fun getStoreDeals(storeId: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId, regionRepository.getSelectedCountryCode())
    }

    override suspend fun getStoreDeals(storeId: Int, limit: Int): List<Deal> {
        refreshDeals(storeId)
        return dealsDao.getStoreDeals(storeId, regionRepository.getSelectedCountryCode(), limit)
    }

    override suspend fun getDeal(dealId: String): DealDetails =
        dealsSource.fetchDealDetails(dealId)

    override suspend fun getDeals(query: DealsQuery): List<Deal> =
        dealsSource.fetchDeals(query)

    override suspend fun clearCachedDeals() = dealsDao.clearAllDeals()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun refreshDeals(storeId: Int, force: Boolean) {
        val country = regionRepository.getSelectedCountryCode()
        val refreshed = storeDealsCache(storeId, country).refreshIfNeeded(force)
        debug(logger) { "Store($storeId) Deals[$country] refresh needed: $refreshed" }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun storeDealsCache(storeId: Int, country: String): CachedResource<Deal> = CachedResource(
        clock = clock,
        read = { dealsDao.getStoreDeals(storeId, country) },
        expiresAtMillis = { it.expires },
        refresh = {
            val expiresAt = clock.nowMillis() + DEALS_TTL_MILLIS
            domainDatabase.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    dealsDao.clearDealsForStore(storeId, country)
                    dealsSource.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = DEAL_PAGE_COUNT))
                        .map { it.copy(expires = expiresAt, country = country) }
                        .let { dealsDao.addDeals(*it.toTypedArray()) }
                }
            }
        }
    )
}
