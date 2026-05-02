package pm.bam.gamedeals.remote.cheapshark

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.mappers.CheapsharkMapperContext
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.testing.TestingLoggingListener
import retrofit2.Retrofit

/**
 * HTTP-level coverage for the [CheapsharkSourceImpl] facade. Stands a real
 * Retrofit + the four `*Api` types up against [MockWebServer] so the wiring
 * (paths, query parameters, JSON decoding) is exercised end-to-end inside the
 * module that owns it.
 */
class CheapsharkSourceImplTest {

    private val logger: Logger = TestingLoggingListener()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var impl: CheapsharkSourceImpl

    private val currencyTransformation: CurrencyTransformation = mockk {
        every { valueToDenominated(any()) } answers { "$${firstArg<Double>()}" }
    }

    private val datetimeFormatter: DateTimeFormatter = mockk {
        every { formatToISODate(any<Long>()) } returns "2020-01-01"
        every { formatToISODateNullable(any<Long>()) } returns "2020-01-01"
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply { start() }

        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()

        impl = CheapsharkSourceImpl(
            logger = logger,
            dealsApi = retrofit.create(DealsApi::class.java),
            gamesApi = retrofit.create(GamesApi::class.java),
            releaseApi = retrofit.create(ReleaseApi::class.java),
            storesApi = retrofit.create(StoresApi::class.java),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
            ctx = CheapsharkMapperContext(currencyTransformation, datetimeFormatter),
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchDealDetails hits deals endpoint with id and decodes response`() = runTest {
        val dealId = "abc123"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(DEAL_DETAILS_BODY))

        val result = impl.fetchDealDetails(dealId)
        assertNotNull(result)
        assertEquals("Some Game", result.gameInfo.name)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/1.0/deals"))
        assertTrue(recorded.path!!.contains("id=$dealId"))
    }

    @Test
    fun `fetchDealsForStore forwards storeID and pageSize as query parameters`() = runTest {
        val storeId = 1
        val pageSize = 60
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(DEAL_LIST_BODY))

        val result = impl.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = pageSize, sortBy = null))
        assertEquals(1, result.size)
        assertEquals("Game One", result.first().title)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/1.0/deals"))
        assertTrue(recorded.path!!.contains("storeID=$storeId"))
        assertTrue(recorded.path!!.contains("pageSize=$pageSize"))
    }

    @Test
    fun `fetchStores hits stores endpoint and decodes response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(STORE_LIST_BODY))

        val result = impl.fetchStores()
        assertEquals(1, result.size)
        assertEquals("Steam", result.first().storeName)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/1.0/stores", recorded.path)
    }

    @Test
    fun `fetchReleases hits releases endpoint and decodes response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(RELEASE_LIST_BODY))

        val result = impl.fetchReleases()
        assertEquals(1, result.size)
        assertEquals("Upcoming Game", result.first().title)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/other/releases", recorded.path)
    }

    @Test
    fun `fetchGames forwards title query parameter`() = runTest {
        val title = "halo"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(GAME_LIST_BODY))

        val result = impl.fetchGames(title = title)
        assertEquals(1, result.size)
        assertEquals("Halo", result.first().title)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/1.0/games"))
        assertTrue(recorded.path!!.contains("title=$title"))
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
              "isOnSale": 1,
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

        // language=JSON
        private const val STORE_LIST_BODY = """[
            {
              "storeID": 1,
              "storeName": "Steam",
              "isActive": 1,
              "images": {
                "banner": "banner.png",
                "logo": "logo.png",
                "icon": "icon.png"
              }
            }
          ]"""

        // language=JSON
        private const val RELEASE_LIST_BODY = """[
            {
              "date": 0,
              "title": "Upcoming Game",
              "image": "image.png"
            }
          ]"""

        // language=JSON
        private const val GAME_LIST_BODY = """[
            {
              "gameID": 100,
              "steamAppID": 100,
              "cheapest": 9.99,
              "cheapestDealID": "deal-1",
              "external": "Halo",
              "internalName": "HALO",
              "thumb": "thumb"
            }
          ]"""
    }
}
