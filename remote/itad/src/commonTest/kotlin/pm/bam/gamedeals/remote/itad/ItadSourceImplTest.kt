package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSort
import pm.bam.gamedeals.domain.models.SUPPORTED_COUNTRIES
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadBundlesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HTTP-level coverage for [ItadSourceImpl] and the ITAD `*Api` classes, driven against a
 * [MockEngine] so paths, query parameters, POST bodies, JSON decoding and the DTO→ITAD-shaped-model
 * mapping are exercised end-to-end. MockEngine routes by path; recorded requests are asserted to
 * verify wiring. [RemoteExceptionTransformer] is the identity transform here.
 */
@OptIn(ExperimentalSerializationApi::class)
class ItadSourceImplTest {

    private val logger: Logger = TestingLoggingListener()
    private val recordedRequests = mutableListOf<HttpRequestData>()
    private lateinit var impl: ItadSourceImpl

    // The source reads the country from [RegionRepository]; the tests assert `country=US`, so the fake
    // always reports US (regional pricing — Phase 3b, #212).
    private val regionRepository = object : RegionRepository {
        override val supportedCountries: List<Country> = SUPPORTED_COUNTRIES
        override fun observeSelectedCountry(): Flow<Country> = flowOf(DEFAULT_COUNTRY)
        override suspend fun getSelectedCountryCode(): String = "US"
        override suspend fun setSelectedCountry(country: Country) = Unit
    }

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
                "/games/info/v2" -> INFO_BODY
                "/bundles/v1" -> BUNDLES_BODY
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
            bundlesApi = ItadBundlesApi(httpClient),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
            regionRepository = regionRepository,
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
    fun fetchItadDeals_maps_the_singular_deal_per_game_from_the_envelope() = runTest {
        val deals = impl.fetchItadDeals(country = "US")

        assertEquals(1, deals.size)
        val deal = deals.first()
        assertEquals("uuid-1", deal.gameId)
        assertEquals("Halo", deal.gameTitle)
        assertEquals(61, deal.shop.id)
        assertEquals(9.99, deal.price.amount)
        assertEquals("USD", deal.price.currency)
        assertEquals(50, deal.cutPercent)
        assertEquals("https://store/halo", deal.url)
        assertEquals("halo-banner300.png", deal.boxart) // prioritized banner300 over boxart
        assertTrue(deal.isLowestEver) // flag "N" (new historical low)
        assertTrue(deal.isNewHistoricalLow) // "N" is specifically a *new* low
        assertFalse(deal.isStoreLow)
        assertTrue(deal.hasVoucher) // voucher code present on the entry

        val recorded = recordedRequests.single().url
        assertEquals("/deals/v2", recorded.encodedPath)
        assertEquals("US", recorded.parameters["country"])
    }

    @Test
    fun fetchDeals_query_passes_sort_shops_offset_and_maps_to_domain() = runTest {
        val deals = impl.fetchDeals(
            DealsQuery(sort = DealsSort.TopDiscount, shopIds = listOf(61, 16), offset = 30, limit = 30)
        )

        assertEquals(1, deals.size)
        val deal = deals.first()
        assertEquals("uuid-1", deal.gameID)
        assertEquals("Halo", deal.title)
        assertEquals(61, deal.storeID)
        assertTrue(deal.isLowestEver) // mapped from the /deals/v2 deal flag "N"
        assertTrue(deal.isNewHistoricalLow)
        assertTrue(deal.hasVoucher)

        val recorded = recordedRequests.single().url
        assertEquals("/deals/v2", recorded.encodedPath)
        assertEquals("US", recorded.parameters["country"]) // applied from RegionRepository
        assertEquals("-cut", recorded.parameters["sort"])
        assertEquals("61,16", recorded.parameters["shops"])
        assertEquals("30", recorded.parameters["offset"])
        assertEquals("30", recorded.parameters["limit"])
    }

