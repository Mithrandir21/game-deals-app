package pm.bam.gamedeals.remote.igdb

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildSteamLookupQuery
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Source-impl coverage for [IgdbSourceImpl]. The auth chain is already covered by
 * [IgdbAuthChainTest]; these tests use the plain [mockHttpClient] (no Auth plugin) and focus
 * on the request shape + unwrap/map pipeline against `/v4/external_games`.
 */
class IgdbSourceImplTest {

    private val logger: Logger = TestingLoggingListener()

    @Test
    fun fetchGameBySteamId_posts_apicalypse_query_to_external_games_and_returns_mapped_domain() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = ONE_GAME_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameBySteamId(STEAM_ID)

        assertEquals(IgdbGame(id = 1L, name = "Halo Infinite", summary = "Master Chief stuff"), result)
        assertEquals(1, recorded.size)
        val request = recorded.single()
        assertEquals("/v4/external_games", request.url.encodedPath)
        assertEquals(buildSteamLookupQuery(STEAM_ID), (request.body as TextContent).text)
    }

    @Test
    fun fetchGameBySteamId_returns_null_when_response_is_empty_list() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameBySteamId(STEAM_ID)

        assertNull(result, "Empty list response must surface as null IgdbGame")
        assertEquals(1, recorded.size)
    }

    @Test
    fun fetchGameBySteamId_propagates_http_500_through_the_unwrap_chain() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }

        assertFailsWith<Throwable> { impl.fetchGameBySteamId(STEAM_ID) }
        assertEquals(1, recorded.size)
    }

    private fun rig(
        recorded: MutableList<HttpRequestData>,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): IgdbSourceImpl {
        val client = mockHttpClient(Json { ignoreUnknownKeys = true }) { request ->
            recorded += request
            handler(request)
        }
        return IgdbSourceImpl(
            logger = logger,
            igdbGamesApi = IgdbGamesApi(client),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
        )
    }

    private companion object {
        const val STEAM_ID = 1240440

        // language=JSON
        const val ONE_GAME_BODY = """[
            {"id":99,"game":{"id":1,"name":"Halo Infinite","summary":"Master Chief stuff"}}
        ]"""
    }
}
