package pm.bam.gamedeals.domain.repositories.stores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.domain.utils.millisInMinute
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.warn

internal const val STORES_TTL_MILLIS = millisInHour * 8

interface StoresRepository {
    fun observeStores(): Flow<List<Store>>

    /**
     * The cached [Store] for [storeId], or `null` when the shop can't be resolved even after a
     * refresh. Callers must degrade rather than treat a miss as a failure — a deal from an
     * unknown shop shouldn't take down a screen that rendered everything else fine.
     */
    suspend fun getStore(storeId: Int): Store?
    suspend fun refreshStores(force: Boolean = false)
}

/**
 * How long to wait before a second cache-miss is allowed to trigger another forced store refresh.
 * Bounds the network cost when a whole screenful of deals references a shop ITAD genuinely doesn't
 * return (one fetch, not one per deal).
 */
internal const val STORE_MISS_REFRESH_COOLDOWN_MILLIS = millisInMinute

internal class StoresRepositoryImpl(
    private val logger: Logger,
    private val storesDao: StoresDao,
    private val dealsSource: DealsSource,
    private val clock: Clock,
) : StoresRepository {

    private val missRefreshMutex = Mutex()
    private var lastMissRefreshMillis = 0L

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

    /**
     * Reads the cached shop, and on a miss forces one store refresh before re-reading — a deal can
     * name a shop the 8h-TTL store cache predates, and the previous non-null contract turned that
     * into a thrown `IllegalStateException` that failed whole screens (Sentry KOTLIN-E).
     *
     * The refresh is rate-limited by [STORE_MISS_REFRESH_COOLDOWN_MILLIS] and serialised by
     * [missRefreshMutex] so mapping a screenful of deals costs at most one fetch. A refresh failure
     * never propagates — the caller gets `null` either way and degrades.
     */
    override suspend fun getStore(storeId: Int): Store? =
        storesDao.getStore(storeId) ?: refreshOnMiss().let { storesDao.getStore(storeId) }

    private suspend fun refreshOnMiss() = missRefreshMutex.withLock {
        val now = clock.nowMillis()
        if (now - lastMissRefreshMillis < STORE_MISS_REFRESH_COOLDOWN_MILLIS) return@withLock
        lastMissRefreshMillis = now
        debug(logger) { "Store cache miss — forcing a store refresh" }
        // `CachedResource` serves stale on error only while something is cached — with an empty table
        // it rethrows, which is exactly the case a miss is most likely to hit (fresh install, cleared
        // data, a first launch with no signal). Letting that escape would put KOTLIN-E straight back:
        // the caller asked for one shop, not for the refresh to have succeeded.
        runCatching { cache.refreshIfNeeded(force = true) }
            .onFailure { warn(logger, it) { "Forced store refresh after a cache miss failed" } }
    }

    override suspend fun refreshStores(force: Boolean) {
        val refreshed = cache.refreshIfNeeded(force)
        debug(logger) { "Stores refresh needed: $refreshed" }
    }
}
