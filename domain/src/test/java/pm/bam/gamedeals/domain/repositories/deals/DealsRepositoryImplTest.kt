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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.toDeal
import pm.bam.gamedeals.domain.models.toDealDetails
import pm.bam.gamedeals.domain.transformations.CurrencyTransformation
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSource
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails
import pm.bam.gamedeals.testing.TestingLoggingListener


class DealsRepositoryImplTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val dealsDao: DealsDao = mockk()

    private val domainDatabase: DomainDatabase = mockk()

    private val cheapsharkSource: CheapsharkSource = mockk()

    private val currencyTransformation: CurrencyTransformation = mockk()

    private val datetimeFormatter: DateTimeFormatter = mockk()

    private val impl = DealsRepositoryImpl(logger, dealsDao, domainDatabase, cheapsharkSource, currencyTransformation, datetimeFormatter)

    @Test
    fun `observe deals with refresh called`() = runTest {
        coEvery { dealsDao.observeAllDeals() } returns flowOf(emptyList())

        val result = impl.observeAllDeals().first()
        assertTrue(result.isEmpty())

        coVerify(exactly = 1) { dealsDao.observeAllDeals() }
    }


    @Test
    fun `get deal`() = runTest {
        val id = "xyz"
        val remoteResults: RemoteDealDetails = mockk()
        val results: DealDetails = mockk()

        mockkStatic(RemoteDealDetails::toDealDetails)
        every { remoteResults.toDealDetails(currencyTransformation, datetimeFormatter) } returns results

        coEvery { cheapsharkSource.fetchDealDetails(id) } returns remoteResults


        val result = impl.getDeal(id)
        assertEquals(results, result)

        coVerify(exactly = 1) { cheapsharkSource.fetchDealDetails(id) }
    }

    @Test
    fun `refresh deals - unforced - expired deals`() = runTest {
        val remoteStoreId = 1
        val remoteResultsQuery = RemoteDealsQuery(storeID = remoteStoreId, pageSize = DEAL_PAGE_COUNT)
        val remoteResults: RemoteDeal = mockk()
        val results: Deal = mockk {
            every { expires } returns 0
        }

        mockkStatic(RemoteDeal::toDeal)
        every { remoteResults.toDeal(currencyTransformation) } returns results

        // Captures and invokes the Lambda for "withTransaction"
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Unit>()
        coEvery { domainDatabase.withTransaction(capture(transactionLambda)) } coAnswers { transactionLambda.captured.invoke() }

        coEvery { cheapsharkSource.fetchDealsForStore(remoteResultsQuery) } returns listOf(remoteResults)
        coEvery { dealsDao.getStoreDeals(remoteStoreId) } returns listOf(results)
        coEvery { dealsDao.clearDealsForStore(remoteStoreId) } just runs
        coEvery { dealsDao.addDeals(results) } just runs


        impl.refreshDeals(remoteStoreId)

        coVerify(exactly = 1) { cheapsharkSource.fetchDealsForStore(remoteResultsQuery) }
        coVerify(exactly = 1) { dealsDao.getStoreDeals(remoteStoreId) }
        coVerify(exactly = 1) { dealsDao.clearDealsForStore(remoteStoreId) }
        coVerify(exactly = 1) { dealsDao.addDeals(results) }
    }

    @Test
    fun `refresh deals - unforced - not expired deals`() = runTest {
        val remoteStoreId = 1
        val results: Deal = mockk {
            every { expires } returns System.currentTimeMillis() + 10000
        }

        coEvery { dealsDao.getStoreDeals(remoteStoreId) } returns listOf(results)


        impl.refreshDeals(remoteStoreId)

        coVerify(exactly = 0) { cheapsharkSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 1) { dealsDao.getStoreDeals(remoteStoreId) }
        coVerify(exactly = 0) { dealsDao.clearDealsForStore(remoteStoreId) }
        coVerify(exactly = 0) { dealsDao.addDeals(any()) }
    }

    @Test
    fun `refresh deals - forced`() = runTest {
        val remoteStoreId = 1
        val remoteResultsQuery = RemoteDealsQuery(storeID = remoteStoreId, pageSize = DEAL_PAGE_COUNT)
        val remoteResults: RemoteDeal = mockk()
        val results: Deal = mockk {
            every { expires } returns 0
        }

        mockkStatic(RemoteDeal::toDeal)
        every { remoteResults.toDeal(currencyTransformation) } returns results

        // Captures and invokes the Lambda for "withTransaction"
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Unit>()
        coEvery { domainDatabase.withTransaction(capture(transactionLambda)) } coAnswers { transactionLambda.captured.invoke() }

        coEvery { cheapsharkSource.fetchDealsForStore(remoteResultsQuery) } returns listOf(remoteResults)
        coEvery { dealsDao.clearDealsForStore(remoteStoreId) } just runs
        coEvery { dealsDao.addDeals(results) } just runs


        impl.refreshDeals(remoteStoreId, force = true)

        coVerify(exactly = 1) { cheapsharkSource.fetchDealsForStore(remoteResultsQuery) }
        coVerify(exactly = 0) { dealsDao.getStoreDeals(any()) }
        coVerify(exactly = 1) { dealsDao.clearDealsForStore(remoteStoreId) }
        coVerify(exactly = 1) { dealsDao.addDeals(results) }
    }

}
