@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.remote.gamerpower

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * HTTP-level coverage for the [GamerPowerSourceImpl] facade. Stands a Ktor [GamesApi]
 * up against a [MockEngine] so the wiring (path, JSON decoding) is exercised end-to-end
 * inside the module that owns it.
 *
 * Inline [FakeDatetimeParsing] replaces mocking — the test exercises one method on that
 * interface, so a hand-rolled fake is cheaper than a Kotlin/Native-capable mocking lib.
 */
class GamerPowerSourceImplTest {

    private val logger: Logger = TestingLoggingListener()

    private val recordedRequests = mutableListOf<HttpRequestData>()
    private lateinit var impl: GamerPowerSourceImpl

    private val datetimeParsing: DatetimeParsing = FakeDatetimeParsing()

    @BeforeTest
    fun setUp() {
        recordedRequests.clear()

        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        val httpClient = mockHttpClient(json) { request ->
            recordedRequests += request
            when (request.url.encodedPath) {
                "/api/giveaways" -> respond(
                    content = GIVEAWAY_LIST_BODY,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        impl = GamerPowerSourceImpl(
            logger = logger,
            gamesApi = GamesApi(httpClient),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
            datetimeParsing = datetimeParsing
        )
    }

    @Test
    fun fetchGiveaways_hits_giveaways_endpoint_and_decodes_response() = runTest {
        val result = impl.fetchGiveaways()
        assertEquals(1, result.size)
        assertEquals("Free Game", result.first().title)
        assertEquals(GiveawayType.GAME, result.first().type)

        assertEquals(1, recordedRequests.size)
        assertEquals("/api/giveaways", recordedRequests.first().url.encodedPath)
    }

    private companion object {
        // language=JSON
        private const val GIVEAWAY_LIST_BODY = """[
            {
              "id": 1,
              "title": "Free Game",
              "worth": "${'$'}9.99",
              "thumbnail": "thumb.png",
              "image": "image.png",
              "description": "A free game.",
              "instructions": "Click here.",
              "open_giveaway_url": "https://example.com/open",
              "published_date": "2026-01-01 00:00:00",
              "type": "Game",
              "platforms": "PC",
              "end_date": "2026-12-31 23:59:59",
              "users": 1000,
              "status": "Active",
              "gamerpower_url": "https://example.com",
              "open_giveaway": "https://example.com/giveaway"
            }
          ]"""
    }
}

private class FakeDatetimeParsing : DatetimeParsing {
    override fun parseLocalDateTime(seconds: Long): Instant = error("not stubbed")
    override fun parseDatetime(value: String): LocalDateTime = LocalDateTime(2026, 1, 1, 0, 0)
    override fun datetimeToString(localDateTime: LocalDateTime): String = error("not stubbed")
}
