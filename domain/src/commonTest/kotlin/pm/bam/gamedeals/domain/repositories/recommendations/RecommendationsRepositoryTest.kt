package pm.bam.gamedeals.domain.repositories.recommendations

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.fixtures.gameDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecommendationsRepositoryTest {

    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit)
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit)
    private val repository = RecommendationsRepositoryImpl(waitlistRepository, collectionRepository, gamesRepository, igdbRepository)

    private fun similar(id: Long, name: String) = IgdbGame.IgdbSimilarGame(id = id, name = name, coverImageId = null)

    private fun detailsWithSteam(steamAppId: Int) =
        gameDetails(info = GameDetails.GameInfo(title = "Seed", steamAppID = steamAppId))

    private fun igdbWith(vararg similar: IgdbGame.IgdbSimilarGame) =
        IgdbGame(id = 1L, name = "Seed", summary = null, similarGames = persistentListOf(*similar))

    @Test
    fun no_seeds_returns_empty_without_touching_igdb() = runTest {
        everySuspend { waitlistRepository.getWaitlist() } returns emptyList()
        everySuspend { collectionRepository.getCollection() } returns emptyList()

        assertTrue(repository.getRecommendations().isEmpty())

        verifySuspend(exactly(0)) { gamesRepository.getGameDetails(any()) }
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsBySteamId(any()) }
    }

    @Test
    fun similar_games_are_returned_deduped() = runTest {
        everySuspend { waitlistRepository.getWaitlist() } returns listOf(WaitlistEntry("g1", "Game One"))
        everySuspend { collectionRepository.getCollection() } returns emptyList()
        everySuspend { gamesRepository.getGameDetails("g1") } returns detailsWithSteam(111)
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(111) } returns
            igdbWith(similar(10, "Rec A"), similar(11, "Rec B"), similar(10, "Rec A dup"))

        val result = repository.getRecommendations()

        assertEquals(listOf(10L, 11L), result.map { it.id })
    }

    @Test
    fun games_already_owned_are_filtered_by_title() = runTest {
        everySuspend { waitlistRepository.getWaitlist() } returns listOf(WaitlistEntry("g1", "Game One"))
        everySuspend { collectionRepository.getCollection() } returns listOf(CollectionEntry("g2", "Rec B"))
        everySuspend { gamesRepository.getGameDetails(any()) } returns detailsWithSteam(111)
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(111) } returns
            igdbWith(similar(10, "Rec A"), similar(11, "Rec B"))

        val result = repository.getRecommendations()

        // "Rec B" is already owned (in the collection), so only "Rec A" survives.
        assertEquals(listOf(10L), result.map { it.id })
    }

    @Test
    fun a_seed_without_a_steam_id_contributes_nothing() = runTest {
        everySuspend { waitlistRepository.getWaitlist() } returns listOf(WaitlistEntry("g1", "Game One"))
        everySuspend { collectionRepository.getCollection() } returns emptyList()
        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails() // steamAppID defaults to null

        assertTrue(repository.getRecommendations().isEmpty())

        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsBySteamId(any()) }
    }
}
