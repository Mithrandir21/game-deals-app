package pm.bam.gamedeals.remote.igdb

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildExactNameLookupDetailsQuery
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildIgdbIdLookupDetailsQuery
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildSearchLookupDetailsQuery
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildSteamLookupDetailsQuery
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.buildSteamLookupQuery
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi.Companion.escapeApicalypseString
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun fetchGameDetailsBySteamId_posts_rich_query_and_returns_fully_mapped_domain() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = ONE_GAME_DETAILS_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameDetailsBySteamId(STEAM_ID)

        val expected = IgdbGame(
            id = 11140L,
            name = "Halo Infinite",
            summary = "Master Chief returns",
            storyline = "When all hope is lost",
            coverImageId = "co1n82",
            screenshotImageIds = persistentListOf("sc1", "sc2"),
            firstReleaseDate = Instant.fromEpochSeconds(1638921600L),
            rating = 78.5,
            ratingCount = 421L,
            aggregatedRating = 87.0,
            aggregatedRatingCount = 24L,
            genres = persistentListOf("Shooter", "Adventure"),
            themes = persistentListOf("Action", "Science fiction"),
            involvedCompanies = persistentListOf(
                IgdbGame.IgdbCompanyRole("343 Industries", IgdbGame.IgdbCompanyRole.Role.Developer),
                IgdbGame.IgdbCompanyRole("Xbox Game Studios", IgdbGame.IgdbCompanyRole.Role.Publisher),
                IgdbGame.IgdbCompanyRole("Skybox Labs", IgdbGame.IgdbCompanyRole.Role.Developer),
                IgdbGame.IgdbCompanyRole("Skybox Labs", IgdbGame.IgdbCompanyRole.Role.Supporting),
            ),
            websites = persistentListOf(
                IgdbGame.IgdbWebsite("https://www.halowaypoint.com", IgdbGame.IgdbWebsite.Category.Official),
                IgdbGame.IgdbWebsite("https://store.steampowered.com/app/1240440", IgdbGame.IgdbWebsite.Category.Steam),
                IgdbGame.IgdbWebsite("https://example.com/unknown", IgdbGame.IgdbWebsite.Category.Other),
                IgdbGame.IgdbWebsite("https://store.playstation.com/en-us/concept/10018646", IgdbGame.IgdbWebsite.Category.PlayStation),
                IgdbGame.IgdbWebsite("https://www.nintendo.com/store/products/halo", IgdbGame.IgdbWebsite.Category.Nintendo),
            ),
            similarGames = persistentListOf(
                IgdbGame.IgdbSimilarGame(id = 1234L, name = "Halo 5: Guardians", coverImageId = "ha5"),
                IgdbGame.IgdbSimilarGame(id = 5678L, name = "Destiny 2", coverImageId = null),
            ),
        )
        assertEquals(expected, result)
        assertEquals(1, recorded.size)
        val request = recorded.single()
        assertEquals("/v4/external_games", request.url.encodedPath)
        assertEquals(buildSteamLookupDetailsQuery(STEAM_ID), (request.body as TextContent).text)
    }

    @Test
    fun fetchGameDetailsBySteamId_returns_null_when_response_is_empty_list() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameDetailsBySteamId(STEAM_ID)

        assertNull(result, "Empty list response must surface as null IgdbGame")
        assertEquals(1, recorded.size)
    }

    @Test
    fun fetchGameDetailsByIgdbId_posts_query_to_games_endpoint_and_returns_mapped_domain() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = ONE_GAME_DIRECT_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameDetailsByIgdbId(IGDB_ID)

        assertEquals(
            IgdbGame(
                id = IGDB_ID,
                name = "Hollow Knight",
                summary = "Indie metroidvania",
                coverImageId = "hkco",
                similarGames = persistentListOf(
                    IgdbGame.IgdbSimilarGame(id = 4242L, name = "Ori and the Blind Forest", coverImageId = "oco"),
                ),
            ),
            result,
        )
        assertEquals(1, recorded.size)
        val request = recorded.single()
        assertEquals("/v4/games", request.url.encodedPath)
        assertEquals(buildIgdbIdLookupDetailsQuery(IGDB_ID), (request.body as TextContent).text)
    }

    @Test
    fun fetchGameDetailsByIgdbId_returns_null_when_response_is_empty_list() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameDetailsByIgdbId(IGDB_ID)

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

    @Test
    fun fetchGameDetailsByTitle_returns_game_on_exact_match_without_firing_search_fallback() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = ONE_GAME_DIRECT_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameDetailsByTitle(TITLE)

        assertEquals("Hollow Knight", result?.name)
        assertEquals(1, recorded.size, "Exact-match hit must short-circuit before search fallback")
        val request = recorded.single()
        assertEquals("/v4/games", request.url.encodedPath)
        assertEquals(buildExactNameLookupDetailsQuery(TITLE), (request.body as TextContent).text)
    }

    @Test
    fun fetchGameDetailsByTitle_falls_back_to_search_when_exact_match_returns_empty_list() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            val body = if (recorded.size == 1) "[]" else ONE_GAME_DIRECT_BODY
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameDetailsByTitle(TITLE)

        assertEquals("Hollow Knight", result?.name)
        assertEquals(2, recorded.size)
        assertEquals("/v4/games", recorded[0].url.encodedPath)
        assertEquals(buildExactNameLookupDetailsQuery(TITLE), (recorded[0].body as TextContent).text)
        assertEquals("/v4/games", recorded[1].url.encodedPath)
        assertEquals(buildSearchLookupDetailsQuery(TITLE), (recorded[1].body as TextContent).text)
    }

    @Test
    fun fetchGameDetailsByTitle_returns_null_when_both_passes_return_empty_list() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = impl.fetchGameDetailsByTitle(TITLE)

        assertNull(result)
        assertEquals(2, recorded.size, "Both exact and search must be attempted before giving up")
    }

    @Test
    fun fetchGameDetailsByTitle_short_circuits_blank_title_with_no_http_call() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(content = "[]", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        assertNull(impl.fetchGameDetailsByTitle(""))
        assertNull(impl.fetchGameDetailsByTitle("   "))
        assertEquals(0, recorded.size, "Blank/whitespace titles must not fire any IGDB request")
    }

    @Test
    fun fetchGameDetailsByTitle_escapes_double_quotes_in_title() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(content = "[]", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        impl.fetchGameDetailsByTitle("""Hyper "Quote" Title""")

        val exactBody = (recorded[0].body as TextContent).text
        assertTrue("""name = "Hyper \"Quote\" Title"""" in exactBody, "Quote must be backslash-escaped in the exact-name body: $exactBody")
    }

    @Test
    fun fetchGameDetailsByTitle_escapes_backslash_before_quote_in_title() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(content = "[]", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        impl.fetchGameDetailsByTitle("""Path\Title""")

        val exactBody = (recorded[0].body as TextContent).text
        assertTrue("""name = "Path\\Title"""" in exactBody, "Backslash must be doubled in the exact-name body: $exactBody")
    }

    @Test
    fun fetchGameDetailsByTitle_propagates_http_500_through_the_unwrap_chain() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val impl = rig(recorded) { _ ->
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }

        assertFailsWith<Throwable> { impl.fetchGameDetailsByTitle(TITLE) }
        assertEquals(1, recorded.size, "Exact-match failure must propagate; search fallback only runs on empty success")
    }

    @Test
    fun buildExactNameLookupDetailsQuery_contains_full_field_list_and_name_predicate() {
        val q = buildExactNameLookupDetailsQuery("Halo Infinite")
        assertTrue("cover.image_id" in q)
        assertTrue("similar_games.id" in q)
        assertTrue("involved_companies.company.name" in q)
        assertTrue("websites.url" in q)
        assertTrue("""where name = "Halo Infinite"""" in q)
        assertTrue("limit 1" in q)
    }

    @Test
    fun buildSearchLookupDetailsQuery_contains_search_clause_and_full_field_list() {
        val q = buildSearchLookupDetailsQuery("Halo Infinite")
        assertTrue("""search "Halo Infinite";""" in q)
        assertTrue("cover.image_id" in q)
        assertTrue("similar_games.id" in q)
        assertTrue("limit 1" in q)
    }

    @Test
    fun escapeApicalypseString_escapes_backslash_first_then_quote() {
        assertEquals("""abc""", escapeApicalypseString("abc"))
        assertEquals("""a\"b""", escapeApicalypseString("""a"b"""))
        assertEquals("""a\\b""", escapeApicalypseString("""a\b"""))
        assertEquals("""a\\\"b""", escapeApicalypseString("""a\"b"""))
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
        const val IGDB_ID = 3151L
        const val TITLE = "Hollow Knight"

        // language=JSON
        const val ONE_GAME_BODY = """[
            {"id":99,"game":{"id":1,"name":"Halo Infinite","summary":"Master Chief stuff"}}
        ]"""

        // Response from `/v4/games where id = N` — flat record, no `external_games` wrapper.
        // language=JSON
        const val ONE_GAME_DIRECT_BODY = """[
            {
                "id": 3151,
                "name": "Hollow Knight",
                "summary": "Indie metroidvania",
                "cover": {"id": 7, "image_id": "hkco"},
                "similar_games": [
                    {"id": 4242, "name": "Ori and the Blind Forest", "cover": {"id": 8, "image_id": "oco"}}
                ]
            }
        ]"""

        // language=JSON
        const val ONE_GAME_DETAILS_BODY = """[
            {
                "id": 99,
                "game": {
                    "id": 11140,
                    "name": "Halo Infinite",
                    "summary": "Master Chief returns",
                    "storyline": "When all hope is lost",
                    "cover": {"id": 12345, "image_id": "co1n82"},
                    "screenshots": [
                        {"id": 1, "image_id": "sc1"},
                        {"id": 2, "image_id": "sc2"}
                    ],
                    "first_release_date": 1638921600,
                    "rating": 78.5,
                    "rating_count": 421,
                    "aggregated_rating": 87.0,
                    "aggregated_rating_count": 24,
                    "genres": [
                        {"id": 5, "name": "Shooter"},
                        {"id": 31, "name": "Adventure"},
                        {"id": 99}
                    ],
                    "themes": [
                        {"id": 1, "name": "Action"},
                        {"id": 18, "name": "Science fiction"}
                    ],
                    "involved_companies": [
                        {"id": 100, "company": {"id": 9, "name": "343 Industries"}, "developer": true, "publisher": false, "porting": false, "supporting": false},
                        {"id": 101, "company": {"id": 10, "name": "Xbox Game Studios"}, "developer": false, "publisher": true, "porting": false, "supporting": false},
                        {"id": 102, "company": {"id": 11, "name": "Skybox Labs"}, "developer": true, "publisher": false, "porting": false, "supporting": true},
                        {"id": 103, "developer": true}
                    ],
                    "websites": [
                        {"id": 1, "url": "https://www.halowaypoint.com", "type": {"id": 1, "type": "Official Website"}},
                        {"id": 2, "url": "https://store.steampowered.com/app/1240440", "type": {"id": 13, "type": "Steam"}},
                        {"id": 3, "url": "https://example.com/unknown", "type": {"id": 999, "type": "Unknown"}},
                        {"id": 4, "type": {"id": 1, "type": "Official Website"}},
                        {"id": 5, "url": "https://store.playstation.com/en-us/concept/10018646", "type": {"id": 998, "type": "PlayStation Store"}},
                        {"id": 6, "url": "https://www.nintendo.com/store/products/halo", "type": {"id": 997, "type": "Nintendo"}}
                    ],
                    "similar_games": [
                        {"id": 1234, "name": "Halo 5: Guardians", "cover": {"id": 50, "image_id": "ha5"}},
                        {"id": 5678, "name": "Destiny 2"},
                        {"id": 9999}
                    ]
                }
            }
        ]"""
    }
}
