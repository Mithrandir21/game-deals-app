package pm.bam.gamedeals.domain.repositories.bundles

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.BundlesCacheEntry
import pm.bam.gamedeals.domain.db.dao.BundlesCacheDao
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundlesRepositoryTest {

    private val dealsSource: DealsSource = mock(MockMode.autoUnit)
    private val bundlesCacheDao: BundlesCacheDao = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private val clock = Clock { now }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val country = "US"
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        everySuspend { getSelectedCountryCode() } returns country
    }

    private val repository = BundlesRepositoryImpl(dealsSource, clock, regionRepository, bundlesCacheDao, json)

    @Test
    fun get_bundles_cold_cache_fetches_caches_and_returns() = runTest {
        val bundles = listOf(bundle(1), bundle(2))
        everySuspend { bundlesCacheDao.get(country) } returns null
        everySuspend { dealsSource.fetchBundles() } returns bundles

        assertEquals(bundles, repository.getBundles())

        verifySuspend(exactly(1)) { dealsSource.fetchBundles() }
        verifySuspend(exactly(1)) { bundlesCacheDao.upsert(any()) }
    }

    @Test
    fun get_bundles_fresh_cache_decodes_without_fetch() = runTest {
        val bundles = listOf(bundle(1))
        everySuspend { bundlesCacheDao.get(country) } returns entryFor(bundles, expires = now + 10_000)

        assertEquals(bundles, repository.getBundles())

        verifySuspend(exactly(0)) { dealsSource.fetchBundles() }
        verifySuspend(exactly(0)) { bundlesCacheDao.upsert(any()) }
    }

    @Test
    fun get_bundles_stale_cache_refresh_failure_serves_stale() = runTest {
        val bundles = listOf(bundle(1))
        everySuspend { bundlesCacheDao.get(country) } returns entryFor(bundles, expires = now - 1)
        everySuspend { dealsSource.fetchBundles() } throws Exception("boom")

        // Warm cache + failed refresh: fall back to the cached list (D7), don't throw, don't upsert.
        assertEquals(bundles, repository.getBundles())
        verifySuspend(exactly(0)) { bundlesCacheDao.upsert(any()) }
    }

    @Test
    fun get_bundle_returns_the_matching_id_from_the_cached_list() = runTest {
        everySuspend { bundlesCacheDao.get(country) } returns null
        everySuspend { dealsSource.fetchBundles() } returns listOf(bundle(1), bundle(2))

        assertEquals(2, repository.getBundle(2)?.id)
    }

    @Test
    fun get_bundle_returns_null_when_absent() = runTest {
        everySuspend { bundlesCacheDao.get(country) } returns null
        everySuspend { dealsSource.fetchBundles() } returns listOf(bundle(1))

        assertNull(repository.getBundle(99))
    }

    private fun entryFor(bundles: List<Bundle>, expires: Long) = BundlesCacheEntry(
        country = country,
        json = json.encodeToString(ListSerializer(Bundle.serializer()), bundles),
        fetchedAt = expires - 1,
        expires = expires,
    )

    private fun bundle(id: Int) = Bundle(
        id = id,
        title = "Bundle $id",
        storeName = "Store",
        url = "https://example.com/$id",
        expiryEpochMs = null,
        gameCount = 0,
        priceDenominated = null,
        games = persistentListOf(),
    )
}
