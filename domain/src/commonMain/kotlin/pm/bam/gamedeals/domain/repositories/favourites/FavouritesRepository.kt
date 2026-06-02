package pm.bam.gamedeals.domain.repositories.favourites

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlin.IgnorableReturnValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.FavouritesDao
import pm.bam.gamedeals.domain.models.FavouriteGame

interface FavouritesRepository {
    fun observeFavourites(): Flow<List<FavouriteGame>>
    fun observeFavouriteIds(): Flow<ImmutableSet<String>>
    fun observeIsFavourite(gameId: String): Flow<Boolean>
    suspend fun addFavourite(gameId: String, title: String, thumb: String)
    suspend fun removeFavourite(gameId: String)
    @IgnorableReturnValue
    suspend fun toggleFavourite(gameId: String, title: String, thumb: String): Boolean
}

internal class FavouritesRepositoryImpl(
    private val favouritesDao: FavouritesDao,
    private val clock: Clock,
) : FavouritesRepository {

    override fun observeFavourites(): Flow<List<FavouriteGame>> =
        favouritesDao.observeAllFavourites()

    override fun observeFavouriteIds(): Flow<ImmutableSet<String>> =
        favouritesDao.observeFavouriteIds().map { it.toImmutableSet() }

    override fun observeIsFavourite(gameId: String): Flow<Boolean> =
        favouritesDao.observeIsFavourite(gameId)

    override suspend fun addFavourite(gameId: String, title: String, thumb: String) {
        favouritesDao.addFavourites(
            FavouriteGame(
                gameID = gameId,
                title = title,
                thumb = thumb,
                dateAddedMs = clock.nowMillis(),
            )
        )
    }

    override suspend fun removeFavourite(gameId: String) {
        favouritesDao.removeFavouriteById(gameId)
    }

    override suspend fun toggleFavourite(gameId: String, title: String, thumb: String): Boolean =
        favouritesDao.toggleFavourite(
            gameId = gameId,
            title = title,
            thumb = thumb,
            dateAddedMs = clock.nowMillis(),
        )
}
