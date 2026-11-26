package pm.bam.gamedeals.remote.salecalendar

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleCalendarHttpSourceTest {
    @Test
    fun reads_the_sales_document() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/events/sales.json", request.url.encodedPath)
            respond("""{"schemaVersion":1,"sales":[]}""")
        })
        assertEquals("""{"schemaVersion":1,"sales":[]}""", SaleCalendarHttpSource(client).fetch())
        client.close()
    }

    @Test
    fun an_error_or_an_oversized_body_fails() = runTest {
        val failing = HttpClient(MockEngine { respond("nope", HttpStatusCode.ServiceUnavailable) }) { expectSuccess = true }
        assertTrue(runCatching { SaleCalendarHttpSource(failing).fetch() }.isFailure)
        failing.close()
        val huge = HttpClient(MockEngine { respond("x".repeat(32_769)) })
        assertTrue(runCatching { SaleCalendarHttpSource(huge).fetch() }.exceptionOrNull() is IllegalArgumentException)
        huge.close()
    }
}
