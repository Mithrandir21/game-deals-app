package pm.bam.gamedeals.domain.repositories.deals

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener


class DealsRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val dealsDao: DealsDao = mockk()

    private val domainDatabase: DomainDatabase = mockk()

    private val cheapsharkSource: CheapsharkSource = mockk()

    private val now = 1_000_000L
    private val clock = Clock { now }

    private val impl = DealsRepository(logger, dealsDao, domainDatabase, cheapsharkSource, clock)


    @Test
    fun `observe all deals`() = runTest {
        coEvery { dealsDao.observeAllDeals() } returns flowOf(emptyList())

        val result = impl.observeAllDeals().first()
        assertTrue(result.isEmpty())

        coVerify(exactly = 1) { dealsDao.observeAllDeals() }
    }


    @Test
    fun `get deal returns DealDetails from source`() = runTest {
        val id = "abc123"
        val details: DealDetails = mockk()

        coEvery { cheapsharkSource.fetchDealDetails(id) } returns details


        val result = impl.getDeal(id)
        assertEquals(details, result)

        coVerify(exactly = 1) { cheapsharkSource.fetchDealDetails(id) }
    }


    @Test
    fun `refreshDeals - forced - fetches via source, stamps expires, writes to DAO`() = runTest {
        val storeId = 1
        val fetched: Deal = mockk()
        val stamped: Deal = mockk()
        every { fetched.copy(expires = now + millisInHour * 8) } returns stamped

        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Unit>()
        coEvery { domainDatabase.withTransaction(capture(transactionLambda)) } coAnswers { transactionLambda.captured.invoke() }

        coEvery { dealsDao.clearDealsForStore(storeId) } just runs
        coEvery { cheapsharkSource.fetchDealsForStore(query = any()) } returns listOf(fetched)
        coEvery { dealsDao.addDeals(*anyVararg()) } just runs


        impl.refreshDeals(storeId, force = true)


        coVerify(exactly = 1) { dealsDao.clearDealsForStore(storeId) }
        coVerify(exactly = 1) { cheapsharkSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 1) { dealsDao.addDeals(stamped) }
        verify(exactly = 1) { fetched.copy(expires = now + millisInHour * 8) }
    }


    @Test
    fun `refreshDeals - unforced - expired deals - fetches via source`() = runTest {
        val storeId = 1
        val expired: Deal = mockk { every { expires } returns now - 10_000 }
        val fetched: Deal = mockk()
        val stamped: Deal = mockk()
        every { fetched.copy(expires = now + millisInHour * 8) } returns stamped

        coEvery { dealsDao.getStoreDeals(storeId) } returns listOf(expired)

        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Unit>()
        coEvery { domainDatabase.withTransaction(capture(transactionLambda)) } coAnswers { transactionLambda.captured.invoke() }

        coEvery { dealsDao.clearDealsForStore(storeId) } just runs
        coEvery { cheapsharkSource.fetchDealsForStore(query = any()) } returns listOf(fetched)
        coEvery { dealsDao.addDeals(*anyVararg()) } just runs


        impl.refreshDeals(storeId)


        coVerify(exactly = 1) { dealsDao.clearDealsForStore(storeId) }
        coVerify(exactly = 1) { cheapsharkSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 1) { dealsDao.addDeals(stamped) }
    }


    @Test
    fun `refreshDeals - unforced - skips source when DAO has fresh deals`() = runTest {
        val storeId = 1
        val fresh: Deal = mockk { every { expires } returns now + 10_000 }
        coEvery { dealsDao.getStoreDeals(storeId) } returns listOf(fresh)


        impl.refreshDeals(storeId)


        coVerify(exactly = 0) { cheapsharkSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 0) { dealsDao.clearDealsForStore(any()) }
    }
}
