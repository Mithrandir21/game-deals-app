package pm.bam.gamedeals.remote.signals

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtilImpl
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

class GameSignalsHttpSourceTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val id = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5"
    private val id2 = "018d937f-1ae9-734c-ba47-bd357cf07edd"

    private fun source(status: HttpStatusCode = HttpStatusCode.OK, body: String, seen: MutableList<HttpRequestData> = mutableListOf()) =
        GameSignalsHttpSource(
            gameDealsHttpClient(
                json = json,
                buildUtil = RemoteBuildUtilImpl(RemoteBuildType.RELEASE),
                baseUrl = "https://gamedeals.bam.pm",
                engine = MockEngine { request ->
                    seen += request
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        )

    // The Worker's answer shape (store/site/signals-routes.mjs), including a field this app doesn't read yet.
    private val workerAnswer = """
        {"games": {"$id": {"updated": 1795000000000, "weeklyMaxCut": [0, 50, 120, -5],
          "review": {"score": 98, "count": 250000, "source": "Steam"},
          "players": {"recent": 4000, "day": 9000, "week": 15000, "peak": 37000},
          "waitlisted": 45000, "collected": 120000, "rank": 12, "later": true}},
         "pending": ["$id2"]}
    """.trimIndent()

    @Test
    fun posts_the_ids_in_the_body_and_reads_the_games_and_pending_ones() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val batch = source(body = workerAnswer, seen = seen).fetch(listOf(id, id2))

        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://gamedeals.bam.pm/api/signals/v1", request.url.toString())
        assertEquals("""{"ids":["$id","$id2"]}""", (request.body as TextContent).text)

        assertEquals(
            listOf(
                GameSignals(
                    gameId = id, updatedEpochMs = 1_795_000_000_000, weeklyMaxCut = listOf(0, 50, 100, 0),
                    reviewScore = 98, reviewCount = 250_000, reviewSource = "Steam", playersRecent = 4000, playersPeak = 37_000,
                    waitlisted = 45_000, collected = 120_000, rank = 12,
                ),
            ),
            batch.games,
        )
        assertEquals(listOf(id2), batch.pending)
    }

    @Test
    fun missing_parts_are_null() = runTest {
        val batch = source(body = """{"games": {"$id": {"updated": 5, "review": null, "players": null}}}""").fetch(listOf(id))
        val signals = batch.games.single()
        assertEquals(emptyList<Int>(), signals.weeklyMaxCut)
        assertEquals(null, signals.reviewScore)
        assertEquals(null, signals.playersRecent)
        assertEquals(emptyList<String>(), batch.pending)
    }

    @Test
    fun an_error_status_fails_the_call() = runTest {
        val failure = runCatching { source(HttpStatusCode.ServiceUnavailable, """{"error":"not configured"}""").fetch(listOf(id)) }
            .exceptionOrNull()
        assertTrue("got $failure", failure is ServerResponseException)
    }

    @Test
    fun more_than_fifty_ids_are_refused_before_calling() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val failure = runCatching { source(body = workerAnswer, seen = seen).fetch(List(51) { id }) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(seen.isEmpty())
    }
}
