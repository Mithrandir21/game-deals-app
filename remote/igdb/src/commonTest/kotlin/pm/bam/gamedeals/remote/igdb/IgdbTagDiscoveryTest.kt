package pm.bam.gamedeals.remote.igdb

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildKeywordSlugLookupQuery
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildTagsDiscoveryQuery
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildVocabularyQuery
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for the tag-discovery query builders (epic #307). The exact APICalypse string is the
 * contract with IGDB, so the AND `[]` semantics, the ` & ` cross-dimension join, omit-empty, and
 * the popularity sort/paging tail are asserted directly — plus a few source-impl MockEngine paths.
 */
class IgdbTagDiscoveryTest {

    private val logger: Logger = TestingLoggingListener()

    // ---- buildTagsDiscoveryQuery ----

    @Test
    fun discoveryQuery_uses_AND_brackets_within_a_dimension_and_ampersand_across_dimensions() {
        val q = buildTagsDiscoveryQuery(
            genreIds = listOf(12L, 5L),
            themeIds = listOf(17L),
            gameModeIds = emptyList(),
            perspectiveIds = emptyList(),
            keywordIds = emptyList(),
            limit = 30,
            offset = 0,
        )
        // `[]` = contains ALL (AND within a dimension); ` & ` joins dimensions (AND across).
        assertTrue("genres = [12,5] & themes = [17]" in q, "AND semantics within + across dimensions: $q")
        // NEVER the OR `(...)` form for the discovery filter.
        assertFalse("genres = (" in q, "Discovery must not use OR parentheses: $q")
        assertTrue("where genres = [12,5] & themes = [17] & cover != null;" in q, q)
    }

    @Test
    fun discoveryQuery_omits_empty_dimensions_entirely() {
        val q = buildTagsDiscoveryQuery(
            genreIds = listOf(12L),
            themeIds = emptyList(),
            gameModeIds = emptyList(),
            perspectiveIds = emptyList(),
            keywordIds = emptyList(),
            limit = 30,
            offset = 0,
        )
        assertTrue("where genres = [12] & cover != null;" in q, q)
        assertFalse("themes" in q, "Empty themes must be omitted: $q")
        assertFalse("game_modes" in q, "Empty game_modes must be omitted: $q")
        assertFalse("player_perspectives" in q, "Empty perspectives must be omitted: $q")
        assertFalse("keywords" in q, "Empty keywords must be omitted: $q")
    }

    @Test
    fun discoveryQuery_includes_fields_popularity_sort_and_paging_tail() {
        val q = buildTagsDiscoveryQuery(
            genreIds = listOf(1L),
            themeIds = emptyList(),
            gameModeIds = listOf(2L, 3L),
            perspectiveIds = emptyList(),
            keywordIds = listOf(99L),
            limit = 25,
            offset = 50,
        )
        assertTrue("fields id,name,cover.image_id,total_rating_count,external_games.uid,external_games.external_game_source;" in q, q)
        assertTrue("game_modes = [2,3]" in q, q)
        assertTrue("keywords = [99]" in q, q)
        assertTrue("sort total_rating_count desc;" in q, q)
        assertTrue("limit 25; offset 50;" in q, q)
    }

    // ---- buildVocabularyQuery / buildKeywordSlugLookupQuery ----

    @Test
    fun vocabularyQuery_requests_id_name_slug_sorted_by_name() {
        assertEquals("fields id,name,slug; sort name asc; limit 500;", buildVocabularyQuery())
    }

    @Test
    fun keywordSlugLookup_uses_OR_parentheses_and_escapes_slugs() {
        val q = buildKeywordSlugLookupQuery(listOf("roguelike", "souls-like"))
        // OR `(...)` over the curated slug set — the deliberate opposite of the AND `[]` above.
        assertEquals("""fields id,name,slug; where slug = ("roguelike","souls-like"); limit 50;""", q)
    }

    @Test
    fun keywordSlugLookup_escapes_embedded_quotes() {
        val q = buildKeywordSlugLookupQuery(listOf("""a"b"""))
        assertTrue("""slug = ("a\"b")""" in q, "Embedded quote must be backslash-escaped: $q")
    }

    // ---- IgdbSourceImpl ----

    @Test
    fun fetchGamesByTags_posts_discovery_query_to_games_and_maps_steam_app_id_and_rating_count() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) {
            respond(TWO_DISCOVERY_GAMES_BODY, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val filter = IgdbTagFilter(genreIds = persistentListOf(12L), keywordIds = persistentListOf(99L))

        val result = impl.fetchGamesByTags(filter, limit = 30, offset = 0)

        assertEquals(2, result.size)
        assertEquals("Hades", result[0].name)
        assertEquals(1145360, result[0].steamAppId, "Steam app id must be extracted from external_games source=1")
        assertEquals(4200L, result[0].totalRatingCount)
        assertEquals(null, result[1].steamAppId, "Console-only row has no Steam external_game → null")
        val request = recorded.single()
        assertEquals("/v4/games", request.url.encodedPath)
        assertEquals(
            buildTagsDiscoveryQuery(listOf(12L), emptyList(), emptyList(), emptyList(), listOf(99L), 30, 0),
            (request.body as TextContent).text,
        )
    }

    @Test
    fun fetchGamesByTags_short_circuits_empty_filter_with_no_http_call() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) {
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        assertTrue(impl.fetchGamesByTags(IgdbTagFilter(), limit = 30, offset = 0).isEmpty())
        assertEquals(0, recorded.size, "An empty filter must never reach IGDB")
    }

    @Test
    fun fetchTagVocabulary_queries_four_endpoints_and_tags_each_with_its_dimension() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { request ->
            val body = when (request.url.encodedPath) {
                "/v4/genres" -> """[{"id":12,"name":"Role-playing (RPG)","slug":"role-playing-rpg"}]"""
                "/v4/themes" -> """[{"id":17,"name":"Fantasy","slug":"fantasy"},{"id":1,"slug":"no-name"}]"""
                "/v4/game_modes" -> """[{"id":2,"name":"Multiplayer","slug":"multiplayer"}]"""
                "/v4/player_perspectives" -> """[{"id":3,"name":"Bird view / Isometric","slug":"bird-view-isometric"}]"""
                else -> "[]"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = impl.fetchTagVocabulary()

        assertEquals(4, recorded.size, "One call per vocabulary endpoint")
        // Nameless row dropped → 4 named tags total (1 genre + 1 theme + 1 mode + 1 perspective).
        assertEquals(4, result.size)
        assertEquals(IgdbTagDimension.Genre, result.first { it.igdbId == 12L }.dimension)
        assertEquals(IgdbTagDimension.Theme, result.first { it.igdbId == 17L }.dimension)
        assertTrue(result.none { it.igdbId == 1L }, "Nameless vocabulary row must be dropped")
    }

    @Test
    fun fetchCuratedKeywords_short_circuits_empty_slug_list() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) {
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        assertTrue(impl.fetchCuratedKeywords(emptyList()).isEmpty())
        assertEquals(0, recorded.size)
    }

    @Test
    fun fetchCuratedKeywords_maps_rows_as_keyword_dimension() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) {
            respond(
                """[{"id":270,"name":"roguelike","slug":"roguelike"}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchCuratedKeywords(listOf("roguelike"))

        assertEquals(1, result.size)
        assertEquals(IgdbTagDimension.Keyword, result.single().dimension)
        assertEquals(buildKeywordSlugLookupQuery(listOf("roguelike")), (recorded.single().body as TextContent).text)
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
            clock = Clock { 0L },
        )
    }

    private companion object {
        // language=JSON — first row has a Steam external game (source 1); second is console-only.
        const val TWO_DISCOVERY_GAMES_BODY = """[
            {"id":7,"name":"Hades","cover":{"id":1,"image_id":"hco"},"total_rating_count":4200,
             "external_games":[{"id":1,"uid":"1145360","external_game_source":1}]},
            {"id":8,"name":"Console Only","total_rating_count":10,
             "external_games":[{"id":2,"uid":"xyz","external_game_source":11}]}
        ]"""
    }
}
