package pm.bam.gamedeals.domain.repositories.favourites

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.FavouritesDao
import pm.bam.gamedeals.domain.models.FavouriteGame

interface FavouritesRepository {
    fun observeFavourites(): Flow<List<FavouriteGame>>
    fun observeFavouriteIds(): Flow<Set<Int>>
    fun observeIsFavourite(gameId: Int): Flow<Boolean>
    suspend fun addFavourite(gameId: Int, title: String, thumb: String)
    suspend fun removeFavourite(gameId: Int)
    suspend fun toggleFavourite(gameId: Int, title: String, thumb: String): Boolean
}

internal class FavouritesRepositoryImpl(
    private val favouritesDao: FavouritesDao,
    private val clock: Clock,
) : FavouritesRepository {

    override fun observeFavourites(): Flow<List<FavouriteGame>> =
        favouritesDao.observeAllFavourites()

    override fun observeFavouriteIds(): Flow<Set<Int>> =
        favouritesDao.observeFavouriteIds().map { it.toSet() }

    override fun observeIsFavourite(gameId: Int): Flow<Boolean> =
        favouritesDao.observeIsFavourite(gameId)

    override suspend fun addFavourite(gameId: Int, title: String, thumb: String) {
        favouritesDao.addFavourites(
            FavouriteGame(
                gameID = gameId,
                title = title,
                thumb = thumb,
                dateAddedMs = clock.nowMillis(),
            )
        )
    }

    override suspend fun removeFavourite(gameId: Int) {
        favouritesDao.removeFavouriteById(gameId)
    }

    override suspend fun toggleFavourite(gameId: Int, title: String, thumb: String): Boolean =
        favouritesDao.toggleFavourite(
            gameId = gameId,
            title = title,
            thumb = thumb,
            dateAddedMs = clock.nowMillis(),
        )
}
