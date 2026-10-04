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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.favicon.FaviconResolverImpl
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.Region
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.RegionalPrice
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.franchise.FranchiseFollowSeeder
import pm.bam.gamedeals.domain.repositories.recentlyviewed.RecentlyViewedRepository
import pm.bam.gamedeals.domain.repositories.notes.NotesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
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
import kotlin.test.assertFalse
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
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit) {
        every { observeIsCollected(any()) } returns flowOf(false)
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIsIgnored(any()) } returns flowOf(false)
    }
    private val notesRepository: NotesRepository = mock(MockMode.autoUnit) {
        every { observeNote(any()) } returns flowOf(null)
    }
    private val followedFranchiseRepository: FollowedFranchiseRepository = mock(MockMode.autoUnit) {
        every { observeFollowedIds() } returns flowOf(emptySet())
    }
    private val franchiseFollowSeeder: FranchiseFollowSeeder = mock(MockMode.autoUnit)
    private val recentlyViewedRepository: RecentlyViewedRepository = mock(MockMode.autoUnit)

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
        collectionRepository = collectionRepository,
        ignoredRepository = ignoredRepository,
        notesRepository = notesRepository,
        faviconResolver = FaviconResolverImpl(),
        followedFranchiseRepository = followedFranchiseRepository,
        franchiseFollowSeeder = franchiseFollowSeeder,
        recentlyViewedRepository = recentlyViewedRepository,
    )

    private fun igdb(id: Long = 100L, steamAppId: Int? = null, name: String = "Halo Infinite") =
        IgdbGame(id = id, name = name, summary = "Master Chief returns", steamAppId = steamAppId)

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
            info = GameDetails.GameInfo(title = "Halo Infinite", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")),
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
        assertEquals(details, state.gameDetailsOrNull)
        assertEquals(1, state.dealDetails.size)
        assertEquals(meta, state.gameMetaOrNull)
        assertEquals(listOf(bundle), state.bundles)
        assertNotNull(state.igdbGameOrNull)
        // IGDB enrichment came via Steam-appid, not title → no fuzzy-match warning.
        assertEquals(false, state.isUncertainMatch)
    }

    @Test
    fun deal_entry_igdb_failure_still_shows_deals() = runTest {
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(1240440) } calls { throw Exception("IGDB down") }

        val state = loadState(mapOf("gameId" to "g1"))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertEquals(details, state.gameDetailsOrNull)
        assertNull(state.igdbGameOrNull, "IGDB enrichment failure must not hide the deal side")
        assertTrue(state.igdb is SectionState.Error, "a thrown IGDB fetch must surface as Error, not silent empty")
    }

    @Test
    fun a_deal_from_an_unresolvable_shop_is_dropped_not_fatal_to_the_page() = runTest {
        // The store cache can lag the deal feed. Throwing here used to escape to the page flow's `catch`
        // and collapse the *whole* game page — title, artwork, IGDB, price history and all — into an
        // error state over one unknown shop (Sentry KOTLIN-E).
        val details = gameDetails(
            info = GameDetails.GameInfo(title = "Halo Infinite", artwork = GameArtwork(banner300 = "t")),
            deals = persistentListOf(gameDeal(storeID = 61), gameDeal(storeID = 999)),
        )
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { storesRepository.getStore(61) } returns store(storeID = 61)
        everySuspend { storesRepository.getStore(999) } returns null // shop the cache doesn't know

        val state = loadState(mapOf("gameId" to "g1"))

        assertTrue(state is GamePageViewModel.GamePageData.Data, "one unknown shop must not error the page")
        assertEquals("Halo Infinite", state.title)
        assertEquals(1, state.dealDetails.size, "only the resolvable deal survives")
        assertEquals(61, state.dealDetails.single().store.storeID)
    }

    @Test
    fun a_page_whose_shops_are_all_unresolvable_still_renders_without_deals() = runTest {
        val details = gameDetails(
            info = GameDetails.GameInfo(title = "Halo Infinite", artwork = GameArtwork(banner300 = "t")),
            deals = persistentListOf(gameDeal(storeID = 999)),
        )
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { storesRepository.getStore(any()) } returns null

        val state = loadState(mapOf("gameId" to "g1"))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertTrue(state.dealDetails.isEmpty())
    }

    @Test
    fun igdb_entry_resolves_itad_id_and_loads_deals() = runTest {
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(100L) } returns igdb(id = 100L, steamAppId = 1240440)
        everySuspend { gamesRepository.findGameIdBySteamAppId(1240440, "Halo Infinite") } returns "g1"
        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails(deals = persistentListOf(gameDeal()))

        val state = loadState(mapOf("igdbGameId" to 100L))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertNotNull(state.igdbGameOrNull)
        assertNotNull(state.gameDetailsOrNull)
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
        assertNotNull(state.igdbGameOrNull)
        assertNull(state.gameDetailsOrNull)
        assertTrue(state.dealDetails.isEmpty())
        // No ITAD id to fetch → the deal/meta facets are a clean Loaded(null), not an Error.
        assertTrue(state.deals is SectionState.Loaded)
        assertTrue(state.gameMeta is SectionState.Loaded)
        verifySuspend(exactly(0)) { gamesRepository.findGameIdBySteamAppId(any(), any()) }
    }

    @Test
    fun regions_tab_lazily_loads_regional_prices_on_selection() = runTest {
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(1240440) } returns igdb(steamAppId = 1240440)
        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails(
            info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")),
            deals = persistentListOf(gameDeal()),
        )
        everySuspend { gamesRepository.getRegionalPrices("g1") } returns listOf(
            RegionalPrice(Country("US", "United States", Region.AMERICAS), 9.99, "$9.99", "https://store/x"),
        )
        val vm = viewModel(mapOf("gameId" to "g1"))
        val emissions = vm.uiState.observeEmissions(backgroundScope, testDispatcher)
        runCurrent()

        // Not loaded until the Regions tab is opened.
        assertEquals(GamePageViewModel.RegionalPricesState.Idle, (emissions.last() as GamePageViewModel.GamePageData.Data).regionalPricesState)

        vm.onRegionsSelected()
        runCurrent()

        val regions = (emissions.last() as GamePageViewModel.GamePageData.Data).regionalPricesState
        assertTrue(regions is GamePageViewModel.RegionalPricesState.Loaded)
        assertEquals(1, regions.items.size)
        verifySuspend(exactly(1)) { gamesRepository.getRegionalPrices("g1") }
    }

    @Test
    fun toggleWaitlist_SignInRequired_when_logged_out() = runTest {
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        val vm = viewModel(mapOf("gameId" to "g1"))
        everySuspend { waitlistRepository.toggleWaitlist("g1") } returns RepoUpdateResult.NOT_LOGGED_IN
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)
        
        vm.toggleWaitlist()
        advanceUntilIdle()
        
        assertEquals(listOf(GamePageViewModel.GameUiEvent.SignInRequired), events)
    }

    @Test
    fun toggleIgnore_SignInRequired_when_logged_out() = runTest {
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        val vm = viewModel(mapOf("gameId" to "g1"))
        everySuspend { ignoredRepository.toggleIgnored("g1") } returns RepoUpdateResult.NOT_LOGGED_IN
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.toggleIgnore()
        advanceUntilIdle()

        assertEquals(listOf(GamePageViewModel.GameUiEvent.SignInRequired), events)
    }

    @Test
    fun setNote_SignInRequired_when_logged_out() = runTest {
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        val vm = viewModel(mapOf("gameId" to "g1"))
        everySuspend { notesRepository.setNote("g1", "text") } returns RepoUpdateResult.NOT_LOGGED_IN
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.setNote("text")
        advanceUntilIdle()

        assertEquals(listOf(GamePageViewModel.GameUiEvent.SignInRequired), events)
    }

    @Test
    fun failed_library_writes_emit_ActionFailed() = runTest {
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        val vm = viewModel(mapOf("gameId" to "g1"))
        everySuspend { waitlistRepository.toggleWaitlist("g1") } returns RepoUpdateResult.FAILED
        everySuspend { notesRepository.setNote("g1", "text") } returns RepoUpdateResult.FAILED
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.toggleWaitlist()
        vm.setNote("text")
        advanceUntilIdle()

        assertEquals(listOf(GamePageViewModel.GameUiEvent.ActionFailed, GamePageViewModel.GameUiEvent.ActionFailed), events)
    }

    @Test
    fun deal_fetch_failure_surfaces_error_on_deals_facet_but_page_still_renders() = runTest {
        // Deals throw, but a title resolves IGDB → the page is still Data with the deal side in Error.
        everySuspend { gamesRepository.getGameDetails("g1") } calls { throw Exception("deals down") }
        everySuspend { igdbRepository.fetchGameDetailsByTitle("Halo Infinite") } returns igdb()

        val state = loadState(mapOf("gameId" to "g1", "title" to "Halo Infinite"))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertTrue(state.deals is SectionState.Error)
        assertNotNull(state.igdbGameOrNull)
    }

    @Test
    fun gameMeta_empty_is_loaded_not_error() = runTest {
        // The repo returns an empty GameMeta (no players/reviews) → genuine-empty, must be Loaded not Error.
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { gamesRepository.getGameMeta("g1") } returns GameMeta(gameId = "g1")

        val state = loadState(mapOf("gameId" to "g1"))

        assertTrue(state is GamePageViewModel.GamePageData.Data)
        assertTrue(state.gameMeta is SectionState.Loaded)
    }

    @Test
    fun retryGameMeta_recovers_after_error() = runTest {
        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal()))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { gamesRepository.getGameMeta("g1") } calls { throw Exception("meta down") }
        val vm = viewModel(mapOf("gameId" to "g1"))
        val emissions = vm.uiState.observeEmissions(backgroundScope, testDispatcher)
        runCurrent()

        assertTrue((emissions.last() as GamePageViewModel.GamePageData.Data).gameMeta is SectionState.Error)

        val meta = GameMeta(gameId = "g1", players = GameMeta.Players(recent = 1000))
        everySuspend { gamesRepository.getGameMeta("g1") } returns meta
        vm.retryGameMeta()
        runCurrent()

        val recovered = emissions.last() as GamePageViewModel.GamePageData.Data
        assertTrue(recovered.gameMeta is SectionState.Loaded)
        assertEquals(meta, recovered.gameMetaOrNull)
    }

    @Test
    fun retryDeals_recovers_and_remaps_deal_details() = runTest {
        everySuspend { gamesRepository.getGameDetails("g1") } calls { throw Exception("deals down") }
        // A title keeps the page alive (IGDB side) while deals are in Error.
        everySuspend { igdbRepository.fetchGameDetailsByTitle("Halo Infinite") } returns igdb()
        val vm = viewModel(mapOf("gameId" to "g1", "title" to "Halo Infinite"))
        val emissions = vm.uiState.observeEmissions(backgroundScope, testDispatcher)
        runCurrent()

        assertTrue((emissions.last() as GamePageViewModel.GamePageData.Data).deals is SectionState.Error)

        val details = gameDetails(info = GameDetails.GameInfo(title = "Halo", steamAppID = 1240440, artwork = GameArtwork(banner300 = "t")), deals = persistentListOf(gameDeal(storeID = 61)))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        vm.retryDeals()
        runCurrent()

        val recovered = emissions.last() as GamePageViewModel.GamePageData.Data
        assertTrue(recovered.deals is SectionState.Loaded)
        assertEquals(details, recovered.gameDetailsOrNull)
        assertEquals(1, recovered.dealDetails.size)
    }

    @Test
    fun steam_id_join_wins_over_the_title_lookup_and_is_certain() = runTest {
        // God of War: the title alone resolves the 2005 game; ITAD's Steam id 1593500 is the 2018 PC release.
        val details = gameDetails(info = GameDetails.GameInfo(title = "God of War", steamAppID = 1593500, artwork = GameArtwork(banner300 = "t")))
        everySuspend { gamesRepository.getGameDetails("g1") } returns details
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(1593500) } returns igdb(id = 19560, steamAppId = 1593500, name = "God of War")
        everySuspend { igdbRepository.fetchGameDetailsByTitle("God of War") } returns igdb(id = 549, name = "God of War")

        val state = loadState(mapOf("gameId" to "g1")) as GamePageViewModel.GamePageData.Data

        assertEquals(19560L, state.igdbGameOrNull?.id)
        assertEquals(false, state.isUncertainMatch)
    }

    @Test
    fun exact_title_match_without_a_steam_id_is_certain() = runTest {
        val info = GameDetails.GameInfo(title = "Ghost of Tsushima DIRECTOR'S CUT", steamAppID = null, artwork = GameArtwork(banner300 = "t"))
        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails(info = info)
        everySuspend { igdbRepository.fetchGameDetailsByTitle("Ghost of Tsushima DIRECTOR'S CUT") } returns igdb(name = "Ghost of Tsushima: Director's Cut")

        val state = loadState(mapOf("gameId" to "g1")) as GamePageViewModel.GamePageData.Data

        assertEquals(false, state.isUncertainMatch)
    }

    @Test
    fun different_title_match_is_flagged_uncertain() = runTest {
        val info = GameDetails.GameInfo(title = "Portal", steamAppID = null, artwork = GameArtwork(banner300 = "t"))
        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails(info = info)
        everySuspend { igdbRepository.fetchGameDetailsByTitle("Portal") } returns igdb(name = "Mac Portal")

        val state = loadState(mapOf("gameId" to "g1")) as GamePageViewModel.GamePageData.Data

        assertEquals(true, state.isUncertainMatch)
    }

    @Test
    fun isSameTitle_ignores_case_punctuation_and_trademarks() {
        assertTrue(isSameTitle("Clair Obscur: Expedition 33", "Clair Obscur: Expedition 33"))
        assertTrue(isSameTitle("No Man's Sky", "No Man's Sky"))
        assertTrue(isSameTitle("The Last of Us™ Part I", "The Last of Us Part I"))
        assertTrue(isSameTitle("Ghost of Tsushima DIRECTOR'S CUT", "Ghost of Tsushima: Director's Cut"))
        assertTrue(isSameTitle("Stray", "Stray"))
        assertFalse(isSameTitle("Portal", "Mac Portal"))
        assertFalse(isSameTitle("Portal", "Portal 2"))
        assertFalse(isSameTitle("™", "®"))
    }
}
