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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.game
import pm.bam.gamedeals.testing.fixtures.gameDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GamesRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val gamesDao: GamesDao = mock(MockMode.autoUnit)
    private val dealsSource: DealsSource = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private val clock = Clock { now }

    private val impl = GamesRepositoryImpl(logger, gamesDao, dealsSource, clock)

    @Test
    fun observe_games_empty_cache_triggers_refresh_and_stamps_expires() = runTest {
        val game = game()

        every { gamesDao.observeAllGames() } returns flowOf(emptyList())
        everySuspend { gamesDao.getAllGames() } returns emptyList()
        everySuspend { dealsSource.fetchGames("") } returns listOf(game)

        val result = impl.observeGames().first()
        assertTrue(result.isEmpty())

        verify(exactly(1)) { gamesDao.observeAllGames() }
        verifySuspend(exactly(1)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(game.copy(expires = now + GAMES_TTL_MILLIS)) }
    }

    @Test
    fun observe_games_fresh_cache_does_not_refresh() = runTest {
        val fresh = game(expires = now + 10_000)

        every { gamesDao.observeAllGames() } returns flowOf(listOf(fresh))
        everySuspend { gamesDao.getAllGames() } returns listOf(fresh)

        val result = impl.observeGames().first()
        assertTrue(result.isNotEmpty())

        verifySuspend(exactly(0)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.getAllGames() }
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
    fun refresh_games_unforced_expired_entry_fetches_and_stamps_expires() = runTest {
        val expired = game(gameID = "old", expires = now - 1)
        val fetched = game(gameID = "new")

        everySuspend { gamesDao.getAllGames() } returns listOf(expired)
        everySuspend { dealsSource.fetchGames("") } returns listOf(fetched)

        impl.refreshGames()

        verifySuspend(exactly(1)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(fetched.copy(expires = now + GAMES_TTL_MILLIS)) }
    }

    @Test
    fun refresh_games_unforced_all_fresh_skips_fetch() = runTest {
        val fresh = game(expires = now + 10_000)

        everySuspend { gamesDao.getAllGames() } returns listOf(fresh)

        impl.refreshGames()

        verifySuspend(exactly(0)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.getAllGames() }
    }

    @Test
    fun get_price_history_delegates_to_source() = runTest {
        val gameId = "uuid-1"
        val priceHistory = PriceHistory(
            gameID = gameId,
            points = persistentListOf(PriceHistory.PricePoint(1_704_067_200_000L, 9.99, "$9.99")),
        )
        everySuspend { dealsSource.fetchPriceHistory(gameId) } returns priceHistory

        val result = impl.getPriceHistory(gameId)

        assertEquals(priceHistory, result)
        verifySuspend(exactly(1)) { dealsSource.fetchPriceHistory(gameId) }
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
