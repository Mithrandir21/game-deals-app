package pm.bam.gamedeals.domain.repositories.stores

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.varargs.anyVarargs
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Lifted to commonTest in phase-A4b.
 *
 * Replaces `mockk<Store> { every { expires } returns ... }` with constructed [Store] values
 * (Mokkery cannot mock final classes). The previous `every { fetched.copy(expires = X) } returns
 * stamped` mock-the-copy pattern becomes a real `Store.copy(...)` call inside the verify block —
 * data classes have value equality, so the verify still pins the impl's stamping behavior.
 */
class StoresRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val storesDao: StoresDao = mock(MockMode.autoUnit)
    private val cheapsharkSource: CheapsharkSource = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private val clock = Clock { now }

    private val impl = StoresRepositoryImpl(logger, storesDao, cheapsharkSource, clock)

    @Test
    fun observe_stores_fresh_cache_does_not_refresh() = runTest {
        val fresh = store(expires = now + 10_000)

        every { storesDao.observeAllStores() } returns flowOf(emptyList())
        everySuspend { storesDao.getAllStores() } returns listOf(fresh)

        val result = impl.observeStores().first()
        assertTrue(result.isEmpty())

        verifySuspend(exactly(0)) { cheapsharkSource.fetchStores() }
        verify(exactly(1)) { storesDao.observeAllStores() }
        verifySuspend(exactly(1)) { storesDao.getAllStores() }
        verifySuspend(exactly(0)) { storesDao.addStores(*anyVarargs<Store>()) }
    }

    @Test
    fun refresh_stores_unforced_expired_entry_fetches_and_stamps_expires() = runTest {
        val expired = store(storeID = 1, expires = now - 1)
        val fetched = store(storeID = 99)
        val expectedExpires = now + millisInHour * 8

        everySuspend { cheapsharkSource.fetchStores() } returns listOf(fetched)
        everySuspend { storesDao.getAllStores() } returns listOf(expired)

        impl.refreshStores()

        verifySuspend(exactly(1)) { cheapsharkSource.fetchStores() }
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

        verifySuspend(exactly(0)) { cheapsharkSource.fetchStores() }
        verifySuspend(exactly(1)) { storesDao.getAllStores() }
        verifySuspend(exactly(0)) { storesDao.addStores(*anyVarargs<Store>()) }
    }

    @Test
    fun refresh_stores_forced_skips_freshness_check_and_stamps_expires() = runTest {
        val fetched = store(storeID = 99)
        val expectedExpires = now + millisInHour * 8

        everySuspend { cheapsharkSource.fetchStores() } returns listOf(fetched)

        impl.refreshStores(force = true)

        verifySuspend(exactly(1)) { cheapsharkSource.fetchStores() }
        verifySuspend(exactly(0)) { storesDao.getAllStores() }
        verifySuspend(exactly(1)) {
            storesDao.addStores(fetched.copy(expires = expectedExpires))
        }
    }
}

private fun store(
    storeID: Int = 1,
    storeName: String = "Test Store",
    isActive: Boolean = true,
    images: Store.StoreImages = Store.StoreImages(banner = "banner", logo = "logo", icon = "icon"),
    expires: Long = 0L,
) = Store(storeID, storeName, isActive, images, expires)
