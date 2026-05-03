package pm.bam.gamedeals.remote.cheapshark

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.testing.TestingLoggingListener

/**
 * HTTP-level coverage for the [CheapsharkSourceImpl] facade. Stands the four `*Api`
 * Ktor classes up against a [MockEngine] so the wiring (paths, query parameters, JSON
 * decoding) is exercised end-to-end inside the module that owns it.
 *
 * MockEngine routes by path; the recorded requests list is asserted at the end of each
 * test to verify path + query-parameter wiring identical to the prior MockWebServer setup.
 */
class CheapsharkSourceImplTest {

    private val logger: Logger = TestingLoggingListener()

    private val recordedRequests = mutableListOf<HttpRequestData>()
    private lateinit var impl: CheapsharkSourceImpl

    private val currencyTransformation: CurrencyTransformation = mockk {
        every { valueToDenominated(any()) } answers { "$${firstArg<Double>()}" }
    }

    private val datetimeFormatter: DateTimeFormatter = mockk {
        every { formatToISODate(any<Long>()) } returns "2020-01-01"
        every { formatToISODateNullable(any<Long>()) } returns "2020-01-01"
    }

    @Before
    fun setUp() {
        recordedRequests.clear()

        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        val mockEngine = MockEngine { request ->
            recordedRequests += request
            val path = request.url.encodedPath
            val body = when {
                path == "/api/1.0/deals" && request.url.parameters["id"] != null -> DEAL_DETAILS_BODY
                path == "/api/1.0/deals" -> DEAL_LIST_BODY
                path == "/api/1.0/games" -> GAME_LIST_BODY
                path == "/api/1.0/stores" -> STORE_LIST_BODY
                path == "/api/other/releases" -> RELEASE_LIST_BODY
                else -> ""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
        }

        impl = CheapsharkSourceImpl(
            logger = logger,
            dealsApi = DealsApi(httpClient),
            gamesApi = GamesApi(httpClient),
            releaseApi = ReleaseApi(httpClient),
            storesApi = StoresApi(httpClient),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
            currencyTransformation = currencyTransformation,
            datetimeFormatter = datetimeFormatter
        )
    }

    @Test
    fun `fetchDealDetails hits deals endpoint with id and decodes response`() = runTest {
        val dealId = "abc123"

        val result = impl.fetchDealDetails(dealId)
        assertNotNull(result)
        assertEquals("Some Game", result.gameInfo.name)

        assertEquals(1, recordedRequests.size)
        val recorded = recordedRequests.first().url
        assertEquals("/api/1.0/deals", recorded.encodedPath)
        assertEquals(dealId, recorded.parameters["id"])
    }

    @Test
    fun `fetchDealsForStore forwards storeID and pageSize as query parameters`() = runTest {
        val storeId = 1
        val pageSize = 60

        val result = impl.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = pageSize, sortBy = null))
        assertEquals(1, result.size)
        assertEquals("Game One", result.first().title)

        assertEquals(1, recordedRequests.size)
        val recorded = recordedRequests.first().url
        assertEquals("/api/1.0/deals", recorded.encodedPath)
        assertEquals(storeId.toString(), recorded.parameters["storeID"])
        assertEquals(pageSize.toString(), recorded.parameters["pageSize"])
    }

    @Test
    fun `fetchStores hits stores endpoint and decodes response`() = runTest {
        val result = impl.fetchStores()
        assertEquals(1, result.size)
        assertEquals("Steam", result.first().storeName)

        assertEquals(1, recordedRequests.size)
        assertEquals("/api/1.0/stores", recordedRequests.first().url.encodedPath)
    }

    @Test
    fun `fetchReleases hits releases endpoint and decodes response`() = runTest {
        val result = impl.fetchReleases()
        assertEquals(1, result.size)
        assertEquals("Upcoming Game", result.first().title)

        assertEquals(1, recordedRequests.size)
        assertEquals("/api/other/releases", recordedRequests.first().url.encodedPath)
    }

    @Test
    fun `fetchGames forwards title query parameter`() = runTest {
        val title = "halo"

        val result = impl.fetchGames(title = title)
        assertEquals(1, result.size)
        assertEquals("Halo", result.first().title)

        assertEquals(1, recordedRequests.size)
        val recorded = recordedRequests.first().url
        assertEquals("/api/1.0/games", recorded.encodedPath)
        assertEquals(title, recorded.parameters["title"])
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
        // storeID is a JSON string in the live cheapshark response, not an int.
        private const val STORE_LIST_BODY = """[
            {
              "storeID": "1",
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
