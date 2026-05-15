package pm.bam.gamedeals.domain.repositories.favourites

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.FavouritesDao
import pm.bam.gamedeals.domain.models.FavouriteGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavouritesRepositoryTest {

    private val favouritesDao: FavouritesDao = mock(MockMode.autoUnit)
    private val clock = Clock { FIXED_NOW_MS }
    private val impl = FavouritesRepositoryImpl(favouritesDao, clock)

    @Test
    fun add_favourite_stamps_clock_now() = runTest {
        impl.addFavourite(gameId = 7, title = "Game 7", thumb = "thumb7")

        verifySuspend(exactly(1)) {
            favouritesDao.addFavourites(
                FavouriteGame(gameID = 7, title = "Game 7", thumb = "thumb7", dateAddedMs = FIXED_NOW_MS)
            )
        }
    }

    @Test
    fun remove_favourite_delegates_to_dao() = runTest {
        impl.removeFavourite(gameId = 9)

        verifySuspend(exactly(1)) { favouritesDao.removeFavouriteById(9) }
    }

    @Test
    fun toggle_delegates_to_dao_and_returns_new_state_true() = runTest {
        everySuspend {
            favouritesDao.toggleFavourite(
                gameId = 7,
                title = "Game 7",
                thumb = "thumb7",
                dateAddedMs = FIXED_NOW_MS,
            )
        } returns true

        val now = impl.toggleFavourite(gameId = 7, title = "Game 7", thumb = "thumb7")
        assertTrue(now)

        verifySuspend(exactly(1)) {
            favouritesDao.toggleFavourite(
                gameId = 7,
                title = "Game 7",
                thumb = "thumb7",
                dateAddedMs = FIXED_NOW_MS,
            )
        }
    }

    @Test
    fun toggle_delegates_to_dao_and_returns_new_state_false() = runTest {
        everySuspend {
            favouritesDao.toggleFavourite(
                gameId = 7,
                title = "Game 7",
                thumb = "thumb7",
                dateAddedMs = FIXED_NOW_MS,
            )
        } returns false

        val now = impl.toggleFavourite(gameId = 7, title = "Game 7", thumb = "thumb7")
        assertFalse(now)

        verifySuspend(exactly(1)) {
            favouritesDao.toggleFavourite(
                gameId = 7,
                title = "Game 7",
                thumb = "thumb7",
                dateAddedMs = FIXED_NOW_MS,
            )
        }
    }

    @Test
    fun observe_favourite_ids_maps_list_to_set() = runTest {
        every { favouritesDao.observeFavouriteIds() } returns flowOf(listOf(1, 2, 2, 3))

        val ids = impl.observeFavouriteIds().first()
        assertEquals(setOf(1, 2, 3), ids)
    }

    private companion object {
        const val FIXED_NOW_MS = 1_700_000_000_000L
    }
}
