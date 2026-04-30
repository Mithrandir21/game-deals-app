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
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.transformations.CurrencyTransformation
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSource
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSourceFactory
import pm.bam.gamedeals.testing.TestingLoggingListener

/**
 * Verifies [DealsRepositoryImpl] end-to-end against a real [CheapsharkSource]
 * backed by [MockWebServer]. The test boundary now lives at HTTP, so we no
 * longer need a forwarding `Remote*DataSource` mock between the repo and the
 * Retrofit wiring.
 */
class DealsRepositoryImplTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val dealsDao: DealsDao = mockk()

    private val domainDatabase: DomainDatabase = mockk()

    private val datetimeFormatter: DateTimeFormatter = mockk {
        every { formatToISODate(any<Long>()) } returns "2020-01-01"
        every { formatToISODateNullable(any<Long>()) } returns "2020-01-01"
    }

    private val currencyTransformation: CurrencyTransformation = mockk {
        every { valueToDenominated(any()) } answers { "$${firstArg<Double>()}" }
    }

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cheapsharkSource: CheapsharkSource
    private lateinit var impl: DealsRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply { start() }
        cheapsharkSource = CheapsharkSourceFactory.create(
            baseUrl = mockWebServer.url("/").toString(),
            logger = logger
        )
        impl = DealsRepositoryImpl(
            logger = logger,
            dealsDao = dealsDao,
            domainDatabase = domainDatabase,
            cheapsharkSource = cheapsharkSource,
            currencyTransformation = currencyTransformation,
            datetimeFormatter = datetimeFormatter
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }


    @Test
    fun `getDeal hits deals endpoint with id and maps response`() = runTest {
        val dealId = "abc123"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(DEAL_DETAILS_BODY))

        val result = impl.getDeal(dealId)
        assertNotNull(result)
        assertEquals("Some Game", result.gameInfo.name)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/1.0/deals"))
        assertTrue(recorded.path!!.contains("id=$dealId"))
    }


    @Test
    fun `refreshDeals - forced - fetches deals over HTTP and writes to DAO`() = runTest {
        val storeId = 1
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(DEAL_LIST_BODY))

        // Captures and invokes the Lambda for "withTransaction"
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionLambda = slot<suspend () -> Unit>()
        coEvery { domainDatabase.withTransaction(capture(transactionLambda)) } coAnswers { transactionLambda.captured.invoke() }

        coEvery { dealsDao.clearDealsForStore(storeId) } just runs
        coEvery { dealsDao.addDeals(*anyVararg()) } just runs


        impl.refreshDeals(storeId, force = true)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/1.0/deals"))
        assertTrue(recorded.path!!.contains("storeID=$storeId"))
        assertTrue(recorded.path!!.contains("pageSize=$DEAL_PAGE_COUNT"))

        coVerify(exactly = 1) { dealsDao.clearDealsForStore(storeId) }
        coVerify(exactly = 1) { dealsDao.addDeals(*anyVararg()) }
    }


    @Test
    fun `refreshDeals - unforced - skips HTTP when DAO has fresh deals`() = runTest {
        val storeId = 1
        val freshDeal = mockk<pm.bam.gamedeals.domain.models.Deal> {
            every { expires } returns System.currentTimeMillis() + 10_000
        }
        coEvery { dealsDao.getStoreDeals(storeId) } returns listOf(freshDeal)


        impl.refreshDeals(storeId)


        assertEquals(0, mockWebServer.requestCount)
        coVerify(exactly = 0) { dealsDao.clearDealsForStore(any()) }
    }


    private companion object {
        // language=JSON
        private const val DEAL_LIST_BODY = """[
            {
              "internalName": "GAME1",
              "title": "Game One",
              "dealID": "deal-1",
              "storeID": 1,
              "gameID": 100,
              "salePrice": 9.99,
              "normalPrice": 19.99,
              "isOnSale": "1",
              "savings": 50.0,
              "metacriticScore": 80,
              "steamRatingPercent": 90,
              "steamRatingCount": "100",
              "releaseDate": 0,
              "lastChange": 0,
              "dealRating": 9.0,
              "thumb": "thumb"
            }
          ]"""

        // language=JSON
        private const val DEAL_DETAILS_BODY = """{
            "gameInfo": {
              "storeID": 1,
              "gameID": 100,
              "name": "Some Game",
              "salePrice": 9.99,
              "retailPrice": 19.99,
              "steamRatingPercent": 90,
              "steamRatingCount": "100",
              "metacriticScore": 80,
              "releaseDate": 0,
              "publisher": "ACME",
              "thumb": "thumb"
            },
            "cheaperStores": [],
            "cheapestPrice": {
              "price": 4.99,
              "date": 0
            }
          }"""
    }
}
