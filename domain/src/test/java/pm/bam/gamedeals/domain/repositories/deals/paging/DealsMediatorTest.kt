package pm.bam.gamedeals.domain.repositories.deals.paging

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.db.dao.PagingDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealPage
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger

@OptIn(ExperimentalPagingApi::class)
internal class DealsMediatorTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val dealsDao: DealsDao = mockk()
    private val pagingDao: PagingDao = mockk()
    private val domainDatabase: DomainDatabase = mockk {
        every { getDealsDao() } returns dealsDao
        every { getPagingDao() } returns pagingDao
    }
    private val cheapsharkSource: CheapsharkSource = mockk()
    private val logger: Logger = mockk {
        every { log(any(), any(), any(), any()) } just runs
        every { fatalThrowable(any(), any()) } just runs
    }
    private val defaultStoreId: Int = 1
    private val pageSize: Int = 10

    private lateinit var mediator: DealsMediator
    private val state = PagingState<Int, Deal>(listOf(), null, PagingConfig(pageSize), pageSize)

    @Before
    fun setup() {
        mediator = DealsMediator(domainDatabase, cheapsharkSource, defaultStoreId, pageSize, logger)

        // Captures and invokes the Lambda for "withTransaction"
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Unit>()
        coEvery { domainDatabase.withTransaction(capture(transactionLambda)) } coAnswers { transactionLambda.captured.invoke() }
    }


    @Test
    fun `prepend results in Success with EndOfPage equal True`() = runTest {
        val result: RemoteMediator.MediatorResult = mediator.load(LoadType.PREPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)


        coVerify(exactly = 0) { pagingDao.getStorePage(defaultStoreId) }
        coVerify(exactly = 0) { cheapsharkSource.fetchDealsForStore(query = any()) }
    }


    @Test
    fun `APPEND results in Success with EndOfPage equal True`() = runTest {
        val page = 0
        val dealPage = DealPage(defaultStoreId, page)
        val newDealPage = DealPage(defaultStoreId, page + 1)

        val deal: Deal = mockk()

        coEvery { cheapsharkSource.fetchDealsForStore(query = any()) } returns listOf(deal)

        coEvery { pagingDao.getStorePage(defaultStoreId) } returns dealPage
        coEvery { pagingDao.insert(newDealPage) } just runs

        coEvery { dealsDao.addDeals(any()) } just runs


        val result: RemoteMediator.MediatorResult = mediator.load(LoadType.APPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        coVerify(exactly = 1) { pagingDao.getStorePage(defaultStoreId) }
        coVerify(exactly = 1) { cheapsharkSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 1) { domainDatabase.withTransaction(any()) }

        coVerify(exactly = 0) { dealsDao.clearDealsForStore(any()) }
        coVerify(exactly = 0) { pagingDao.clearStorePage(any()) }

        coVerify(exactly = 1) { pagingDao.insert(eq(newDealPage)) }
        coVerify(exactly = 1) { dealsDao.addDeals(any()) }
    }


    @Test
    fun `APPEND results in Error`() = runTest {
        val page = 0
        val dealPage = DealPage(defaultStoreId, page)
        val exception: Exception = mockk()

        coEvery { pagingDao.getStorePage(defaultStoreId) } returns dealPage

        coEvery { cheapsharkSource.fetchDealsForStore(query = any()) } throws exception


        val result: RemoteMediator.MediatorResult = mediator.load(LoadType.APPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertEquals(exception, (result as RemoteMediator.MediatorResult.Error).throwable)

        coVerify(exactly = 1) { pagingDao.getStorePage(defaultStoreId) }
        coVerify(exactly = 1) { cheapsharkSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 0) { domainDatabase.withTransaction(any()) }

        coVerify(exactly = 0) { dealsDao.clearDealsForStore(any()) }
        coVerify(exactly = 0) { pagingDao.clearStorePage(any()) }

        coVerify(exactly = 0) { pagingDao.insert(any()) }
        coVerify(exactly = 0) { dealsDao.addDeals(any()) }
    }


    @Test
    fun `REFRESH results in Success with EndOfPage equal True`() = runTest {
        val page = 0
        val newDealPage = DealPage(defaultStoreId, page + 1)

        val deal: Deal = mockk()

        coEvery { cheapsharkSource.fetchDealsForStore(query = any()) } returns listOf(deal)

        coEvery { pagingDao.clearStorePage(defaultStoreId) } just runs
        coEvery { pagingDao.insert(newDealPage) } just runs

        coEvery { dealsDao.clearDealsForStore(defaultStoreId) } just runs
        coEvery { dealsDao.addDeals(any()) } just runs


        val result: RemoteMediator.MediatorResult = mediator.load(LoadType.REFRESH, state)

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        coVerify(exactly = 0) { pagingDao.getStorePage(defaultStoreId) }
        coVerify(exactly = 1) { cheapsharkSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 1) { domainDatabase.withTransaction(any()) }

        coVerify(exactly = 1) { dealsDao.clearDealsForStore(defaultStoreId) }
        coVerify(exactly = 1) { pagingDao.clearStorePage(defaultStoreId) }

        coVerify(exactly = 1) { pagingDao.insert(eq(newDealPage)) }
        coVerify(exactly = 1) { dealsDao.addDeals(any()) }
    }
}
