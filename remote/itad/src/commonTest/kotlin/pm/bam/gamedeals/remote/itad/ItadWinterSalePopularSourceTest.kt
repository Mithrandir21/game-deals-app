package pm.bam.gamedeals.remote.itad

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.logic.itadHttpClient
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItadWinterSalePopularSourceTest {
    @Test
    fun requests_ranked_games_and_preserves_type_and_maturity() = runTest {
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = MockEngine { request ->
                val body = (request.body as TextContent).text
                assertTrue(body.contains("\"country\":\"GB\""))
                assertTrue(body.contains("\"sort\":\"rank\""))
                assertTrue(body.contains("\"limit\":100"))
                assertTrue(body.contains("\"mature\":false"))
                assertTrue(body.contains("\"type\":[1]"))
                respond(
                    """{"list":[{"id":"game","title":"Game","type":"game","mature":false,"deal":{"shop":{"id":1,"name":"Shop"},"price":{"amount":10,"currency":"GBP"},"url":"https://example.com"}},
                        {"id":"dlc","title":"DLC","type":"dlc","mature":null,"deal":{"shop":{"id":1,"name":"Shop"},"price":{"amount":10,"currency":"GBP"},"url":"https://example.com"}}]}""",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val games = ItadWinterSalePopularSource(ItadDealsApi(client)).fetch("GB", false, 100)
        assertEquals(listOf("game", "dlc"), games.map { it.gameId })
        assertEquals(listOf("game", "dlc"), games.map { it.type })
        assertEquals(listOf(false, null), games.map { it.mature })
        client.close()
    }

    @Test
    fun a_real_ranked_reply_maps_titles_artwork_and_maturity() = runTest {
        val client = itadHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            apiKey = "test-key",
            engine = MockEngine { respond(RECORDED_POPULAR_GB, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
        )

        val games = ItadWinterSalePopularSource(ItadDealsApi(client)).fetch("GB", false, 100)

        assertEquals(listOf("Cyberpunk 2077", "Elden Ring", "Red Dead Redemption 2"), games.map { it.title })
        assertTrue(games.all { it.type == "game" && it.mature == false })
        assertEquals(
            "https://assets.isthereanydeal.com/018d937f-2997-7131-b8b9-7c8af4825fa8/banner300.jpg?t=1792687867",
            games.first().artwork.banner300,
        )
        client.close()
    }
}
