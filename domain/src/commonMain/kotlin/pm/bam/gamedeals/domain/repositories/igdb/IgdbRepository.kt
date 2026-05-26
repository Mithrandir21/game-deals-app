package pm.bam.gamedeals.domain.repositories.igdb

import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.source.IgdbSource

interface IgdbRepository {
    suspend fun fetchGameBySteamId(steamId: Int): IgdbGame?
}

internal class IgdbRepositoryImpl(
    private val igdbSource: IgdbSource,
) : IgdbRepository {

    override suspend fun fetchGameBySteamId(steamId: Int): IgdbGame? =
        igdbSource.fetchGameBySteamId(steamId)
}
