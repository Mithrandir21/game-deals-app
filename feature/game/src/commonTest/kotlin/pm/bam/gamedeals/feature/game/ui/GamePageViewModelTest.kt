@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.favicon.FaviconResolverImpl
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.notes.NotesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.gameDeal
import pm.bam.gamedeals.testing.fixtures.gameDetails
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamePageViewModelTest : MainDispatcherTest() {

    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit) {
        everySuspend { getPriceHistory(any()) } returns PriceHistory(gameID = "", points = persistentListOf())
        everySuspend { getGameMeta(any()) } returns GameMeta(gameId = "")
        everySuspend { getBundlesForGame(any()) } returns emptyList()
    }
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit) {
        everySuspend { getStore(any()) } returns store()
    }
    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit) {
        everySuspend { fetchGameDetailsBySteamId(any()) } returns null
        everySuspend { fetchGameDetailsByIgdbId(any()) } returns null
        everySuspend { fetchGameDetailsByTitle(any()) } returns null
        everySuspend { fetchTimeToBeat(any()) } returns null
    }
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeIsWaitlisted(any()) } returns flowOf(false)
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIsIgnored(any()) } returns flowOf(false)
    }
    private val notesRepository: NotesRepository = mock(MockMode.autoUnit) {
        every { observeNote(any()) } returns flowOf(null)
    }

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel(args: Map<String, Any?>) = GamePageViewModel(
        savedStateHandle = SavedStateHandle(args),
        logger = TestingLoggingListener(),
        gamesRepository = gamesRepository,
        storesRepository = storesRepository,
        igdbRepository = igdbRepository,
        dealShareTextBuilder = dealShareTextBuilder,
        waitlistRepository = waitlistRepository,
        ignoredRepository = ignoredRepository,
        notesRepository = notesRepository,
        faviconResolver = FaviconResolverImpl(),
    )

    private fun igdb(id: Long = 100L, steamAppId: Int? = null) =
        IgdbGame(id = id, name = "Halo Infinite", summary = "Master Chief returns", steamAppId = steamAppId)

    /** Subscribe, let the load flow run to completion on the test dispatcher, return the final state. */
    private fun TestScope.loadState(args: Map<String, Any?>): GamePageViewModel.GamePageData {
        val emissions = viewModel(args).uiState.observeEmissions(backgroundScope, testDispatcher)
        runCurrent()
        return emissions.last()
    }

    @Test
    fun no_identity_args_emit_Error() = runTest {
        val state = loadState(emptyMap())
        assertEquals(GamePageViewModel.GamePageData.Error, state)
    }

    @Test
    fun deal_entry_loads_details_deals_meta_bundles_and_igdb() = runTest {
        val details = gameDetails(
            info = GameDetails.GameInfo(title = "Halo Infinite", steamAppID = 1240440, thumb = "t"),
            deals = persistentListOf(gameDeal(storeID = 61)),
        )
        val meta = GameMeta(gameId = "g1", developers = persistentListOf("343"), players = GameMeta.Players(recent = 9000))
        val bundle = Bundle(id = 1, title = "Humble", storeName = "Humble", url = "u", expiryEpochMs = null, gameCount = 3, priceDenominated = "$5", games = persistentListOf())
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { gamesRepository.getGameMeta("g1") } returns meta
        everySuspend { gamesRepository.getBundlesForGame("g1") } returns listOf(bundle)
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(1240440) } returns igdb(steamAppId = 1240440)

        val state = loadState(mapOf("gameId" to "g1"))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertEquals("Halo Infinite", state.title)
        assertEquals(details, state.gameDetails)
        assertEquals(1, state.dealDetails.size)
        assertEquals(meta, state.gameMeta)
        assertEquals(listOf(bundle), state.bundles)
        assertNotNull(state.igdbGame)
        // IGDB enrichment came via Steam-appid, not title → no fuzzy-match warning.
        assertEquals(false, state.resolvedByTitle)
    }

    @Test
    fun deal_entry_igdb_failure_still_shows_deals() = runTest {
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, thumb = "t"), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(1240440) } calls { throw Exception("IGDB down") }

        val state = loadState(mapOf("gameId" to "g1"))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertEquals(details, state.gameDetails)
        assertNull(state.igdbGame, "IGDB enrichment failure must not hide the deal side")
    }

    @Test
    fun igdb_entry_resolves_itad_id_and_loads_deals() = runTest {
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(100L) } returns igdb(id = 100L, steamAppId = 1240440)
        everySuspend { gamesRepository.findGameIdBySteamAppId(1240440, "Halo Infinite") } returns "g1"
        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails(deals = persistentListOf(gameDeal()))

        val state = loadState(mapOf("igdbGameId" to 100L))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertNotNull(state.igdbGame)
        assertNotNull(state.gameDetails)
        assertEquals(1, state.dealDetails.size)
        // The resolved ITAD id drives the enrichment fetches.
        verifySuspend(exactly(1)) { gamesRepository.getGameMeta("g1") }
        verifySuspend(exactly(1)) { gamesRepository.getBundlesForGame("g1") }
    }

    @Test
    fun igdb_entry_with_no_itad_match_still_shows_metadata() = runTest {
        // No Steam-appid → no ITAD lookup → metadata-only page (no deals).
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(100L) } returns igdb(id = 100L, steamAppId = null)

        val state = loadState(mapOf("igdbGameId" to 100L))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertNotNull(state.igdbGame)
        assertNull(state.gameDetails)
        assertTrue(state.dealDetails.isEmpty())
        verifySuspend(exactly(0)) { gamesRepository.findGameIdBySteamAppId(any(), any()) }
    }

    @Test
    fun title_entry_with_no_match_emits_NoMatch() = runTest {
        everySuspend { igdbRepository.fetchGameDetailsByTitle("Obscure Game") } returns null

        val state = loadState(mapOf("title" to "Obscure Game"))

        assertEquals(GamePageViewModel.GamePageData.NoMatch("Obscure Game"), state)
    }
}
