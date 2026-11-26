package pm.bam.gamedeals.remote.featureflags

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsHttpSourceTest {

    @Test
    fun reads_the_switch_file_with_a_plain_get() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/flags/v1", request.url.encodedPath)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("", request.url.encodedQuery)
            respond("""{"schemaVersion":1,"flags":{}}""")
        })
        assertEquals("""{"schemaVersion":1,"flags":{}}""", FeatureFlagsHttpSource(client).fetch())
        client.close()
    }

    @Test
    fun a_503_for_a_mistyped_file_or_an_oversized_body_fails() = runTest {
        val failing = HttpClient(MockEngine { respond("The switch file isn't valid", HttpStatusCode.ServiceUnavailable) }) { expectSuccess = true }
        assertTrue(runCatching { FeatureFlagsHttpSource(failing).fetch() }.isFailure)
        failing.close()
        val huge = HttpClient(MockEngine { respond("x".repeat(16_385)) })
        assertTrue(runCatching { FeatureFlagsHttpSource(huge).fetch() }.exceptionOrNull() is IllegalArgumentException)
        huge.close()
    }
}
