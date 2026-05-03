package pm.bam.gamedeals.remote.gamerpower

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.testing.TestingLoggingListener

/**
 * HTTP-level coverage for the [GamerPowerSourceImpl] facade. Stands a Ktor [GamesApi]
 * up against a [MockEngine] so the wiring (path, JSON decoding) is exercised end-to-end
 * inside the module that owns it.
 */
class GamerPowerSourceImplTest {

    private val logger: Logger = TestingLoggingListener()

    private val recordedRequests = mutableListOf<HttpRequestData>()
    private lateinit var impl: GamerPowerSourceImpl

    private val datetimeParsing: DatetimeParsing = mockk {
        every { parseDatetime(any()) } returns LocalDateTime(2026, 1, 1, 0, 0)
    }

    @Before
    fun setUp() {
        recordedRequests.clear()

        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        val mockEngine = MockEngine { request ->
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

        val httpClient = HttpClient(mockEngine) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
        }

        impl = GamerPowerSourceImpl(
            logger = logger,
            gamesApi = GamesApi(httpClient),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
            datetimeParsing = datetimeParsing
        )
    }

    @Test
    fun `fetchGiveaways hits giveaways endpoint and decodes response`() = runTest {
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
