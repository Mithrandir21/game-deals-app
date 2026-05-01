package pm.bam.gamedeals.domain.repositories.stores

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener

class StoresRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val storesDao: StoresDao = mockk()

    private val cheapsharkSource: CheapsharkSource = mockk()

    private val now = 1_000_000L
    private val clock = Clock { now }

    private val impl = StoresRepository(logger, storesDao, cheapsharkSource, clock)


    @Test
    fun `observe stores - fresh cache - does not refresh`() = runTest {
        val fresh: Store = mockk { every { expires } returns now + 10_000 }

        coEvery { storesDao.observeAllStores() } returns flowOf(emptyList())
        coEvery { storesDao.getAllStores() } returns listOf(fresh)


        val result = impl.observeStores().first()
        Assert.assertTrue(result.isEmpty())

        coVerify(exactly = 0) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 1) { storesDao.observeAllStores() }
        coVerify(exactly = 1) { storesDao.getAllStores() }
        coVerify(exactly = 0) { storesDao.addStores(*anyVararg()) }
    }


    @Test
    fun `refresh stores - unforced - expired entry - fetches and stamps expires`() = runTest {
        val expired: Store = mockk { every { expires } returns now - 1 }
        val fetched: Store = mockk()
        val stamped: Store = mockk()
        every { fetched.copy(expires = now + millisInHour * 8) } returns stamped

        coEvery { cheapsharkSource.fetchStores() } returns listOf(fetched)
        coEvery { storesDao.getAllStores() } returns listOf(expired)
        coEvery { storesDao.addStores(*anyVararg()) } just runs


        impl.refreshStores()


        coVerify(exactly = 1) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 1) { storesDao.getAllStores() }
        coVerify(exactly = 1) { storesDao.addStores(stamped) }
        verify(exactly = 1) { fetched.copy(expires = now + millisInHour * 8) }
    }


    @Test
    fun `refresh stores - unforced - all fresh - skips fetch`() = runTest {
        val fresh: Store = mockk { every { expires } returns now + 10_000 }

        coEvery { storesDao.getAllStores() } returns listOf(fresh)


        impl.refreshStores()


        coVerify(exactly = 0) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 1) { storesDao.getAllStores() }
        coVerify(exactly = 0) { storesDao.addStores(*anyVararg()) }
    }


    @Test
    fun `refresh stores - forced - skips freshness check and stamps expires`() = runTest {
        val fetched: Store = mockk()
        val stamped: Store = mockk()
        every { fetched.copy(expires = now + millisInHour * 8) } returns stamped

        coEvery { cheapsharkSource.fetchStores() } returns listOf(fetched)
        coEvery { storesDao.addStores(*anyVararg()) } just runs


        impl.refreshStores(force = true)


        coVerify(exactly = 1) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 0) { storesDao.getAllStores() }
        coVerify(exactly = 1) { storesDao.addStores(stamped) }
    }
}
