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
}
