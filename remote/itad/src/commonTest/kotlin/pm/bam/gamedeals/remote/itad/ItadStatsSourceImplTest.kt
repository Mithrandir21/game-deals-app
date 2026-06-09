package pm.bam.gamedeals.remote.itad

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
import pm.bam.gamedeals.domain.models.SUPPORTED_COUNTRIES
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.api.ItadStatsApi
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HTTP-level coverage for [ItadStatsSourceImpl] (epic #219, Phase 5.1) against a [MockEngine]: the
 * stats rankings map to RankedGame, a batched `/games/prices/v3` enriches the cheapest price per game,
 * and games without a price entry are left null (best-effort).
 */
@OptIn(ExperimentalSerializationApi::class)
class ItadStatsSourceImplTest {

    private val logger: Logger = TestingLoggingListener()
    private val recordedRequests = mutableListOf<HttpRequestData>()
    private lateinit var impl: ItadStatsSourceImpl

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
                "/stats/most-waitlisted/v1" -> WAITLISTED_BODY
                "/stats/most-collected/v1" -> COLLECTED_BODY
                "/stats/most-popular/v1" -> POPULAR_BODY
                "/games/prices/v3" -> PRICES_BODY
                "/games/info/v2" -> {
                    if (request.url.parameters["id"] == "g1") INFO_BODY_G1 else ""
                }
                else -> ""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        impl = ItadStatsSourceImpl(
            logger = logger,
            statsApi = ItadStatsApi(httpClient),
            gamesApi = ItadGamesApi(httpClient),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
            regionRepository = regionRepository,
        )
    }

    @Test
    fun fetchMostWaitlisted_maps_rankings_and_enriches_cheapest_price_and_boxart() = runTest {
        val ranked = impl.fetchMostWaitlisted(limit = 2)

        assertEquals(2, ranked.size)
        assertEquals("g1", ranked.first().gameId)
        assertEquals("Game One", ranked.first().title)
        assertEquals("https://assets/g1_banner400.jpg", ranked.first().boxart) // prioritized banner400 over boxart
        assertEquals("$5.99", ranked.first().priceDenominated) // cheapest of g1's two deals

        // g2 has no price entry and no info entry → left null (best-effort enrichment)
        assertEquals("g2", ranked[1].gameId)
        assertNull(ranked[1].priceDenominated)
        assertNull(ranked[1].boxart)

        val stats = recordedRequests.first().url
        assertEquals("/stats/most-waitlisted/v1", stats.encodedPath)
        assertEquals("2", stats.parameters["limit"])
        assertEquals("0", stats.parameters["offset"])
        assertTrue(recordedRequests.any { it.url.encodedPath == "/games/prices/v3" })
        assertTrue(recordedRequests.any { it.url.encodedPath == "/games/info/v2" && it.url.parameters["id"] == "g1" })
    }

    @Test
    fun fetchMostCollected_hits_collected_endpoint() = runTest {
        impl.fetchMostCollected()
        assertEquals("/stats/most-collected/v1", recordedRequests.first().url.encodedPath)
    }

    @Test
    fun fetchMostPopular_hits_popular_endpoint() = runTest {
        impl.fetchMostPopular()
        assertEquals("/stats/most-popular/v1", recordedRequests.first().url.encodedPath)
    }

    private companion object {
        private const val WAITLISTED_BODY = """[
            { "position": 1, "id": "g1", "slug": "game-one", "title": "Game One", "type": "game", "mature": false, "count": 100 },
            { "position": 2, "id": "g2", "slug": "game-two", "title": "Game Two", "type": "game", "mature": false, "count": 90 }
        ]"""
        private const val COLLECTED_BODY = """[ { "position": 1, "id": "g1", "title": "Game One", "count": 50 } ]"""
        private const val POPULAR_BODY = """[ { "position": 1, "id": "g1", "title": "Game One", "count": 50 } ]"""
        private const val INFO_BODY_G1 = """{
            "id": "g1",
            "title": "Game One",
            "assets": {
                "boxart": "https://assets/g1_box.jpg",
                "banner400": "https://assets/g1_banner400.jpg"
            }
        }"""
        private const val PRICES_BODY = """[
            {
              "id": "g1",
              "historyLow": { "all": { "amount": 4.99, "amountInt": 499, "currency": "USD" } },
              "deals": [
                { "shop": { "id": 61, "name": "Steam" }, "price": { "amount": 9.99, "amountInt": 999, "currency": "USD" }, "cut": 50, "url": "https://steam/g1" },
                { "shop": { "id": 35, "name": "GOG" }, "price": { "amount": 5.99, "amountInt": 599, "currency": "USD" }, "cut": 70, "url": "https://gog/g1" }
              ]
            }
          ]"""
    }
}
