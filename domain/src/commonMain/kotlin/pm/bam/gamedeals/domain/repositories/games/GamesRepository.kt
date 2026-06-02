package pm.bam.gamedeals.domain.repositories.games

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.source.DealsSource

interface GamesRepository {
    fun observeGames(): Flow<List<Game>>
    suspend fun searchGames(query: String): List<Game>

    @ExperimentalSerializationApi
    suspend fun searchGames(searchParameters: SearchParameters): List<Deal>

    @ExperimentalSerializationApi
    suspend fun getReleaseDeal(gameTitle: String): Deal?

    suspend fun getGameDetails(dealId: String): GameDetails
    suspend fun refreshGames()

    suspend fun findGameIdBySteamAppId(steamAppId: Int, title: String): String?
}

internal class GamesRepositoryImpl(
    private val gamesDao: GamesDao,
    private val dealsSource: DealsSource
) : GamesRepository {

    override fun observeGames(): Flow<List<Game>> =
        gamesDao.observeAllGames()
            .onStart { refreshGames() }

    override suspend fun searchGames(query: String): List<Game> =
        dealsSource.fetchGames(query)

    @ExperimentalSerializationApi
    override suspend fun searchGames(searchParameters: SearchParameters): List<Deal> =
        dealsSource.fetchDealsForStore(searchParameters)

    @ExperimentalSerializationApi
    override suspend fun getReleaseDeal(gameTitle: String): Deal? =
        dealsSource.fetchDealsForStore(SearchParameters(title = gameTitle, exact = true))
            .firstOrNull()

    override suspend fun getGameDetails(dealId: String): GameDetails =
        dealsSource.fetchGameDetails(dealId)

    override suspend fun refreshGames() {
        dealsSource.fetchGames("")
            .let { gamesDao.addGames(*it.toTypedArray()) }
    }

    override suspend fun findGameIdBySteamAppId(steamAppId: Int, title: String): String? =
        runCatching {
            dealsSource.fetchGames(title = title, steamAppID = steamAppId, limit = 1)
                .firstOrNull()
                ?.gameID
        }.getOrNull()
}
