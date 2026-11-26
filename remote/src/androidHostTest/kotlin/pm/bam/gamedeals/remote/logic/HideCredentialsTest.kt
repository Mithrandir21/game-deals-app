package pm.bam.gamedeals.remote.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideCredentialsTest {
    @Test
    fun debug_logging_masks_api_keys_and_tokens_but_keeps_other_headers() = runTest {
        val lines = mutableListOf<String>()
        val client = HttpClient(MockEngine { respond("{}") }) {
            install(Logging) {
                logger = object : Logger { override fun log(message: String) { lines += message } }
                level = LogLevel.HEADERS
                hideCredentials()
            }
        }

        client.get("https://example.com/x") {
            header("ITAD-API-Key", "secret-key")
            header("Client-ID", "secret-client")
            header(HttpHeaders.Authorization, "Bearer secret-token")
            header(HttpHeaders.Accept, "application/json")
        }
        client.close()

        val log = lines.joinToString("\n")
        for (secret in listOf("secret-key", "secret-client", "secret-token")) assertFalse(secret, log.contains(secret))
        assertTrue(log.contains("ITAD-API-Key: ***"))
        assertTrue(log.contains("application/json"))
    }
}
