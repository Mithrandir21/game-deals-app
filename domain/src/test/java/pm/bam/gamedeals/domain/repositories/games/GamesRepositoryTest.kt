package pm.bam.gamedeals.domain.repositories.games

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.source.CheapsharkSource

class GamesRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val gamesDao: GamesDao = mockk()

    private val cheapsharkSource: CheapsharkSource = mockk()

    private val impl = GamesRepository(gamesDao, cheapsharkSource)

    @Test
    fun `observe games with refresh called`() = runTest {
        val game: Game = mockk()

        coEvery { gamesDao.observeAllGames() } returns flowOf(emptyList())
        coEvery { cheapsharkSource.fetchGames("") } returns listOf(game)
        coEvery { gamesDao.addGames(game) } just runs


        val result = impl.observeGames().first()
        Assert.assertTrue(result.isEmpty())

        coVerify(exactly = 1) { gamesDao.observeAllGames() }
        coVerify(exactly = 1) { cheapsharkSource.fetchGames("") }
        coVerify(exactly = 1) { gamesDao.addGames(game) }
    }


    @Test
    fun `get game`() = runTest {
        val id = 1
        val idString = id.toString()
        val gameDetails: GameDetails = mockk()

        coEvery { cheapsharkSource.fetchGameDetails(idString) } returns gameDetails


        val result = impl.getGameDetails(id)
        Assert.assertEquals(gameDetails, result)

        coVerify(exactly = 1) { cheapsharkSource.fetchGameDetails(idString) }
    }


    @Test
    fun `refresh games`() = runTest {
        val game: Game = mockk()

        coEvery { cheapsharkSource.fetchGames("") } returns listOf(game)
        coEvery { gamesDao.addGames(game) } just runs


        impl.refreshGames()

        coVerify(exactly = 1) { cheapsharkSource.fetchGames("") }
        coVerify(exactly = 1) { gamesDao.addGames(game) }
    }

}
