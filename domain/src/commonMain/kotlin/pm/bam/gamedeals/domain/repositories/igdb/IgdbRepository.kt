package pm.bam.gamedeals.domain.repositories.igdb

import kotlinx.collections.immutable.ImmutableList
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.source.IgdbSource

interface IgdbRepository {
    suspend fun fetchGameBySteamId(steamId: Int): IgdbGame?
    suspend fun fetchGameDetailsBySteamId(steamId: Int): IgdbGame?
    suspend fun fetchGameDetailsByIgdbId(igdbGameId: Long): IgdbGame?
    suspend fun fetchGameDetailsByTitle(title: String): IgdbGame?
    suspend fun fetchSearchCandidatesByTitle(title: String): ImmutableList<IgdbGame.IgdbSimilarGame>
}

internal class IgdbRepositoryImpl(
    private val igdbSource: IgdbSource,
) : IgdbRepository {

    override suspend fun fetchGameBySteamId(steamId: Int): IgdbGame? =
        igdbSource.fetchGameBySteamId(steamId)

    override suspend fun fetchGameDetailsBySteamId(steamId: Int): IgdbGame? =
        igdbSource.fetchGameDetailsBySteamId(steamId)

    override suspend fun fetchGameDetailsByIgdbId(igdbGameId: Long): IgdbGame? =
        igdbSource.fetchGameDetailsByIgdbId(igdbGameId)

    override suspend fun fetchGameDetailsByTitle(title: String): IgdbGame? =
        igdbSource.fetchGameDetailsByTitle(title)

    override suspend fun fetchSearchCandidatesByTitle(title: String): ImmutableList<IgdbGame.IgdbSimilarGame> =
        igdbSource.fetchSearchCandidatesByTitle(title)
}
