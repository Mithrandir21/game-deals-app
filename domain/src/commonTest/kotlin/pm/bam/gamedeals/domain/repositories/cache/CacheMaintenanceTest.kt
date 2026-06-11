package pm.bam.gamedeals.domain.repositories.cache

import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.BundlesCacheDao
import pm.bam.gamedeals.domain.db.dao.DealDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GameDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GameIdMappingDao
import pm.bam.gamedeals.domain.db.dao.PriceHistoryCacheDao
import pm.bam.gamedeals.domain.db.dao.StatsRankingsCacheDao
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheMaintenanceTest {

    private val logger: Logger = TestingLoggingListener()
    private val dealDetailsCacheDao: DealDetailsCacheDao = mock(MockMode.autoUnit)
    private val gameDetailsCacheDao: GameDetailsCacheDao = mock(MockMode.autoUnit)
    private val priceHistoryCacheDao: PriceHistoryCacheDao = mock(MockMode.autoUnit)
    private val bundlesCacheDao: BundlesCacheDao = mock(MockMode.autoUnit)
    private val statsRankingsCacheDao: StatsRankingsCacheDao = mock(MockMode.autoUnit)
    private val gameIdMappingDao: GameIdMappingDao = mock(MockMode.autoUnit)

    private val now = 1_000_000_000L
    private val clock = Clock { now }

    private fun maintenance(storage: Storage) = CacheMaintenanceImpl(
        storage, clock, dealDetailsCacheDao, gameDetailsCacheDao, priceHistoryCacheDao,
        bundlesCacheDao, statsRankingsCacheDao, gameIdMappingDao, logger,
    )

    @Test
    fun schema_version_changed_clears_format_versioned_caches_records_version_and_skips_sweep() = runTest {
        val storage = FakeStorage() // no stored version → format bump (cold install or app update)

        maintenance(storage).runStartupMaintenance()

        verifySuspend(exactly(1)) { dealDetailsCacheDao.clear() }
        verifySuspend(exactly(1)) { gameDetailsCacheDao.clear() }
        verifySuspend(exactly(1)) { priceHistoryCacheDao.clear() }
        verifySuspend(exactly(1)) { bundlesCacheDao.clear() }
        verifySuspend(exactly(1)) { statsRankingsCacheDao.clear() }
        verifySuspend(exactly(1)) { gameIdMappingDao.clear() }
        assertEquals(CACHE_SCHEMA_VERSION, storage.saved[CACHE_SCHEMA_VERSION_KEY])
        // A full wipe leaves nothing to sweep.
        verifySuspend(exactly(0)) { dealDetailsCacheDao.deleteExpiredBefore(any()) }
        verifySuspend(exactly(0)) { priceHistoryCacheDao.deleteFetchedBefore(any()) }
    }

    @Test
    fun schema_version_unchanged_runs_the_eviction_sweep_with_the_right_thresholds() = runTest {
        val storage = FakeStorage(stored = mapOf(CACHE_SCHEMA_VERSION_KEY to CACHE_SCHEMA_VERSION))

        maintenance(storage).runStartupMaintenance()

        // Version matches → no clear, just the age-based sweep.
        verifySuspend(exactly(0)) { dealDetailsCacheDao.clear() }

        val grace = now - CACHE_SWEEP_EXPIRY_GRACE_MILLIS
        verifySuspend(exactly(1)) { dealDetailsCacheDao.deleteExpiredBefore(grace) }
        verifySuspend(exactly(1)) { gameDetailsCacheDao.deleteExpiredBefore(grace) }
        verifySuspend(exactly(1)) { bundlesCacheDao.deleteExpiredBefore(grace) }
        verifySuspend(exactly(1)) { statsRankingsCacheDao.deleteExpiredBefore(grace) }
        // Identity mappings: no serve-stale grace (re-lookup is cheap).
        verifySuspend(exactly(1)) { gameIdMappingDao.deleteExpiredBefore(now) }
        // Price history: 30-day retention on the last refresh.
        verifySuspend(exactly(1)) { priceHistoryCacheDao.deleteFetchedBefore(now - PRICE_HISTORY_RETENTION_MILLIS) }
    }
}

/** Minimal in-memory [Storage]; only the primitive get/save the maintenance uses are exercised. */
private class FakeStorage(stored: Map<String, Any> = emptyMap()) : Storage {
    val saved = stored.toMutableMap()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        (saved[storageKey] as T?) ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        (saved[storageKey] as T?) ?: defaultValue ?: error("no value for $storageKey")

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        saved[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = saved.containsKey(storageKey)
    override suspend fun remove(storageKey: String): Boolean = saved.remove(storageKey) != null
}
