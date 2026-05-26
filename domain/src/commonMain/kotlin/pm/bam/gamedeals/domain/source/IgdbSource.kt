package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.IgdbGame

interface IgdbSource {
    /**
     * Look up the IGDB game record for a Steam title. Returns null when no IGDB record matches
     * the given Steam app ID.
     */
    suspend fun fetchGameBySteamId(steamId: Int): IgdbGame?
}
