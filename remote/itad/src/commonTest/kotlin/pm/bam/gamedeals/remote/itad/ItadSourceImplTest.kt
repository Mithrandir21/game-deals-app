package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.api.ItadShopsApi
import pm.bam.gamedeals.remote.itad.logic.ITAD_HOST
import pm.bam.gamedeals.remote.itad.logic.itadHttpClient
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * HTTP-level coverage for [ItadSourceImpl] and the ITAD `*Api` classes, driven against a
 * [MockEngine] so paths, query parameters, POST bodies, JSON decoding and the DTO→ITAD-shaped-model
 * mapping are exercised end-to-end. MockEngine routes by path; recorded requests are asserted to
 * verify wiring. [RemoteExceptionTransformer] is the identity transform here.
 */
class ItadSourceImplTest {

    private val logger: Logger = TestingLoggingListener()
    private val recordedRequests = mutableListOf<HttpRequestData>()
    private lateinit var impl: ItadSourceImpl

    @BeforeTest
    fun setUp() {
        recordedRequests.clear()

        val json = Json { ignoreUnknownKeys = true }

        val httpClient = mockHttpClient(json) { request ->
            recordedRequests += request
            val body = when (request.url.encodedPath) {
                "/service/shops/v1" -> SHOPS_BODY
                "/deals/v2" -> DEALS_BODY
                "/games/prices/v3" -> PRICES_BODY
                "/games/history/v2" -> HISTORY_BODY
                "/games/search/v1" -> SEARCH_BODY
                "/games/lookup/v1" -> LOOKUP_BODY
                else -> ""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        impl = ItadSourceImpl(
            logger = logger,
            shopsApi = ItadShopsApi(httpClient),
            dealsApi = ItadDealsApi(httpClient),
            gamesApi = ItadGamesApi(httpClient),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
        )
    }

    @Test
    fun fetchStores_maps_shop_id_and_name() = runTest {
        val stores = impl.fetchStores()

        assertEquals(1, stores.size)
        assertEquals(61, stores.first().storeID)
        assertEquals("Steam", stores.first().storeName)
        assertEquals("/service/shops/v1", recordedRequests.single().url.encodedPath)
    }

    @Test
    fun fetchDeals_flattens_and_maps_deal_entries() = runTest {
        val deals = impl.fetchDeals(country = "US")

        assertEquals(1, deals.size)
        val deal = deals.first()
        assertEquals("uuid-1", deal.gameId)
        assertEquals("Halo", deal.gameTitle)
        assertEquals(61, deal.shop.id)
        assertEquals(9.99, deal.price.amount)
        assertEquals("USD", deal.price.currency)
        assertEquals(50, deal.cutPercent)
        assertEquals("https://store/halo", deal.url)

        val recorded = recordedRequests.single().url
        assertEquals("/deals/v2", recorded.encodedPath)
        assertEquals("US", recorded.parameters["country"])
    }

    @Test
    fun fetchGamePrices_maps_history_low_and_deals() = runTest {
        val prices = impl.fetchGamePrices(listOf("uuid-1"), country = "US")

        assertEquals(1, prices.size)
        assertEquals("uuid-1", prices.first().gameId)
        assertEquals(4.99, prices.first().historyLowAll?.amount)
        assertEquals(1, prices.first().deals.size)
        assertEquals(35, prices.first().deals.first().shop.id)
        assertEquals(63, prices.first().deals.first().cutPercent)
        assertEquals("/games/prices/v3", recordedRequests.single().url.encodedPath)
    }

    @Test
    fun fetchPriceHistory_maps_entries_and_filters_rows_without_a_deal() = runTest {
        val history = impl.fetchPriceHistory("uuid-1")

        // Two rows in the fixture; the second has a null deal and is dropped.
        assertEquals(1, history.size)
        assertEquals("2026-01-01T00:00:00Z", history.first().timestamp)
        assertEquals(5.99, history.first().price.amount)
        assertEquals(70, history.first().cutPercent)
        assertEquals("/games/history/v2", recordedRequests.single().url.encodedPath)
    }

    @Test
    fun searchGames_maps_results() = runTest {
        val results = impl.searchGames("halo")

        assertEquals(1, results.size)
        assertEquals("uuid-1", results.first().id)
        assertEquals("Halo", results.first().title)
        assertEquals("box.png", results.first().boxart)
        assertEquals("/games/search/v1", recordedRequests.single().url.encodedPath)
    }

    @Test
    fun lookupBySteamAppId_maps_game_and_carries_the_queried_appid() = runTest {
        val result = impl.lookupBySteamAppId(1240)

        assertNotNull(result)
        assertEquals("uuid-1", result.id)
        assertEquals(1240, result.steamAppId)
        val recorded = recordedRequests.single().url
        assertEquals("/games/lookup/v1", recorded.encodedPath)
        assertEquals("1240", recorded.parameters["appid"])
    }

    @Test
    fun dealsSource_methods_blocked_by_the_model_gap_throw_until_phase_2() = runTest {
        assertFailsWith<UnsupportedOperationException> { impl.fetchDealDetails("x") }
        assertFailsWith<UnsupportedOperationException> { impl.fetchGames("x", null, null, null) }
        assertFailsWith<UnsupportedOperationException> { impl.fetchGameDetails("x") }
        assertFailsWith<UnsupportedOperationException> { impl.fetchDealsForStore(null) }
    }

    @Test
    fun itadHttpClient_sends_the_api_key_header_to_the_itad_host() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            recorded += request
            respond(
                content = SHOPS_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = engine,
        )

        ItadShopsApi(client).getShops().getOrThrow()

        val request = recorded.single()
        assertEquals("test-key", request.headers["ITAD-API-Key"])
        assertEquals(ITAD_HOST, request.url.host)
    }

    private companion object {
        // language=JSON
        private const val SHOPS_BODY = """[ { "id": 61, "title": "Steam" } ]"""

        // language=JSON
        private const val DEALS_BODY = """[
            {
              "id": "uuid-1",
              "slug": "halo",
              "title": "Halo",
              "deals": [
                {
                  "shop": { "id": 61, "name": "Steam" },
                  "price": { "amount": 9.99, "amountInt": 999, "currency": "USD" },
                  "regular": { "amount": 19.99, "amountInt": 1999, "currency": "USD" },
                  "cut": 50,
                  "url": "https://store/halo"
                }
              ]
            }
          ]"""

        // language=JSON
        private const val PRICES_BODY = """[
            {
              "id": "uuid-1",
              "historyLow": { "all": { "amount": 4.99, "amountInt": 499, "currency": "USD" } },
              "deals": [
                {
                  "shop": { "id": 35, "name": "GOG" },
                  "price": { "amount": 7.49, "amountInt": 749, "currency": "USD" },
                  "regular": { "amount": 19.99, "amountInt": 1999, "currency": "USD" },
                  "cut": 63,
                  "url": "https://gog/halo"
                }
              ]
            }
          ]"""

        // language=JSON
        private const val HISTORY_BODY = """[
            {
              "timestamp": "2026-01-01T00:00:00Z",
              "shop": { "id": 61, "name": "Steam" },
              "deal": { "price": { "amount": 5.99, "amountInt": 599, "currency": "USD" }, "regular": { "amount": 19.99, "amountInt": 1999, "currency": "USD" }, "cut": 70 }
            },
            {
              "timestamp": "2026-02-01T00:00:00Z",
              "shop": { "id": 61, "name": "Steam" },
              "deal": null
            }
          ]"""

        // language=JSON
        private const val SEARCH_BODY = """[ { "id": "uuid-1", "slug": "halo", "title": "Halo", "assets": { "boxart": "box.png" } } ]"""

        // language=JSON
        private const val LOOKUP_BODY = """{ "found": true, "game": { "id": "uuid-1", "slug": "halo", "title": "Halo" } }"""
    }
}