    @Test
    fun fetchDeals_query_omits_shops_when_no_shop_filter() = runTest {
        impl.fetchDeals(DealsQuery(sort = DealsSort.PriceLowToHigh, shopIds = emptyList()))

        val recorded = recordedRequests.single().url
        assertEquals("/deals/v2", recorded.encodedPath)
        assertEquals("price", recorded.parameters["sort"])
        assertNull(recorded.parameters["shops"])
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
    fun fetchItadPriceHistory_maps_entries_and_filters_rows_without_a_deal() = runTest {
        val history = impl.fetchItadPriceHistory("uuid-1")

        // Two rows in the fixture; the second has a null deal and is dropped.
        assertEquals(1, history.size)
        assertEquals("2026-01-01T00:00:00Z", history.first().timestamp)
        assertEquals(5.99, history.first().price.amount)
        assertEquals(70, history.first().cutPercent)
        assertEquals("/games/history/v2", recordedRequests.single().url.encodedPath)
    }

    @Test
    fun fetchPriceHistory_maps_to_domain_points_sorted_oldest_first() = runTest {
        val history = impl.fetchPriceHistory("uuid-1")

        assertEquals("uuid-1", history.gameID)
        // The null-deal row is dropped; the remaining row becomes one domain point.
        assertEquals(1, history.points.size)
        val point = history.points.first()
        assertEquals(1767225600000L, point.timestampEpochMs) // 2026-01-01T00:00:00Z
        assertEquals(5.99, point.priceValue)
        assertEquals("$5.99", point.priceDenominated)
        assertEquals("/games/history/v2", recordedRequests.single().url.encodedPath)
        assertEquals("US", recordedRequests.single().url.parameters["country"])
    }

    @Test
    fun fetchBundles_maps_array_with_cheapest_price_and_union_of_tier_games() = runTest {
        val bundles = impl.fetchBundles()

        assertEquals(1, bundles.size)
        val bundle = bundles.first()
        assertEquals(16232, bundle.id)
        assertEquals("Humble Choice (June 2026)", bundle.title)
        assertEquals("Humble Bundle", bundle.storeName)
        assertEquals("https://humble.example/c/123", bundle.url)
        assertEquals(8, bundle.gameCount)
        assertEquals("$14.99", bundle.priceDenominated) // cheapest of the two tiers
        assertEquals(2, bundle.games.size) // union across both tiers
        assertEquals("Construction Simulator", bundle.games.first().title)
        assertEquals("cs-box.png", bundle.games.first().boxart)
        assertEquals("/bundles/v1", recordedRequests.single().url.encodedPath)
        assertEquals("US", recordedRequests.single().url.parameters["country"])
    }

    @Test
    fun searchGames_maps_results() = runTest {
        val results = impl.searchGames("halo")

        assertEquals(1, results.size)
        assertEquals("uuid-1", results.first().id)
        assertEquals("Halo", results.first().title)
        assertEquals("banner400.png", results.first().boxart) // prioritized banner400 over boxart
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
    fun fetchDealsForStore_by_store_maps_to_domain_deals_with_synthesized_id_and_null_cheapshark_fields() = runTest {
        val deals = impl.fetchDealsForStore(SearchParameters(storeID = 61, pageSize = 20))

        assertEquals(1, deals.size)
        val deal = deals.first()
        assertEquals("uuid-1:61", deal.dealID) // "<gameUUID>:<shopId>"
        assertEquals(61, deal.storeID)
        assertEquals("uuid-1", deal.gameID)
        assertEquals("Halo", deal.title)
        assertEquals(9.99, deal.salePriceValue)
        assertEquals("\$9.99", deal.salePriceDenominated)
        assertEquals(19.99, deal.normalPriceValue)
        assertEquals(50.0, deal.savings)
        assertEquals("halo-banner300.png", deal.thumb) // prioritized banner300 over boxart
        assertEquals("https://store/halo", deal.url)
        assertTrue(deal.isLowestEver) // flag "N" survives onto the (Room-cached) store-deal model
        assertTrue(deal.isNewHistoricalLow)
        assertFalse(deal.isStoreLow)
        assertTrue(deal.hasVoucher) // voucher survives onto the store-deal model too
        // ITAD provides none of these.
        assertNull(deal.steamRatingPercent)
        assertNull(deal.metacriticScore)
        assertNull(deal.dealRating)
        assertNull(deal.releaseDate)

        val recorded = recordedRequests.single().url
        assertEquals("/deals/v2", recorded.encodedPath)
        assertEquals("61", recorded.parameters["shops"])
    }

    @Test
    fun fetchDealsForStore_by_title_resolves_via_search_then_prices() = runTest {
        val deals = impl.fetchDealsForStore(SearchParameters(title = "halo", exact = true))

        assertEquals(1, deals.size)
        val deal = deals.first()
        // Cheapest deal from PRICES_BODY (shop 35, $7.49), carrying the search game's title/boxart.
        assertEquals("uuid-1:35", deal.dealID)
        assertEquals(35, deal.storeID)
        assertEquals("Halo", deal.title)
        assertEquals(7.49, deal.salePriceValue)
        assertEquals("banner400.png", deal.thumb) // prioritized banner400 over boxart
        assertTrue(deal.isLowestEver) // flag "H" derived from the /games/prices/v3 deal entry
        assertFalse(deal.isNewHistoricalLow) // "H" is at the low, but not *new*
        assertFalse(deal.isStoreLow)
        assertFalse(deal.hasVoucher) // no voucher on this entry

        val paths = recordedRequests.map { it.url.encodedPath }
        assertTrue("/games/search/v1" in paths)
        assertTrue("/games/prices/v3" in paths)
    }

    @Test
    fun fetchDealDetails_parses_game_uuid_and_focus_shop_from_dealId() = runTest {
        val details = impl.fetchDealDetails("uuid-1:35")

        assertEquals("uuid-1", details.gameInfo.gameID)
        assertEquals(35, details.gameInfo.storeID) // focus shop parsed from the dealId
        assertEquals("Halo", details.gameInfo.name) // from /games/info/v2
        assertEquals(7.49, details.gameInfo.salePriceValue)
        assertEquals(4.99, details.cheapestPrice?.priceValue) // historyLow.all

        val paths = recordedRequests.map { it.url.encodedPath }
        assertTrue("/games/info/v2" in paths)
        assertTrue("/games/prices/v3" in paths)
    }

    @Test
    fun fetchGames_by_steam_app_id_uses_the_lookup_bridge() = runTest {
        val games = impl.fetchGames(title = "ignored", steamAppID = 1240, limit = 1)

        assertEquals(1, games.size)
        assertEquals("uuid-1", games.first().gameID)
        assertEquals("Halo", games.first().title)
        assertEquals("/games/lookup/v1", recordedRequests.single().url.encodedPath)
    }

    @Test
    fun fetchGames_by_title_uses_search() = runTest {
        val games = impl.fetchGames(title = "halo", steamAppID = null, limit = 5)

        assertEquals(1, games.size)
        assertEquals("uuid-1", games.first().gameID)
        assertEquals("banner400.png", games.first().thumb) // prioritized banner400 over boxart
        assertEquals("/games/search/v1", recordedRequests.single().url.encodedPath)
    }

    @Test
    fun fetchGameDetails_combines_info_and_prices() = runTest {
        val details = impl.fetchGameDetails("uuid-1")

        assertEquals("Halo", details.info.title)
        assertEquals(4.99, details.cheapestPriceEver.priceValue)
        assertEquals(1, details.deals.size)
        assertEquals(35, details.deals.first().storeID)
        assertEquals("uuid-1:35", details.deals.first().dealID)

        val paths = recordedRequests.map { it.url.encodedPath }
        assertTrue("/games/info/v2" in paths)
        assertTrue("/games/prices/v3" in paths)
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

    @Test
    fun itadHttpClient_retries_on_429_then_succeeds() = runTest {
        var attempts = 0
        val engine = MockEngine { _ ->
            attempts++
            if (attempts == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter, "0"),
                )
            } else {
                respond(
                    content = SHOPS_BODY,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = engine,
        )

        val shops = ItadShopsApi(client).getShops().getOrThrow()

        assertEquals(2, attempts) // initial 429 + one retry that succeeded
        assertTrue(shops.isNotEmpty())
    }

    @Test
    fun itadHttpClient_gives_up_after_max_retries_on_persistent_429() = runTest {
        var attempts = 0
        val engine = MockEngine { _ ->
            attempts++
            respond(
                content = "",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "0"),
            )
        }
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = engine,
        )

        assertFailsWith<Throwable> { ItadShopsApi(client).getShops().getOrThrow() }

        // 1 initial attempt + 3 retries (ITAD_MAX_RETRIES)
        assertEquals(4, attempts)
    }

    private companion object {
        // language=JSON
        private const val SHOPS_BODY = """[ { "id": 61, "title": "Steam" } ]"""

        // language=JSON — the live /deals/v2 envelope: { nextOffset, hasMore, list:[ game{ assets, deal } ] }.
        private const val DEALS_BODY = """{
            "nextOffset": 1,
            "hasMore": false,
            "list": [
              {
                "id": "uuid-1",
                "slug": "halo",
                "title": "Halo",
                "assets": { "boxart": "halo-box.png", "banner300": "halo-banner300.png" },
                "deal": {
                  "shop": { "id": 61, "name": "Steam" },
                  "price": { "amount": 9.99, "amountInt": 999, "currency": "USD" },
                  "regular": { "amount": 19.99, "amountInt": 1999, "currency": "USD" },
                  "cut": 50,
                  "flag": "N",
                  "voucher": "SUMMER10",
                  "url": "https://store/halo"
                }
              }
            ]
          }"""

        // language=JSON — /games/info/v2 returns a single game object (title + assets).
        private const val INFO_BODY =
            """{ "id": "uuid-1", "slug": "halo", "title": "Halo", "assets": { "boxart": "info-box.png", "banner400": "info-banner400.png" } }"""

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
                  "flag": "H",
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
        private const val SEARCH_BODY =
            """[ { "id": "uuid-1", "slug": "halo", "title": "Halo", "assets": { "boxart": "box.png", "banner400": "banner400.png" } } ]"""

        // language=JSON
        private const val LOOKUP_BODY = """{ "found": true, "game": { "id": "uuid-1", "slug": "halo", "title": "Halo" } }"""

        // A bare array of bundles (the live /bundles/v1 shape), one bundle with two tiers.
        // language=JSON
        private const val BUNDLES_BODY = """[
          {
            "id": 16232,
            "title": "Humble Choice (June 2026)",
            "page": { "id": 1, "name": "Humble Bundle", "shopId": 37 },
            "url": "https://humble.example/c/123",
            "details": "https://isthereanydeal.com/bundles/16232",
            "isMature": false,
            "publish": "2026-06-02T20:57:58+02:00",
            "expiry": "2026-07-07T19:00:00+02:00",
            "counts": { "games": 8, "media": 1 },
            "tiers": [
              {
                "price": { "amount": 14.99, "amountInt": 1499, "currency": "USD" },
                "addon": false,
                "games": [ { "id": "g1", "slug": "construction-simulator", "title": "Construction Simulator", "assets": { "boxart": "cs-box.png" } } ]
              },
              {
                "price": { "amount": 24.99, "amountInt": 2499, "currency": "USD" },
                "addon": true,
                "games": [ { "id": "g2", "slug": "another-game", "title": "Another Game", "assets": { "boxart": "ag-box.png" } } ]
              }
            ]
          }
        ]"""
    }
}
