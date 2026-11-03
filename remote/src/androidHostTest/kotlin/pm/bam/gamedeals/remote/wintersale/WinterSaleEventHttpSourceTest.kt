package pm.bam.gamedeals.remote.wintersale

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
