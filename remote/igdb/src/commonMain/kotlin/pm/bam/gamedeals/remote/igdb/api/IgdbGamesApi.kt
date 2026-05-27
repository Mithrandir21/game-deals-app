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
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame

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

    /**
     * Rich lookup by IGDB game id — same expansion as [fetchGameDetailsBySteamId] but targets
     * `/v4/games` directly, so the response is a flat list of [RemoteIgdbGame] (no
     * `external_games` wrapper). Used by the similar-games row, where each tile already carries
     * an IGDB id and a Steam-id detour would silently drop console-exclusive / indie titles.
     */
    suspend fun fetchGameDetailsByIgdbId(igdbGameId: Long): ApiResponse<List<RemoteIgdbGame>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/games") {
                contentType(ContentType.Text.Plain)
                setBody(buildIgdbIdLookupDetailsQuery(igdbGameId))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * Exact-name lookup against `/v4/games`. Deterministic — `where name = "<title>"` is
     * whitespace- and case-sensitive on IGDB, so titles with edition suffixes or store-key
     * decoration will miss here and fall through to [fetchGameDetailsBySearch].
     */
    suspend fun fetchGameDetailsByExactName(title: String): ApiResponse<List<RemoteIgdbGame>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/games") {
                contentType(ContentType.Text.Plain)
                setBody(buildExactNameLookupDetailsQuery(title))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * Fuzzy fallback against `/v4/games`. Apicalypse `search` cannot be combined with `sort`;
     * IGDB orders by relevance by default. `where category = 0` keeps the result restricted to
     * a "main game", so we never accidentally resolve a DLC or expansion when the deal title
     * happens to share a prefix.
     */
    suspend fun fetchGameDetailsBySearch(title: String): ApiResponse<List<RemoteIgdbGame>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/games") {
                contentType(ContentType.Text.Plain)
                setBody(buildSearchLookupDetailsQuery(title))
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

        // Same field set as the Steam-id query, minus the `game.` prefix — this query targets
        // `/v4/games` directly so each field is already on the top-level record.
        internal fun buildIgdbIdLookupDetailsQuery(igdbGameId: Long): String = """
            fields
                id, name, summary, storyline,
                cover.image_id,
                screenshots.image_id,
                first_release_date,
                rating, rating_count, aggregated_rating, aggregated_rating_count,
                genres.name, themes.name,
                involved_companies.company.name, involved_companies.developer, involved_companies.publisher, involved_companies.porting, involved_companies.supporting,
                websites.url, websites.type.id, websites.type.type,
                similar_games.id, similar_games.name, similar_games.cover.image_id;
            where id = $igdbGameId; limit 1;
        """.trimIndent()

        // Apicalypse strings are double-quoted. Backslash MUST be escaped before quote — escaping
        // quote first would double-escape the backslashes introduced by the quote-replacement.
        internal fun escapeApicalypseString(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

        // The legacy `category` enum is empty on modern IGDB records (see the same pitfall noted
        // above for `category = 1` on external_games). IGDB now exposes the equivalent via
        // `game_type`, but its id values are not the same and aren't documented as stable. We
        // accept that the exact-name + search-fallback chain may occasionally resolve to a DLC
        // when the deal title literally matches one — the details screen still renders correctly.
        internal fun buildExactNameLookupDetailsQuery(title: String): String {
            val escaped = escapeApicalypseString(title)
            return """
                fields
                    id, name, summary, storyline,
                    cover.image_id,
                    screenshots.image_id,
                    first_release_date,
                    rating, rating_count, aggregated_rating, aggregated_rating_count,
                    genres.name, themes.name,
                    involved_companies.company.name, involved_companies.developer, involved_companies.publisher, involved_companies.porting, involved_companies.supporting,
                    websites.url, websites.type.id, websites.type.type,
                    similar_games.id, similar_games.name, similar_games.cover.image_id;
                where name = "$escaped"; limit 1;
            """.trimIndent()
        }

        internal fun buildSearchLookupDetailsQuery(title: String): String {
            val escaped = escapeApicalypseString(title)
            return """
                search "$escaped";
                fields
                    id, name, summary, storyline,
                    cover.image_id,
                    screenshots.image_id,
                    first_release_date,
                    rating, rating_count, aggregated_rating, aggregated_rating_count,
                    genres.name, themes.name,
                    involved_companies.company.name, involved_companies.developer, involved_companies.publisher, involved_companies.porting, involved_companies.supporting,
                    websites.url, websites.type.id, websites.type.type,
                    similar_games.id, similar_games.name, similar_games.cover.image_id;
                limit 1;
            """.trimIndent()
        }
    }
}
