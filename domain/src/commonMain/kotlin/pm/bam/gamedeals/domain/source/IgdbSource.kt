package pm.bam.gamedeals.domain.source

import kotlinx.collections.immutable.ImmutableList
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.models.Release

interface IgdbSource {

    /**
     * Recently-released games for the Home "new releases" strip (epic #205, Phase 2c). IGDB is the
     * source of releases now — ITAD has no releases endpoint and the old CheapShark one is gone.
     * Returns a capped, newest-first list (empty on miss).
     */
    suspend fun fetchNewReleases(): List<Release>
    /**
     * Lean lookup — only `id`, `name`, `summary` are populated. Drives the deal-screen summary
     * card. Returns null when no IGDB record matches the given Steam app ID.
     */
    suspend fun fetchGameBySteamId(steamId: Int): IgdbGame?

    /**
     * Rich lookup — populates the full [IgdbGame] including cover, screenshots, ratings, dates,
     * genres, themes, companies, websites, and similar games. Drives the game-details screen.
     * Returns null when no IGDB record matches the given Steam app ID.
     */
    suspend fun fetchGameDetailsBySteamId(steamId: Int): IgdbGame?

    /**
     * Rich lookup keyed by IGDB game id (not Steam). Same payload as [fetchGameDetailsBySteamId].
     * Used when navigating from a similar-games tile — tiles already carry an IGDB id, and going
     * through Steam would silently drop games without a Steam mapping.
     */
    suspend fun fetchGameDetailsByIgdbId(igdbGameId: Long): IgdbGame?

    /**
     * Rich lookup keyed by game title — same payload as [fetchGameDetailsBySteamId]. Used when a
     * deal has no `steamAppID` (Humble, Green Man Gaming, …) so the Steam-id path would silently
     * drop it. Resolves in two passes: exact `name` match first, then a fuzzy `search` fallback
     * restricted to main games. Returns null when both passes fail.
     */
    suspend fun fetchGameDetailsByTitle(title: String): IgdbGame?

    /**
     * Lean ranked candidate list for the fuzzy-match picker — up to 10 IGDB games ordered by
     * relevance. Used when the user opens the candidate picker on a title-resolved game details
     * screen to switch to a different match. Returns an empty list on miss or blank title;
     * never null.
     */
    suspend fun fetchSearchCandidatesByTitle(title: String): ImmutableList<IgdbGame.IgdbSimilarGame>

    /**
     * HowLongToBeat-style completion estimates for an IGDB game (epic #291, Phase 2), from the dedicated
     * `/v4/game_time_to_beats` endpoint. Fetched separately from the game lookup and merged onto
     * [IgdbGame.timeToBeat] by the consumer. Returns null when IGDB has no completion data for the game.
     */
    suspend fun fetchTimeToBeat(igdbGameId: Long): IgdbGame.IgdbTimeToBeat?

    /**
     * Discover games matching an AND-combined [filter] of IGDB tags (epic #307), newest-popularity
     * first, paginated via [limit]/[offset]. Each game carries its Steam app id (when known) for
     * downstream ITAD pricing. Returns an empty list for an empty filter — no query is fired.
     */
    suspend fun fetchGamesByTags(filter: IgdbTagFilter, limit: Int, offset: Int): List<IgdbGame>

    /**
     * The curated tag-picker vocabulary (epic #307): all genres, themes, game-modes and player
     * perspectives, each tagged with its dimension. Keywords are fetched separately via
     * [fetchCuratedKeywords] because they come from a hand-picked slug allow-list.
     */
    suspend fun fetchTagVocabulary(): List<IgdbTag>

    /** Resolve a curated keyword [slugs] allow-list to domain [IgdbTag]s (epic #307). */
    suspend fun fetchCuratedKeywords(slugs: List<String>): List<IgdbTag>
}
