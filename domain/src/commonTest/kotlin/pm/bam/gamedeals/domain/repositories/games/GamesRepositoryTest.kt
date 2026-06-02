package pm.bam.gamedeals.domain.repositories.games

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.testing.fixtures.game
import pm.bam.gamedeals.testing.fixtures.gameDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GamesRepositoryTest {

    private val gamesDao: GamesDao = mock(MockMode.autoUnit)
    private val dealsSource: DealsSource = mock(MockMode.autoUnit)
    private val impl = GamesRepositoryImpl(gamesDao, dealsSource)

    @Test
    fun observe_games_with_refresh_called() = runTest {
        val game = game()

        every { gamesDao.observeAllGames() } returns flowOf(emptyList())
        everySuspend { dealsSource.fetchGames("") } returns listOf(game)

        val result = impl.observeGames().first()
        assertTrue(result.isEmpty())

        verify(exactly(1)) { gamesDao.observeAllGames() }
        verifySuspend(exactly(1)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(game) }
    }

    @Test
    fun get_game() = runTest {
        val id = "1"
        val idString = id
        val gameDetails = gameDetails()

        everySuspend { dealsSource.fetchGameDetails(idString) } returns gameDetails

        val result = impl.getGameDetails(id)
        assertEquals(gameDetails, result)

        verifySuspend(exactly(1)) { dealsSource.fetchGameDetails(idString) }
    }

    @Test
    fun refresh_games() = runTest {
        val game = game()

        everySuspend { dealsSource.fetchGames("") } returns listOf(game)

        impl.refreshGames()

        verifySuspend(exactly(1)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(game) }
    }

    @Test
    fun find_cheapshark_game_id_by_steam_app_id_returns_gameID_when_match_found() = runTest {
        val match = game().copy(gameID = "12345")
        everySuspend { dealsSource.fetchGames(title = "Halo Infinite", steamAppID = 1240440, limit = 1) } returns listOf(match)

        val result = impl.findGameIdBySteamAppId(steamAppId = 1240440, title = "Halo Infinite")

        assertEquals("12345", result)
        verifySuspend(exactly(1)) { dealsSource.fetchGames(title = "Halo Infinite", steamAppID = 1240440, limit = 1) }
    }

    @Test
    fun find_cheapshark_game_id_by_steam_app_id_returns_null_when_no_match() = runTest {
        everySuspend { dealsSource.fetchGames(title = "Unknown Game", steamAppID = 999999, limit = 1) } returns emptyList()

        val result = impl.findGameIdBySteamAppId(steamAppId = 999999, title = "Unknown Game")

        assertEquals(null, result)
    }

    @Test
    fun find_cheapshark_game_id_by_steam_app_id_returns_null_when_source_throws() = runTest {
        everySuspend { dealsSource.fetchGames(title = "Halo Infinite", steamAppID = 1240440, limit = 1) } throws Exception("network down")

        val result = impl.findGameIdBySteamAppId(steamAppId = 1240440, title = "Halo Infinite")

        assertEquals(null, result, "runCatching must swallow the exception and surface null")
    }
}
