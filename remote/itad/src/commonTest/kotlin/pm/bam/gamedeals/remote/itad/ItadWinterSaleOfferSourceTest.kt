package pm.bam.gamedeals.remote.itad

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.logic.itadHttpClient
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItadWinterSaleOfferSourceTest {
    @Test
    fun overview_request_preserves_one_voucher_inclusive_offer_and_its_fields() = runTest {
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/games/overview/v2", request.url.encodedPath)
                assertEquals("GB", request.url.parameters["country"])
                assertEquals("true", request.url.parameters["vouchers"])
                assertEquals("[\"game-1\",\"game-2\"]", (request.body as TextContent).text)
                respond(FIXTURE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val source = ItadWinterSaleOfferSource(ItadGamesApi(client), TestingLoggingListener(), RemoteExceptionTransformer { it })

        val result = source.fetch(listOf("game-1", "game-2"), "GB")
        val offer = assertNotNull(result["game-1"])

        assertEquals("GB", offer.country)
        assertEquals("Voucher Shop", offer.shopName)
        assertEquals(7.5, offer.price.amount)
        assertEquals("GBP", offer.price.currency)
        assertEquals(25, offer.cutPercent)
        assertEquals("SAVE25", offer.voucher)
        assertEquals("https://example.com/offer", offer.url)
        assertEquals(listOf("Steam"), offer.activationLabels)
        assertNotNull(offer.checkedAtEpochMillis)
        assertNotNull(offer.expiresAtEpochMillis)
        assertTrue(offer.isAtHistoricalLow)
        assertNull(result["game-2"])
        client.close()
    }

    @Test
    fun zero_cut_current_offer_is_not_a_sale() = runTest {
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = MockEngine {
                respond(FIXTURE.replace("\"cut\":25", "\"cut\":0"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val offer = assertNotNull(ItadWinterSaleOfferSource(ItadGamesApi(client), TestingLoggingListener(), RemoteExceptionTransformer { it })
            .fetch(listOf("game-1"), "GB")["game-1"])

        assertFalse(offer.isDiscounted)
        client.close()
    }

    @Test
    fun a_real_reply_leaves_out_games_with_no_price_data() = runTest {
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = MockEngine { respond(RECORDED_OVERVIEW_GB, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
        )
        val requested = listOf(GTA_V, GTA_SAN_ANDREAS, ELDEN_RING, EYE_OF_THE_BEHOLDER)

        val result = ItadWinterSaleOfferSource(ItadGamesApi(client), TestingLoggingListener(), RemoteExceptionTransformer { it })
            .fetch(requested, "GB")

        // The offers repository counts a game missing from a successful reply as "no current offer", not a failure.
        assertEquals(setOf(ELDEN_RING), result.keys)
        val offer = assertNotNull(result[ELDEN_RING])
        assertEquals("PlanetPlay", offer.shopName)
        assertEquals(42.21, offer.price.amount)
        assertEquals("GBP", offer.price.currency)
        assertEquals(51.48, offer.regular?.amount)
        assertEquals(18, offer.cutPercent)
        assertEquals("AUTUMN", offer.voucher)
        assertEquals(listOf("Steam"), offer.activationLabels)
        assertEquals(24.75, offer.historicalLow?.amount)
        assertFalse(offer.isAtHistoricalLow)
        assertNotNull(offer.checkedAtEpochMillis)
        assertNull(offer.expiresAtEpochMillis)
        client.close()
    }

    private companion object {
        const val GTA_V = "018d937f-03e2-7281-a961-037a2d279a92"
        const val GTA_SAN_ANDREAS = "018d937e-f829-73ff-aa7f-95421948da6b"
        const val ELDEN_RING = "018d937f-590c-728b-ac35-38bcff85f086"
        const val EYE_OF_THE_BEHOLDER = "018d97d1-6352-73db-9f18-51d12c520f46"
        val FIXTURE = """
            {
              "prices": [
                {"id":"game-1","current":{
                  "shop":{"id":7,"name":"Voucher Shop"},
                  "price":{"amount":7.5,"currency":"GBP"},
                  "regular":{"amount":10.0,"currency":"GBP"},
                  "cut":25,"url":"https://example.com/offer","voucher":"SAVE25",
                  "drm":[{"id":61,"name":"Steam"},{"id":9999,"name":"Unknown"}],
                  "timestamp":"2026-12-17T18:05:00Z","expiry":"2026-12-20T18:00:00Z"
                },"lowest":{"price":{"amount":7.5,"currency":"GBP"}}},
                {"id":"game-2","current":null,"lowest":null}
              ],
              "bundles": []
            }
        """.trimIndent()
    }
}
