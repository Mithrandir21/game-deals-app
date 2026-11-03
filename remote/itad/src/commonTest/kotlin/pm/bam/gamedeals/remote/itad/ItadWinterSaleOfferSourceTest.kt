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

    private companion object {
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
