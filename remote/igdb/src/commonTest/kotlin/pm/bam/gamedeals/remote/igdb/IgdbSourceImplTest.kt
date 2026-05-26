package pm.bam.gamedeals.remote.igdb

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Source-impl coverage for [IgdbSourceImpl]: the auth chain is already covered by
 * [IgdbAuthChainTest], so these tests use the plain [mockHttpClient] (no Auth plugin)
 * and focus on the unwrap + map pipeline.
 */
class IgdbSourceImplTest {

    private val logger: Logger = TestingLoggingListener()

    @Test
    fun fetchSampleGames_decodes_and_maps_to_domain_with_null_and_non_null_summary() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = TWO_GAMES_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchSampleGames()

        assertEquals(2, result.size)
        assertEquals(IgdbGame(id = 1L, name = "Halo", summary = "Master Chief stuff"), result[0])
        assertEquals(2L, result[1].id)
        assertEquals("Some Untold Game", result[1].name)
        assertNull(result[1].summary, "Nullable summary must round-trip as null")
        assertEquals(1, recorded.size)
        assertEquals("/v4/games", recorded.single().url.encodedPath)
    }

    @Test
    fun fetchSampleGames_propagates_http_500_through_the_unwrap_chain() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }

        assertFailsWith<Throwable> { impl.fetchSampleGames() }
        assertEquals(1, recorded.size)
    }

    private fun rig(
        recorded: MutableList<HttpRequestData>,
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
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
        // language=JSON
        const val TWO_GAMES_BODY = """[
            {"id":1,"name":"Halo","summary":"Master Chief stuff"},
            {"id":2,"name":"Some Untold Game"}
        ]"""
    }
}
