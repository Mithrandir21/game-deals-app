package pm.bam.gamedeals.remote.gamerpower

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveawayType
import pm.bam.gamedeals.testing.TestingLoggingListener
import retrofit2.Retrofit

/**
 * HTTP-level coverage for the [GamerPowerSourceImpl] facade. Stands a real
 * Retrofit + the [GamesApi] up against [MockWebServer] so the wiring
 * (path, JSON decoding) is exercised end-to-end inside the module that
 * owns it.
 */
class GamerPowerSourceImplTest {

    private val logger: Logger = TestingLoggingListener()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var impl: GamerPowerSourceImpl

    @OptIn(ExperimentalSerializationApi::class)
    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply { start() }

        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()

        impl = GamerPowerSourceImpl(
            logger = logger,
            gamesApi = retrofit.create(GamesApi::class.java),
            remoteExceptionTransformer = RemoteExceptionTransformer { it }
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchGiveaways hits giveaways endpoint and decodes response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(GIVEAWAY_LIST_BODY))

        val result = impl.fetchGiveaways()
        assertEquals(1, result.size)
        assertEquals("Free Game", result.first().title)
        assertEquals(RemoteGiveawayType.GAME, result.first().type)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/giveaways", recorded.path)
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
