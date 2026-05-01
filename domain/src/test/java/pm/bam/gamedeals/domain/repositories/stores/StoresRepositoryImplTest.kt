package pm.bam.gamedeals.domain.repositories.stores

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.toStore
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSource
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore
import pm.bam.gamedeals.testing.TestingLoggingListener

class StoresRepositoryImplTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val storesDao: StoresDao = mockk()

    private val cheapsharkSource: CheapsharkSource = mockk()

    private val impl = StoresRepositoryImpl(logger, storesDao, cheapsharkSource)

    @Test
    fun `observe stores with refresh called`() = runTest {
        val remoteResults: RemoteStore = mockk()
        val results: Store = mockk {
            every { expires } returns System.currentTimeMillis() + 10000
        }

        mockkStatic(RemoteStore::toStore)
        every { remoteResults.toStore() } returns results

        coEvery { storesDao.observeAllStores() } returns flowOf(listOf())
        coEvery { storesDao.getAllStores() } returns listOf(results)


        val result = impl.observeStores().first()
        Assert.assertTrue(result.isEmpty())

        coVerify(exactly = 0) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 1) { storesDao.observeAllStores() }
        coVerify(exactly = 1) { storesDao.getAllStores() }
        coVerify(exactly = 0) { storesDao.addStores(results) }
    }


    @Test
    fun `refresh stores - unforced - expired deals`() = runTest {
        val remoteResults: RemoteStore = mockk()
        val results: Store = mockk {
            every { expires } returns 0
        }

        mockkStatic(RemoteStore::toStore)
        every { remoteResults.toStore() } returns results

        coEvery { cheapsharkSource.fetchStores() } returns listOf(remoteResults)
        coEvery { storesDao.getAllStores() } returns listOf(results)
        coEvery { storesDao.addStores(results) } just runs


        impl.refreshStores()

        coVerify(exactly = 1) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 1) { storesDao.getAllStores() }
        coVerify(exactly = 1) { storesDao.addStores(results) }
    }

    @Test
    fun `refresh deals - unforced - not expired deals`() = runTest {
        val remoteResults: RemoteStore = mockk()
        val results: Store = mockk {
            every { expires } returns System.currentTimeMillis() + 10000
        }

        mockkStatic(RemoteStore::toStore)
        every { remoteResults.toStore() } returns results

        coEvery { storesDao.getAllStores() } returns listOf(results)


        impl.refreshStores()

        coVerify(exactly = 0) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 1) { storesDao.getAllStores() }
        coVerify(exactly = 0) { storesDao.addStores(results) }
    }

    @Test
    fun `refresh deals - forced`() = runTest {
        val remoteResults: RemoteStore = mockk()
        val results: Store = mockk {
            every { expires } returns 0
        }

        mockkStatic(RemoteStore::toStore)
        every { remoteResults.toStore() } returns results

        coEvery { cheapsharkSource.fetchStores() } returns listOf(remoteResults)
        coEvery { storesDao.getAllStores() } returns listOf(results)
        coEvery { storesDao.addStores(results) } just runs


        impl.refreshStores()

        coVerify(exactly = 1) { cheapsharkSource.fetchStores() }
        coVerify(exactly = 1) { storesDao.getAllStores() }
        coVerify(exactly = 1) { storesDao.addStores(results) }
    }
}
