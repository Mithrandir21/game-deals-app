package pm.bam.gamedeals.remote.igdb

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.auth.IgdbCredentials
import pm.bam.gamedeals.remote.igdb.auth.IgdbTokenProvider
import pm.bam.gamedeals.remote.igdb.logic.IGDB_HOST
import pm.bam.gamedeals.remote.igdb.logic.igdbHttpClient
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage of the IGDB auth chain. One [MockEngine] is shared between the token client
 * and the IGDB client so the entire refresh round-trip can be observed in request order from a
 * single handler.
 */
class IgdbAuthChainTest {

    @Test
    fun first_request_triggers_token_fetch_and_retries_with_bearer() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val rig = rig(recorded, defaultHandler())

        val result = rig.api.sampleGames().getOrThrow()

        assertEquals(listOf(RemoteIgdbGame(id = 1L, name = "Halo")), result)
        assertEquals(3, recorded.size, "Expected: IGDB-no-auth → Twitch token → IGDB-with-bearer")

        val firstIgdb = recorded[0]
        assertEquals(IGDB_HOST, firstIgdb.url.host)
        assertEquals("/v4/games", firstIgdb.url.encodedPath)
        assertEquals(FAKE_CLIENT_ID, firstIgdb.headers[CLIENT_ID_HEADER])
        assertNull(firstIgdb.headers[HttpHeaders.Authorization])
        assertEquals(SAMPLE_QUERY, (firstIgdb.body as TextContent).text)
        val firstContentType = (firstIgdb.body as TextContent).contentType
        assertEquals(ContentType.Text.Plain.contentType, firstContentType.contentType)
        assertEquals(ContentType.Text.Plain.contentSubtype, firstContentType.contentSubtype)

        val twitch = recorded[1]
        assertEquals("id.twitch.tv", twitch.url.host)
        assertEquals("/oauth2/token", twitch.url.encodedPath)
        val form = (twitch.body as FormDataContent).formData
        assertEquals(FAKE_CLIENT_ID, form["client_id"])
        assertEquals(FAKE_CLIENT_SECRET, form["client_secret"])
        assertEquals("client_credentials", form["grant_type"])

        val retriedIgdb = recorded[2]
        assertEquals(IGDB_HOST, retriedIgdb.url.host)
        assertEquals("/v4/games", retriedIgdb.url.encodedPath)
        assertEquals(FAKE_CLIENT_ID, retriedIgdb.headers[CLIENT_ID_HEADER])
        assertEquals("Bearer $FAKE_TOKEN", retriedIgdb.headers[HttpHeaders.Authorization])
        assertEquals(SAMPLE_QUERY, (retriedIgdb.body as TextContent).text)
    }

    @Test
    fun second_call_reuses_cached_token_without_a_second_twitch_round_trip() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val rig = rig(recorded, defaultHandler())

        rig.api.sampleGames().getOrThrow()
        rig.api.sampleGames().getOrThrow()

        val twitchCalls = recorded.count { it.url.host == "id.twitch.tv" }
        assertEquals(1, twitchCalls, "Twitch token endpoint must be called exactly once across two IGDB calls")

        val authedIgdbCalls = recorded.count {
            it.url.host == IGDB_HOST && it.headers[HttpHeaders.Authorization] == "Bearer $FAKE_TOKEN"
        }
        assertTrue(authedIgdbCalls >= 2, "Both IGDB calls must end up sending the bearer token, got $authedIgdbCalls")
    }

    @Test
    fun twitch_token_failure_propagates_as_ApiResponse_Failure_Exception() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
            when (request.url.host) {
                "id.twitch.tv" -> respond(
                    content = """{"status":400,"message":"bad client"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond(content = "", status = HttpStatusCode.Unauthorized)
            }
        }
        val rig = rig(recorded, handler)

        val response = rig.api.sampleGames()

        assertTrue(response is ApiResponse.Failure.Exception, "Expected Failure.Exception, got $response")
        assertTrue(recorded.any { it.url.host == "id.twitch.tv" }, "Twitch endpoint must have been attempted")
    }

    private fun defaultHandler(): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
        when {
            request.url.host == "id.twitch.tv" && request.url.encodedPath == "/oauth2/token" -> respond(
                content = """{"access_token":"$FAKE_TOKEN","expires_in":5184000,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
            request.url.host == IGDB_HOST && request.url.encodedPath == "/v4/games" -> {
                if (request.headers[HttpHeaders.Authorization] == "Bearer $FAKE_TOKEN") respond(
                    content = """[{"id":1,"name":"Halo"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                ) else respond(content = "", status = HttpStatusCode.Unauthorized)
            }
            else -> respond(content = "", status = HttpStatusCode.NotFound)
        }
    }

    private fun rig(
        recorded: MutableList<HttpRequestData>,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Rig {
        val recordingHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
            recorded += request
            handler(request)
        }
        val engine = MockEngine(recordingHandler)
        val json = Json { ignoreUnknownKeys = true }
        val buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE }
        val credentials = IgdbCredentials(clientId = FAKE_CLIENT_ID, clientSecret = FAKE_CLIENT_SECRET)
        val tokenClient: HttpClient = gameDealsHttpClient(
            json = json,
            buildUtil = buildUtil,
            baseUrl = "https://id.twitch.tv",
            engine = engine,
        )
        val tokenProvider = IgdbTokenProvider(tokenClient = tokenClient, credentials = credentials)
        val igdbClient: HttpClient = igdbHttpClient(
            json = json,
            buildUtil = buildUtil,
            credentials = credentials,
            tokenProvider = tokenProvider,
            engine = engine,
        )
        return Rig(api = IgdbGamesApi(igdbClient))
    }

    private class Rig(val api: IgdbGamesApi)

    private companion object {
        const val FAKE_CLIENT_ID = "fake-id"
        const val FAKE_CLIENT_SECRET = "fake-secret"
        const val FAKE_TOKEN = "FAKE_TOKEN"
        const val CLIENT_ID_HEADER = "Client-ID"
        const val SAMPLE_QUERY = "fields id,name; limit 1;"
    }
}
