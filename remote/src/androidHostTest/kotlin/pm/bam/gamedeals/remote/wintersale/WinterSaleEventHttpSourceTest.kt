package pm.bam.gamedeals.remote.wintersale

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WinterSaleEventHttpSourceTest {
    @Test
    fun requests_the_event_without_caching_and_reads_the_server_date() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/events/winter-sale-2026.json", request.url.encodedPath)
            assertEquals("no-cache, no-store", request.headers[HttpHeaders.CacheControl])
            assertEquals("no-cache", request.headers["Pragma"])
            respond("""{"enabled":false}""", headers = headersOf(HttpHeaders.Date, "Tue, 03 Nov 2026 18:10:00 GMT"))
        })

        val payload = WinterSaleEventHttpSource(client).fetch()

        assertEquals(1_793_729_400_000L, payload.serverDateEpochMillis)
        assertTrue(payload.body.contains("\"enabled\":false"))
        client.close()
    }

    @Test
    fun the_developer_test_switch_reads_the_test_document() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/events/winter-sale-2026-test.json", request.url.encodedPath)
            respond("""{"enabled":false}""", headers = headersOf(HttpHeaders.Date, DATE))
        })

        WinterSaleEventHttpSource(client).fetch(testDocument = true)
        client.close()
    }

    @Test
    fun an_error_status_fails_the_fetch() = runTest {
        // The production client sets expectSuccess, so the repository sees a failure and falls back to its cache.
        for ((status, type) in listOf(HttpStatusCode.NotFound to ClientRequestException::class.java,
            HttpStatusCode.ServiceUnavailable to ServerResponseException::class.java)) {
            val client = HttpClient(MockEngine { respond("nope", status) }) { expectSuccess = true }
            val failure = runCatching { WinterSaleEventHttpSource(client).fetch() }.exceptionOrNull()
            assertTrue("$status gave $failure", type.isInstance(failure))
            client.close()
        }
    }

    @Test
    fun a_redirect_to_the_site_home_page_returns_its_html_for_the_repository_to_reject() = runTest {
        // Today's production state before the route ships: the Worker redirects every unknown path to "/".
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/") {
                respond("<html>Game Deals</html>", headers = headersOf(HttpHeaders.Date, DATE))
            } else {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/"))
            }
        }) { expectSuccess = true }

        val payload = WinterSaleEventHttpSource(client).fetch()

        assertTrue(payload.body.startsWith("<html>"))
        client.close()
    }

    @Test
    fun an_oversized_body_is_refused() = runTest {
        val client = HttpClient(MockEngine { respond("x".repeat(16_385), headers = headersOf(HttpHeaders.Date, DATE)) })

        val failure = runCatching { WinterSaleEventHttpSource(client).fetch() }.exceptionOrNull()
        assertTrue("got $failure", failure is IllegalArgumentException)
        client.close()
    }

    @Test
    fun a_missing_or_unreadable_date_header_gives_no_server_time() = runTest {
        for (headers in listOf(headersOf(), headersOf(HttpHeaders.Date, "yesterday"))) {
            val client = HttpClient(MockEngine { respond("""{"enabled":false}""", headers = headers) })
            assertNull(WinterSaleEventHttpSource(client).fetch().serverDateEpochMillis)
            client.close()
        }
    }

    private companion object {
        const val DATE = "Tue, 03 Nov 2026 18:10:00 GMT"
    }
}
