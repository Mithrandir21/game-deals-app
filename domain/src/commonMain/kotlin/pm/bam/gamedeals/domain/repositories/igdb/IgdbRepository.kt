package pm.bam.gamedeals.domain.repositories.igdb

import kotlinx.collections.immutable.ImmutableList
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.source.IgdbSource

interface IgdbRepository {
    suspend fun fetchGameBySteamId(steamId: Int): IgdbGame?
    suspend fun fetchGameDetailsBySteamId(steamId: Int): IgdbGame?
    suspend fun fetchGameDetailsByIgdbId(igdbGameId: Long): IgdbGame?
    suspend fun fetchGameDetailsByTitle(title: String): IgdbGame?
    suspend fun fetchSearchCandidatesByTitle(title: String): ImmutableList<IgdbGame.IgdbSimilarGame>
    suspend fun fetchTimeToBeat(igdbGameId: Long): IgdbGame.IgdbTimeToBeat?

    /** Discover games matching an AND-combined tag [filter], paginated (epic #307). */
    suspend fun fetchGamesByTags(filter: IgdbTagFilter, limit: Int, offset: Int): List<IgdbGame>

    /** Curated genre/theme/game-mode/perspective vocabulary for the tag picker (epic #307). */
    suspend fun fetchTagVocabulary(): List<IgdbTag>

    /** Resolve a curated keyword [slugs] allow-list to domain tags (epic #307). */
    suspend fun fetchCuratedKeywords(slugs: List<String>): List<IgdbTag>
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

    override suspend fun fetchTimeToBeat(igdbGameId: Long): IgdbGame.IgdbTimeToBeat? =
        igdbSource.fetchTimeToBeat(igdbGameId)

    override suspend fun fetchGamesByTags(filter: IgdbTagFilter, limit: Int, offset: Int): List<IgdbGame> =
        igdbSource.fetchGamesByTags(filter, limit, offset)

    override suspend fun fetchTagVocabulary(): List<IgdbTag> =
        igdbSource.fetchTagVocabulary()

    override suspend fun fetchCuratedKeywords(slugs: List<String>): List<IgdbTag> =
        igdbSource.fetchCuratedKeywords(slugs)
}
