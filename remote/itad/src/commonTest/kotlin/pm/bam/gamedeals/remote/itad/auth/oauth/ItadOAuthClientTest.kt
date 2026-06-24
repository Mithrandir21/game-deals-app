package pm.bam.gamedeals.remote.itad.auth.oauth

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItadOAuthClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val credentials = ItadCredentials(
        apiKey = "key",
        oauthClientId = "client-123",
        redirectUri = "pm.bam.gamedeals://oauth/itad",
    )
    private val tokenJson =
        """{"access_token":"AT","token_type":"Bearer","expires_in":3600,"refresh_token":"RT","scope":"user_info"}"""

    @Test
    fun exchangeCodeForToken_posts_to_token_url_and_parses_response() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val client = mockHttpClient(json) { request ->
            recorded += request
            respond(tokenJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val token = ItadOAuthClient(client, credentials).exchangeCodeForToken(code = "the-code", codeVerifier = "the-verifier")

        assertEquals("AT", token.accessToken)
        assertEquals("RT", token.refreshToken)
        assertEquals(3600, token.expiresIn)
        assertEquals("isthereanydeal.com", recorded.single().url.host)
        // Trailing slash is required — ITAD 302-redirects /oauth/token, which drops the POST body.
        assertTrue(recorded.single().url.encodedPath.endsWith("/oauth/token/"))
    }

    @Test
    fun refreshToken_parses_response() = runTest {
        val client = mockHttpClient(json) { _ ->
            respond(tokenJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val token = ItadOAuthClient(client, credentials).refreshToken("old-refresh")

        assertEquals("AT", token.accessToken)
        assertEquals("RT", token.refreshToken)
    }

    @Test
    fun buildAuthorizeUrl_includes_pkce_and_client_params() {
        val client = mockHttpClient(json) { _ -> respond("") }
        val url = ItadOAuthClient(client, credentials).buildAuthorizeUrl(codeChallenge = "CHAL", state = "STATE")

        assertTrue(url.startsWith("https://isthereanydeal.com/oauth/authorize/"))
        assertTrue(url.contains("client_id=client-123"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge=CHAL"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=STATE"))
        assertTrue(url.contains("scope=user_info"))
    }
}
