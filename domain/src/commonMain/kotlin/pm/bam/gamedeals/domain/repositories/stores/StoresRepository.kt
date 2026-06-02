package pm.bam.gamedeals.domain.repositories.stores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

internal val STORES_TTL_MILLIS = millisInHour * 8

interface StoresRepository {
    fun observeStores(): Flow<List<Store>>
    suspend fun getStore(storeId: Int): Store
    suspend fun refreshStores(force: Boolean = false)
}

internal class StoresRepositoryImpl(
    private val logger: Logger,
    private val storesDao: StoresDao,
    private val dealsSource: DealsSource,
    private val clock: Clock,
) : StoresRepository {

    private val cache = CachedResource(
        clock = clock,
        read = { storesDao.getAllStores() },
        expiresAtMillis = { it.expires },
        refresh = {
            val expiresAt = clock.nowMillis() + STORES_TTL_MILLIS
            dealsSource.fetchStores()
                .map { it.copy(expires = expiresAt) }
                .let { storesDao.addStores(*it.toTypedArray()) }
        }
    )

    override fun observeStores(): Flow<List<Store>> =
        storesDao.observeAllStores()
            .onStart { refreshStores() }

    override suspend fun getStore(storeId: Int): Store =
        storesDao.getStore(storeId)

    override suspend fun refreshStores(force: Boolean) {
        val refreshed = cache.refreshIfNeeded(force)
        debug(logger) { "Stores refresh needed: $refreshed" }
    }
}
