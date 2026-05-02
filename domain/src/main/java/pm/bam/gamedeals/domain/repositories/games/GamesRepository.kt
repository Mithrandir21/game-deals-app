package pm.bam.gamedeals.domain.repositories.games

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.db.entities.toEntity
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.source.CheapsharkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamesRepository @Inject internal constructor(
    private val gamesDao: GamesDao,
    private val cheapsharkSource: CheapsharkSource
) {

    fun observeGames(): Flow<List<Game>> =
        gamesDao.observeAllGames()
            .onStart { refreshGames() }

    suspend fun searchGames(query: String): List<Game> =
        cheapsharkSource.fetchGames(query)

    @ExperimentalSerializationApi
    suspend fun searchGames(searchParameters: SearchParameters): List<Deal> =
        cheapsharkSource.fetchDealsForStore(searchParameters)

    @ExperimentalSerializationApi
    suspend fun getReleaseGameId(gameTitle: String): Int? =
        cheapsharkSource.fetchDealsForStore(SearchParameters(title = gameTitle, exact = true))
            .firstOrNull()
            ?.gameID

    suspend fun getGameDetails(dealId: Int): GameDetails =
        cheapsharkSource.fetchGameDetails(dealId.toString())

    suspend fun refreshGames() =
        cheapsharkSource.fetchGames("")
            .map { it.toEntity() }
            .let { gamesDao.addGameEntities(*it.toTypedArray()) }
}
