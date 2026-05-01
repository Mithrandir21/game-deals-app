package pm.bam.gamedeals.domain.repositories.games

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.source.CheapsharkSource
import javax.inject.Inject

internal class GamesRepositoryImpl @Inject constructor(
    private val gamesDao: GamesDao,
    private val cheapsharkSource: CheapsharkSource
) : GamesRepository {

    override fun observeGames(): Flow<List<Game>> =
        gamesDao.observeAllGames()
            .onStart { refreshGames() }

    override suspend fun searchGames(query: String): List<Game> =
        cheapsharkSource.fetchGames(query)

    @ExperimentalSerializationApi
    override suspend fun searchGames(searchParameters: SearchParameters): List<Deal> =
        cheapsharkSource.fetchDealsForStore(searchParameters)

    @ExperimentalSerializationApi
    override suspend fun getReleaseGameId(gameTitle: String): Int? =
        cheapsharkSource.fetchDealsForStore(SearchParameters(title = gameTitle, exact = true))
            .firstOrNull()
            ?.gameID

    override suspend fun getGameDetails(dealId: Int): GameDetails =
        cheapsharkSource.fetchGameDetails(dealId.toString())

    override suspend fun refreshGames() =
        cheapsharkSource.fetchGames("")
            .let { gamesDao.addGames(*it.toTypedArray()) }
}
