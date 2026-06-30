package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.serialization.builtins.serializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.BundlesCacheDao
import pm.bam.gamedeals.domain.db.dao.DealDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GameDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GameIdMappingDao
import pm.bam.gamedeals.domain.db.dao.PriceHistoryCacheDao
import pm.bam.gamedeals.domain.db.dao.StatsRankingsCacheDao
import pm.bam.gamedeals.domain.utils.millisInDay
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

/** Current semantic cache-format version; bump to force a one-time clear of the format-versioned caches. */
internal const val CACHE_SCHEMA_VERSION = 2
internal const val CACHE_SCHEMA_VERSION_KEY = "cache_schema_version"

/** Keep rows for a grace window past TTL expiry so serve-stale-on-error (D7) still has something to fall back on. */
internal const val CACHE_SWEEP_EXPIRY_GRACE_MILLIS = millisInDay * 7

/** Price-history retention — drop a game's series if not refreshed within this window (D9). */
internal const val PRICE_HISTORY_RETENTION_MILLIS = millisInDay * 30

/**
 * Startup cache maintenance (ITAD caching strategy, Phase 8, #269). Runs once per cold start (fire-and-forget
 * off the app's background scope):
 *
 * 1. **`cacheSchemaVersion` guard** — when the stored version differs from [CACHE_SCHEMA_VERSION] (a semantic
 *    cache-format change across an app update that a Room column migration can't express), wipe the
 *    format-versioned caches and record the new version. This is also D3's escape hatch for ITAD UUID merges.
 * 2. **Eviction sweep** — age-based, per-table. Never a blanket `DELETE WHERE expires < now`: a grace window
 *    past expiry is kept so the serve-stale rows D7 relies on survive. Bounds growth to recently-browsed games.
 *
 * Only the new ITAD read-caches are touched; user data (Waitlist/Collection) and migration-covered column
 * tables (Deal/Game/…) are left alone.
 */
interface CacheMaintenance {
    suspend fun runStartupMaintenance()
}

internal class CacheMaintenanceImpl(
    private val storage: Storage,
    private val clock: Clock,
    private val dealDetailsCacheDao: DealDetailsCacheDao,
    private val gameDetailsCacheDao: GameDetailsCacheDao,
    private val priceHistoryCacheDao: PriceHistoryCacheDao,
    private val bundlesCacheDao: BundlesCacheDao,
    private val statsRankingsCacheDao: StatsRankingsCacheDao,
    private val gameIdMappingDao: GameIdMappingDao,
    private val logger: Logger,
) : CacheMaintenance {

    override suspend fun runStartupMaintenance() {
        // A schema-version bump already wipes everything, so there is nothing left to sweep.
        if (!clearIfSchemaChanged()) sweepExpired()
    }

    /** @return true when the stored version differed and the format-versioned caches were cleared. */
    private suspend fun clearIfSchemaChanged(): Boolean {
        val stored = runCatching { storage.getNullable(CACHE_SCHEMA_VERSION_KEY, Int.serializer()) }.getOrNull()
        if (stored == CACHE_SCHEMA_VERSION) return false

        dealDetailsCacheDao.clear()
        gameDetailsCacheDao.clear()
        priceHistoryCacheDao.clear()
        bundlesCacheDao.clear()
        statsRankingsCacheDao.clear()
        gameIdMappingDao.clear()
        storage.save(CACHE_SCHEMA_VERSION_KEY, CACHE_SCHEMA_VERSION, Int.serializer())
        debug(logger) { "Cache schema $stored -> $CACHE_SCHEMA_VERSION: cleared format-versioned caches" }
        return true
    }

    private suspend fun sweepExpired() {
        val now = clock.nowMillis()
        val expiryGrace = now - CACHE_SWEEP_EXPIRY_GRACE_MILLIS
        dealDetailsCacheDao.deleteExpiredBefore(expiryGrace)
        gameDetailsCacheDao.deleteExpiredBefore(expiryGrace)
        bundlesCacheDao.deleteExpiredBefore(expiryGrace)
        statsRankingsCacheDao.deleteExpiredBefore(expiryGrace)
        // Identity mappings self-heal on re-lookup, so no serve-stale grace is needed.
        gameIdMappingDao.deleteExpiredBefore(now)
        priceHistoryCacheDao.deleteFetchedBefore(now - PRICE_HISTORY_RETENTION_MILLIS)
        debug(logger) { "Cache eviction sweep complete" }
    }
}
