package pm.bam.gamedeals.domain.repositories.games

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.toDeal
import pm.bam.gamedeals.domain.models.toGame
import pm.bam.gamedeals.domain.models.toGameDetails
import pm.bam.gamedeals.domain.models.toRemoteDealsQuery
import pm.bam.gamedeals.domain.transformations.CurrencyTransformation
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSource
import javax.inject.Inject

internal class GamesRepositoryImpl @Inject constructor(
    private val gamesDao: GamesDao,
    private val cheapsharkSource: CheapsharkSource,
    private val currencyTransformation: CurrencyTransformation,
    private val dateTimeFormatter: DateTimeFormatter
) : GamesRepository {

    override fun observeGames(): Flow<List<Game>> =
        gamesDao.observeAllGames()
            .onStart { refreshGames() }

    override suspend fun searchGames(query: String): List<Game> =
        cheapsharkSource.fetchGames(query)
            .map { remoteGame -> remoteGame.toGame(currencyTransformation) }

    @ExperimentalSerializationApi
    override suspend fun searchGames(searchParameters: SearchParameters): List<Deal> =
        cheapsharkSource.fetchDealsForStore(searchParameters.toRemoteDealsQuery())
            .map { remoteDeal -> remoteDeal.toDeal(currencyTransformation) }

    @ExperimentalSerializationApi
    override suspend fun getReleaseGameId(gameTitle: String): Int? =
        cheapsharkSource.fetchDealsForStore(SearchParameters(title = gameTitle, exact = true).toRemoteDealsQuery())
            .firstOrNull()
            ?.gameID

    override suspend fun getGameDetails(dealId: Int): GameDetails =
        cheapsharkSource.fetchGameDetails(dealId.toString()).toGameDetails(currencyTransformation, dateTimeFormatter)

    override suspend fun refreshGames() =
        cheapsharkSource.fetchGames("")
            .map { remoteGame -> remoteGame.toGame(currencyTransformation) }
            .let { gamesDao.addGames(*it.toTypedArray()) }
}
