package pm.bam.gamedeals.remote.igdb

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IgdbWinterSaleSeriesSourceTest {
    @Test
    fun franchise_query_requests_theme_ids_and_classifies_erotic_games() = runTest {
        val client = mockHttpClient(Json { ignoreUnknownKeys = true }) { request ->
            val query = (request.body as TextContent).text
            assertTrue(query.contains("themes.id"))
            assertTrue(query.contains("game_type.type"))
            assertTrue(query.contains("parent_game"))
            assertTrue(query.contains("where franchises = (42)"))
            assertTrue(query.contains("limit 30"))
            respond("""[
                {"id":1,"name":"Safe","category":0,"themes":[{"id":1}],"external_games":[{"uid":"123","name":"Safe","external_game_source":1}]},
                {"id":2,"name":"Adult","category":0,"themes":[{"id":42}],"external_games":[{"uid":"456","name":"Adult","external_game_source":1}]},
                {"id":3,"name":"DLC","category":1,"parent_game":1,"themes":[],"external_games":[{"uid":"789","name":"DLC","external_game_source":1}]},
                {"id":4,"name":"Unknown","category":0,"external_games":[{"uid":"777","name":"Unknown","external_game_source":1}]}
            ]""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val games = IgdbWinterSaleSeriesSource(IgdbGamesApi(client)).fetch(42, 30)
        assertEquals(listOf(false, true, null), games.map { it.erotic })
        assertEquals(listOf(123, 456, 777), games.map { it.steamAppId })
        client.close()
    }
}
