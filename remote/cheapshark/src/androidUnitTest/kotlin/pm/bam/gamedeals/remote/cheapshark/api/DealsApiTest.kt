package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.getOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the dealID encoding contract: Cheapshark's `/deals` list endpoint
 * returns dealIDs already percent-encoded (e.g. `"...%3D"`), and the `/deals?id=...` lookup
 * expects the same single-encoded form on the wire. The Retrofit-era code captured this with
 * `@Query("id", encoded = true)`; the Ktor port must use `encodedParameters.append` to avoid
 * re-encoding `%` to `%25` and producing a 404.
 */
class DealsApiTest {

    @Test
    fun `getDeal preserves single-encoded id on the wire (does not re-encode)`() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val client = mockClient(recorded)
        val api = DealsApi(client)

        val alreadyEncodedId = "FhBKK4XJ0dmqH9xN1YideiIrjTWPk6dXxgGVFlJzO7s%3D"
        api.getDeal(alreadyEncodedId).getOrThrow()

        assertEquals(1, recorded.size)
        val request = recorded.single()
        assertEquals("/api/1.0/deals", request.url.encodedPath)
        // The wire form must keep the caller's `%3D` intact — NOT `%253D`.
        assertEquals("id=$alreadyEncodedId", request.url.encodedQuery)
    }

    @Test
    fun `getDeals encodes raw query parameters once (sanity check for the non-encoded path)`() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val client = mockClient(recorded, listResponse = true)
        val api = DealsApi(client)

        api.getDeals(storeID = 1, pageSize = 10).getOrThrow()

        assertEquals(1, recorded.size)
        val query = recorded.single().url.encodedQuery
        assertEquals("storeID=1&pageSize=10", query)
    }

    private fun mockClient(
        recorded: MutableList<HttpRequestData>,
        listResponse: Boolean = false,
    ): HttpClient {
        val engine = MockEngine { request ->
            recorded += request
            respond(
                content = if (listResponse) "[]" else DEAL_DETAILS_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private companion object {
        // language=JSON
        const val DEAL_DETAILS_BODY = """{
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
