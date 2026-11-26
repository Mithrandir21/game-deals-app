package pm.bam.gamedeals.domain.repositories.stores

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoresRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val storesDao: StoresDao = mock(MockMode.autoUnit)
    private val dealsSource: DealsSource = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private var currentMillis = now
    private val clock = Clock { currentMillis }

    private val impl = StoresRepositoryImpl(logger, storesDao, dealsSource, clock)

    @Test
    fun observe_stores_fresh_cache_does_not_refresh() = runTest {
        val fresh = store(expires = now + 10_000)

        every { storesDao.observeAllStores() } returns flowOf(emptyList())
        everySuspend { storesDao.getAllStores() } returns listOf(fresh)

        val result = impl.observeStores().first()
        assertTrue(result.isEmpty())

        verifySuspend(exactly(0)) { dealsSource.fetchStores() }
        verify(exactly(1)) { storesDao.observeAllStores() }
        verifySuspend(exactly(1)) { storesDao.getAllStores() }
        verifySuspend(exactly(0)) { storesDao.addStores(*any()) }
    }

    @Test
    fun refresh_stores_unforced_expired_entry_fetches_and_stamps_expires() = runTest {
        val expired = store(storeID = 1, expires = now - 1)
        val fetched = store(storeID = 99)
        val expectedExpires = now + millisInHour * 8

        everySuspend { dealsSource.fetchStores() } returns listOf(fetched)
        everySuspend { storesDao.getAllStores() } returns listOf(expired)

        impl.refreshStores()

        verifySuspend(exactly(1)) { dealsSource.fetchStores() }
        verifySuspend(exactly(1)) { storesDao.getAllStores() }
        verifySuspend(exactly(1)) {
            storesDao.addStores(fetched.copy(expires = expectedExpires))
        }
    }

    @Test
    fun refresh_stores_unforced_all_fresh_skips_fetch() = runTest {
        val fresh = store(expires = now + 10_000)

        everySuspend { storesDao.getAllStores() } returns listOf(fresh)

        impl.refreshStores()

        verifySuspend(exactly(0)) { dealsSource.fetchStores() }
        verifySuspend(exactly(1)) { storesDao.getAllStores() }
        verifySuspend(exactly(0)) { storesDao.addStores(*any()) }
    }

    @Test
    fun refresh_stores_forced_skips_freshness_check_and_stamps_expires() = runTest {
        val fetched = store(storeID = 99)
        val expectedExpires = now + millisInHour * 8

        everySuspend { dealsSource.fetchStores() } returns listOf(fetched)

        impl.refreshStores(force = true)

        verifySuspend(exactly(1)) { dealsSource.fetchStores() }
        verifySuspend(exactly(0)) { storesDao.getAllStores() }
        verifySuspend(exactly(1)) {
            storesDao.addStores(fetched.copy(expires = expectedExpires))
        }
    }

    @Test
    fun get_store_hit_returns_the_cached_store_without_refreshing() = runTest {
        val cached = store(storeID = 7)
        everySuspend { storesDao.getStore(7) } returns cached

        assertEquals(cached, impl.getStore(7))

        verifySuspend(exactly(0)) { dealsSource.fetchStores() }
    }

    @Test
    fun get_store_miss_forces_a_refresh_then_re_reads() = runTest {
        val fetched = store(storeID = 7)
        // Miss, then a hit once the forced refresh has repopulated the table.
        everySuspend { storesDao.getStore(7) } sequentially {
            returns(null)
            returns(fetched)
        }
        everySuspend { dealsSource.fetchStores() } returns listOf(fetched)

        assertEquals(fetched, impl.getStore(7))

        verifySuspend(exactly(1)) { dealsSource.fetchStores() }
        verifySuspend(exactly(2)) { storesDao.getStore(7) }
    }

    @Test
    fun get_store_miss_that_stays_missing_returns_null_rather_than_throwing() = runTest {
        everySuspend { storesDao.getStore(7) } returns null
        everySuspend { dealsSource.fetchStores() } returns listOf(store(storeID = 99))

        assertNull(impl.getStore(7))
    }

    @Test
    fun repeated_misses_within_the_cooldown_trigger_only_one_refresh() = runTest {
        everySuspend { storesDao.getStore(any()) } returns null
        everySuspend { dealsSource.fetchStores() } returns listOf(store(storeID = 99))

        // Mapping a screenful of deals from an unknown shop must cost one fetch, not one per deal.
        repeat(5) { assertNull(impl.getStore(7)) }

        verifySuspend(exactly(1)) { dealsSource.fetchStores() }
    }

    @Test
    fun a_miss_after_the_cooldown_expires_refreshes_again() = runTest {
        everySuspend { storesDao.getStore(any()) } returns null
        everySuspend { dealsSource.fetchStores() } returns listOf(store(storeID = 99))

        assertNull(impl.getStore(7))
        currentMillis = now + STORE_MISS_REFRESH_COOLDOWN_MILLIS + 1
        assertNull(impl.getStore(7))

        verifySuspend(exactly(2)) { dealsSource.fetchStores() }
    }

    @Test
    fun get_store_miss_survives_a_failing_refresh_on_an_empty_cache() = runTest {
        everySuspend { storesDao.getStore(7) } returns null
        // Nothing cached at all — a fresh install, cleared data, or a first launch with no signal.
        everySuspend { storesDao.getAllStores() } returns emptyList()
        everySuspend { dealsSource.fetchStores() } throws IllegalStateException("network down")

        // With an empty table CachedResource has no stale rows to serve and rethrows. That must not
        // reach the caller: propagating it is KOTLIN-E again, just from the refresh instead of the DAO.
        assertNull(impl.getStore(7))
    }

    @Test
    fun get_store_miss_survives_a_failing_refresh() = runTest {
        everySuspend { storesDao.getStore(7) } returns null
        everySuspend { storesDao.getAllStores() } returns listOf(store(storeID = 99))
        everySuspend { dealsSource.fetchStores() } throws IllegalStateException("network down")

        // CachedResource serves stale on error while anything is cached, so the miss degrades to null
        // instead of propagating — the caller's whole screen must not fail over one unknown shop.
        assertNull(impl.getStore(7))
    }
}
