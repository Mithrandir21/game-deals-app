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
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbTag
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbTimeToBeat

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
     * IGDB orders by relevance by default. Returns the single most-relevant rich hit — the
     * candidate-picker path uses [fetchSearchCandidatesByTitle] instead when surfacing
     * alternative matches.
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

    /**
     * Lean ranked candidate list — `search "<title>"; fields id, name, cover.image_id; limit 10;`
     * Used to populate the fuzzy-match candidate picker on the game-details screen when the deal
     * navigated by title. Each candidate's full payload is fetched on demand via
     * [fetchGameDetailsByIgdbId] when the user picks it.
     */
    suspend fun fetchSearchCandidatesByTitle(title: String): ApiResponse<List<RemoteIgdbGame>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/games") {
                contentType(ContentType.Text.Plain)
                setBody(buildSearchCandidatesQuery(title))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * Recently-released games for the Home "new releases" strip (epic #205, Phase 2c — replaces the
     * CheapShark releases endpoint, which ITAD has no equivalent for). Returns games whose
     * `first_release_date` is already in the past and that have a cover, newest first.
     */
    suspend fun fetchNewReleases(nowEpochSeconds: Long, limit: Int): ApiResponse<List<RemoteIgdbGame>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/games") {
                contentType(ContentType.Text.Plain)
                setBody(buildNewReleasesQuery(nowEpochSeconds, limit))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * HowLongToBeat-style completion estimates for a game (epic #291, Phase 2). IGDB exposes these on the
     * dedicated `/v4/game_time_to_beats` endpoint (not via the games dot-expansion), keyed by `game_id`.
     * Returns at most one row; empty list means IGDB has no time-to-beat data for that game.
     */
    suspend fun fetchTimeToBeat(igdbGameId: Long): ApiResponse<List<RemoteIgdbTimeToBeat>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/game_time_to_beats") {
                contentType(ContentType.Text.Plain)
                setBody(buildTimeToBeatQuery(igdbGameId))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * Discover games matching an AND-combined set of tags across IGDB dimensions (epic #307).
     * Each non-empty id list becomes one `<dimension> = [ids]` clause ("contains ALL" — AND within
     * a dimension); the clauses are joined with `&` (AND across dimensions). Sorted by
     * `total_rating_count desc` as a popularity proxy, paginated via [limit]/[offset]. Each row
     * carries `name`, `cover.image_id` and the Steam app id (`external_games`) so the result can be
     * priced through the ITAD bridge without a second IGDB round-trip.
     */
    suspend fun fetchGamesByTags(
        genreIds: List<Long>,
        themeIds: List<Long>,
        gameModeIds: List<Long>,
        perspectiveIds: List<Long>,
        keywordIds: List<Long>,
        limit: Int,
        offset: Int,
    ): ApiResponse<List<RemoteIgdbGame>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/games") {
                contentType(ContentType.Text.Plain)
                setBody(buildTagsDiscoveryQuery(genreIds, themeIds, gameModeIds, perspectiveIds, keywordIds, limit, offset))
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * Enumerate one of IGDB's small vocabulary endpoints — `/v4/genres`, `/v4/themes`,
     * `/v4/game_modes`, `/v4/player_perspectives` — for the tag picker (epic #307). All share the
     * `{ id, name, slug }` shape, so the dimension is supplied by the caller, not the wire.
     */
    suspend fun fetchTagVocabulary(endpoint: String): ApiResponse<List<RemoteIgdbTag>> = try {
        ApiResponse.Success(
            httpClient.post(endpoint) {
                contentType(ContentType.Text.Plain)
                setBody(buildVocabularyQuery())
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /**
     * Resolve a curated set of keyword slugs to IGDB keyword ids (epic #307). The full `/v4/keywords`
     * table is far too large/noisy for a fixed picker, so we hand-pick a small allow-list of slugs
     * and look them up. `where slug = (…)` is OR over the set (we want ANY of the curated slugs) —
     * contrast the AND `[]` used in [buildTagsDiscoveryQuery]. Unknown slugs simply return no row.
     */
    suspend fun fetchKeywordsBySlugs(slugs: List<String>): ApiResponse<List<RemoteIgdbTag>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/keywords") {
                contentType(ContentType.Text.Plain)
                setBody(buildKeywordSlugLookupQuery(slugs))
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
                game.similar_games.id, game.similar_games.name, game.similar_games.cover.image_id,
                game.dlcs.id, game.dlcs.name, game.dlcs.cover.image_id,
                game.expansions.id, game.expansions.name, game.expansions.cover.image_id,
                game.external_games.uid, game.external_games.external_game_source;
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
                similar_games.id, similar_games.name, similar_games.cover.image_id,
                dlcs.id, dlcs.name, dlcs.cover.image_id,
                expansions.id, expansions.name, expansions.cover.image_id,
                external_games.uid, external_games.external_game_source;
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
                    similar_games.id, similar_games.name, similar_games.cover.image_id,
                    dlcs.id, dlcs.name, dlcs.cover.image_id,
                    expansions.id, expansions.name, expansions.cover.image_id;
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
                    similar_games.id, similar_games.name, similar_games.cover.image_id,
                    dlcs.id, dlcs.name, dlcs.cover.image_id,
                    expansions.id, expansions.name, expansions.cover.image_id;
                limit 1;
            """.trimIndent()
        }

        // `/v4/game_time_to_beats` is keyed by `game_id`; times come back in seconds.
        internal fun buildTimeToBeatQuery(igdbGameId: Long): String =
            """fields hastily, normally, completely, count; where game_id = $igdbGameId; limit 1;"""

        internal fun buildSearchCandidatesQuery(title: String): String {
            val escaped = escapeApicalypseString(title)
            return """
                search "$escaped";
                fields id, name, cover.image_id;
                limit 10;
            """.trimIndent()
        }

        // Home "new releases": most-recently-released games that have a cover (the cover filter drops
        // the long tail of cover-less DB stubs). first_release_date is Unix seconds; the `<= now`
        // cutoff keeps out not-yet-released (future-dated) games. Sorted newest-first.
        internal fun buildNewReleasesQuery(nowEpochSeconds: Long, limit: Int): String =
            """
            fields name, cover.image_id, first_release_date;
            where first_release_date != null & first_release_date <= $nowEpochSeconds & cover != null;
            sort first_release_date desc;
            limit $limit;
            """.trimIndent()

        // Tag discovery. AND semantics: `<dim> = [a,b]` means "contains ALL of a and b"
        // (NOT `(a,b)`, which is OR); clauses across dimensions are joined with `&` (also AND). Empty
        // dimensions are omitted entirely. `cover != null` drops the long tail of cover-less stubs and
        // also guarantees a non-empty `where` even if (defensively) every dimension list is empty —
        // though the source layer short-circuits an empty filter before reaching here.
        internal fun buildTagsDiscoveryQuery(
            genreIds: List<Long>,
            themeIds: List<Long>,
            gameModeIds: List<Long>,
            perspectiveIds: List<Long>,
            keywordIds: List<Long>,
            limit: Int,
            offset: Int,
        ): String {
            val tagClauses = listOfNotNull(
                andClause("genres", genreIds),
                andClause("themes", themeIds),
                andClause("game_modes", gameModeIds),
                andClause("player_perspectives", perspectiveIds),
                andClause("keywords", keywordIds),
            )
            val whereClause = (tagClauses + "cover != null").joinToString(" & ")
            return """
                fields id,name,cover.image_id,total_rating_count,external_games.uid,external_games.external_game_source;
                where $whereClause;
                sort total_rating_count desc;
                limit $limit; offset $offset;
            """.trimIndent()
        }

        // `field = [a,b]` is APICalypse's "array contains ALL of these" (AND). Returns null for an
        // empty list so the caller can omit the dimension instead of emitting `field = []`.
        private fun andClause(field: String, ids: List<Long>): String? =
            if (ids.isEmpty()) null else "$field = [${ids.joinToString(",")}]"

        // Shared by the four small vocabulary endpoints (genres/themes/game_modes/player_perspectives).
        // Each is well under 500 rows, so a single page enumerates the whole table.
        internal fun buildVocabularyQuery(): String =
            "fields id,name,slug; sort name asc; limit 500;"

        // `where slug = (…)` is OR over the curated slug set (we want games matching ANY of them) —
        // the deliberate opposite of the AND `[]` used for discovery filtering. Slugs are quoted and
        // escaped like any other APICalypse string literal.
        internal fun buildKeywordSlugLookupQuery(slugs: List<String>): String {
            val quoted = slugs.joinToString(",") { "\"${escapeApicalypseString(it)}\"" }
            return "fields id,name,slug; where slug = ($quoted); limit 50;"
        }
    }
}
