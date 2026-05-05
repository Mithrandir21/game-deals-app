package pm.bam.gamedeals.domain.repositories.games

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
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
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.testing.fixtures.game
import pm.bam.gamedeals.testing.fixtures.gameDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GamesRepositoryTest {

    private val gamesDao: GamesDao = mock(MockMode.autoUnit)
    private val cheapsharkSource: CheapsharkSource = mock(MockMode.autoUnit)
    private val impl = GamesRepositoryImpl(gamesDao, cheapsharkSource)

    @Test
    fun observe_games_with_refresh_called() = runTest {
        val game = game()

        every { gamesDao.observeAllGames() } returns flowOf(emptyList())
        everySuspend { cheapsharkSource.fetchGames("") } returns listOf(game)

        val result = impl.observeGames().first()
        assertTrue(result.isEmpty())

        verify(exactly(1)) { gamesDao.observeAllGames() }
        verifySuspend(exactly(1)) { cheapsharkSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(game) }
    }

    @Test
    fun get_game() = runTest {
        val id = 1
        val idString = id.toString()
        val gameDetails = gameDetails()

        everySuspend { cheapsharkSource.fetchGameDetails(idString) } returns gameDetails

        val result = impl.getGameDetails(id)
        assertEquals(gameDetails, result)

        verifySuspend(exactly(1)) { cheapsharkSource.fetchGameDetails(idString) }
    }

    @Test
    fun refresh_games() = runTest {
        val game = game()

        everySuspend { cheapsharkSource.fetchGames("") } returns listOf(game)

        impl.refreshGames()

        verifySuspend(exactly(1)) { cheapsharkSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(game) }
    }
}
