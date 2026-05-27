package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.IgdbGame

interface IgdbSource {
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
}
