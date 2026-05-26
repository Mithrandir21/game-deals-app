package pm.bam.gamedeals.remote.igdb.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.igdb.models.RemoteExternalGameLookup

/**
 * IGDB endpoints. IGDB uses Apicalypse — a plain-text query language sent as the request body.
 * `Content-Type: text/plain` keeps Ktor's ContentNegotiation from trying to JSON-serialize the
 * raw `String` body. The `Client-ID` header is set on the HttpClient's `defaultRequest`; the
 * `Authorization: Bearer …` header is injected by Ktor's Auth plugin.
 */
class IgdbGamesApi(private val httpClient: HttpClient) {

    /**
     * Lean lookup — only the fields needed for the deal-screen summary card.
     * Returns at most one row; empty list means no IGDB record exists for that Steam title.
     */
    suspend fun fetchGameBySteamId(steamAppId: Int): ApiResponse<List<RemoteExternalGameLookup>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/external_games") {
                contentType(ContentType.Text.Plain)
                setBody(buildSteamLookupQuery(steamAppId))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * Rich lookup — pulls cover, screenshots, ratings, dates, genres, themes, companies,
     * websites, and similar games via Apicalypse dot-expansion in a single round-trip.
     * Drives the dedicated game-details screen.
     */
    suspend fun fetchGameDetailsBySteamId(steamAppId: Int): ApiResponse<List<RemoteExternalGameLookup>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/external_games") {
                contentType(ContentType.Text.Plain)
                setBody(buildSteamLookupDetailsQuery(steamAppId))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    internal companion object {
        // IGDB migrated from the legacy `category` enum to a separate `external_game_source` reference
        // table (see /v4/external_game_sources). Steam still has id = 1 there. The old `category` field
        // is empty on modern records (e.g. Halo Infinite, Counter-Strike 2), so filtering on
        // `category = 1` silently returns no rows.
        internal fun buildSteamLookupQuery(steamAppId: Int): String =
            """fields game.id,game.name,game.summary; where uid = "$steamAppId" & external_game_source = 1; limit 1;"""

        internal fun buildSteamLookupDetailsQuery(steamAppId: Int): String = """
            fields
                game.id, game.name, game.summary, game.storyline,
                game.cover.image_id,
                game.screenshots.image_id,
                game.first_release_date,
                game.rating, game.rating_count, game.aggregated_rating, game.aggregated_rating_count,
                game.genres.name, game.themes.name,
                game.involved_companies.company.name, game.involved_companies.developer, game.involved_companies.publisher, game.involved_companies.porting, game.involved_companies.supporting,
                game.websites.url, game.websites.type.id, game.websites.type.type,
                game.similar_games.id, game.similar_games.name, game.similar_games.cover.image_id;
            where uid = "$steamAppId" & external_game_source = 1; limit 1;
        """.trimIndent()
    }
}
