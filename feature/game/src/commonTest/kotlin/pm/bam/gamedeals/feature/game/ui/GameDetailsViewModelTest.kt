@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.favicon.FaviconResolverImpl
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameDetailsViewModelTest : MainDispatcherTest() {

    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(steamAppId: Int?): GameDetailsViewModel = GameDetailsViewModel(
        savedStateHandle = if (steamAppId == null) SavedStateHandle() else SavedStateHandle(mapOf("steamAppId" to steamAppId)),
        logger = TestingLoggingListener(),
        igdbRepository = igdbRepository,
        gamesRepository = gamesRepository,
        faviconResolver = FaviconResolverImpl(),
    )

    private fun createViewModelByIgdbId(igdbGameId: Long): GameDetailsViewModel = GameDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("igdbGameId" to igdbGameId)),
        logger = TestingLoggingListener(),
        igdbRepository = igdbRepository,
        gamesRepository = gamesRepository,
        faviconResolver = FaviconResolverImpl(),
    )

    private fun createViewModelByTitle(title: String): GameDetailsViewModel = GameDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("title" to title)),
        logger = TestingLoggingListener(),
        igdbRepository = igdbRepository,
        gamesRepository = gamesRepository,
        faviconResolver = FaviconResolverImpl(),
    )

    @Test
    fun initially_emits_Loading_while_fetch_in_flight() = runTest {
        val steamId = 1240440
        // Mock that never resolves so the flow stays in Loading state.
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } calls {
            delay(Long.MAX_VALUE)
            null
        }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Loading, emissions.last())
    }

    @Test
    fun successful_fetch_emits_Data() = runTest {
        val steamId = 1240440
        val igdb = igdbDetails()
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdb

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(igdb), emissions.last())
    }

    @Test
    fun repository_returns_null_emits_Error() = runTest {
        val steamId = 1240440
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns null

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun exception_during_fetch_emits_Error() = runTest {
        val steamId = 1240440
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } calls { throw Exception("IGDB down") }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun missing_steamAppId_emits_Error_without_calling_repository() = runTest {
        val viewModel = createViewModel(steamAppId = null)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsBySteamId(any()) }
    }

    @Test
    fun reload_re_fetches_and_emits_fresh_Data() = runTest {
        val steamId = 1240440
        val first = igdbDetails(summary = "first")
        val second = igdbDetails(summary = "second")

        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } sequentially {
            returns(first)
            returns(second)
        }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(first), emissions.last())

        viewModel.reload()
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(second), emissions.last())
    }

    @Test
    fun data_state_precomputes_favicon_urls_for_each_website() = runTest {
        val steamId = 1240440
        val websites = persistentListOf(
            IgdbGame.IgdbWebsite("https://store.steampowered.com/app/1240440", IgdbGame.IgdbWebsite.Category.Steam),
            IgdbGame.IgdbWebsite("https://example.com/", IgdbGame.IgdbWebsite.Category.Official),
            IgdbGame.IgdbWebsite("not-a-url", IgdbGame.IgdbWebsite.Category.Other),
        )
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdbDetails(websites = websites)

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(3, data.websites.size)
        assertEquals(
            WebsiteUiModel(
                url = "https://store.steampowered.com/app/1240440",
                category = IgdbGame.IgdbWebsite.Category.Steam,
                faviconUrl = "https://store.steampowered.com/favicon.ico",
                faviconCacheKey = "brand:steam",
            ),
            data.websites[0],
        )
        // Unknown brand → host-based favicon URL, null cache key (Coil falls back to URL-as-key).
        assertEquals("https://example.com/favicon.ico", data.websites[1].faviconUrl)
        assertNull(data.websites[1].faviconCacheKey)
        assertNull(data.websites[2].faviconUrl, "Malformed URL must produce null favicon")
        assertNull(data.websites[2].faviconCacheKey)
    }

    @Test
    fun data_state_routes_epic_subdomains_to_canonical_url_and_brand_key() = runTest {
        // Empirical finding (FaviconProbe diagnostic): `store.epicgames.com/favicon.ico` returns garbage that
        // can't be decoded, while `epicgames.com/favicon.ico` returns a real image. Brand-key + canonical URL
        // ensures every Epic chip (regardless of which subdomain IGDB has) renders the same icon from one cache
        // entry. Same logic for Steam (store.steampowered.com vs steamcommunity.com).
        val steamId = 1240440
        val websites = persistentListOf(
            IgdbGame.IgdbWebsite("https://store.epicgames.com/en-US/p/luna-abyss-696590", IgdbGame.IgdbWebsite.Category.EpicStore),
            IgdbGame.IgdbWebsite("https://epicgames.com/p/lego-batman-c82b6b", IgdbGame.IgdbWebsite.Category.EpicStore),
            IgdbGame.IgdbWebsite("https://www.epicgames.com/store/en-US/p/halo-infinite", IgdbGame.IgdbWebsite.Category.EpicStore),
            IgdbGame.IgdbWebsite("https://steamcommunity.com/app/12345", IgdbGame.IgdbWebsite.Category.Steam),
        )
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdbDetails(websites = websites)

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        // All three Epic URLs collapse to the same canonical favicon + cache key.
        repeat(3) { i ->
            assertEquals("https://epicgames.com/favicon.ico", data.websites[i].faviconUrl, "Epic[$i]")
            assertEquals("brand:epicgames", data.websites[i].faviconCacheKey, "Epic[$i]")
        }
        // steamcommunity.com is normalised to the same Steam brand-key as the store subdomain.
        assertEquals("https://store.steampowered.com/favicon.ico", data.websites[3].faviconUrl)
        assertEquals("brand:steam", data.websites[3].faviconCacheKey)
    }

    @Test
    fun igdbGameId_in_savedState_calls_fetchGameDetailsByIgdbId_and_emits_Data() = runTest {
        val igdbId = 3151L
        val igdb = igdbDetails(id = igdbId, name = "Hollow Knight")
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(igdbId) } returns igdb

        val viewModel = createViewModelByIgdbId(igdbId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(igdb), emissions.last())
        verifySuspend(exactly(1)) { igdbRepository.fetchGameDetailsByIgdbId(igdbId) }
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsBySteamId(any()) }
    }

    @Test
    fun igdbGameId_returning_null_emits_Error() = runTest {
        val igdbId = 3151L
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(igdbId) } returns null

        val viewModel = createViewModelByIgdbId(igdbId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun igdbGameId_exception_during_fetch_emits_Error() = runTest {
        val igdbId = 3151L
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(igdbId) } calls { throw Exception("IGDB down") }

        val viewModel = createViewModelByIgdbId(igdbId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun title_in_savedState_calls_fetchGameDetailsByTitle_and_emits_Data_with_resolvedByTitle_true() = runTest {
        val title = "Genshin Impact"
        val igdb = igdbDetails(id = 9999L, name = title)
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns igdb

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(igdb, data.game)
        assertEquals(true, data.resolvedByTitle, "Title path must surface the fuzzy-match flag")
        assertEquals(GameDetailsViewModel.CandidatesState.Idle, data.candidatesState)
        assertEquals(false, data.showPicker)
        verifySuspend(exactly(1)) { igdbRepository.fetchGameDetailsByTitle(title) }
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsBySteamId(any()) }
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsByIgdbId(any()) }
    }

    @Test
    fun steamAppId_path_sets_resolvedByTitle_false() = runTest {
        val steamId = 1240440
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdbDetails()

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(false, data.resolvedByTitle)
    }

    @Test
    fun igdbGameId_path_sets_resolvedByTitle_false() = runTest {
        val igdbId = 3151L
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(igdbId) } returns igdbDetails(id = igdbId)

        val viewModel = createViewModelByIgdbId(igdbId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(false, data.resolvedByTitle)
    }

    @Test
    fun onWarningTap_fires_candidate_query_when_state_is_Idle() = runTest {
        val title = "Tomb Raider"
        val igdb = igdbDetails(id = 100L, name = title)
        val candidates = persistentListOf(
            IgdbGame.IgdbSimilarGame(id = 11L, name = "Tomb Raider", coverImageId = "c1"),
            IgdbGame.IgdbSimilarGame(id = 22L, name = "Tomb Raider II", coverImageId = null),
        )
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns igdb
        everySuspend { igdbRepository.fetchSearchCandidatesByTitle(title) } returns candidates

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        viewModel.onWarningTap()
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(true, data.showPicker)
        assertEquals(GameDetailsViewModel.CandidatesState.Loaded(candidates), data.candidatesState)
        verifySuspend(exactly(1)) { igdbRepository.fetchSearchCandidatesByTitle(title) }
    }

    @Test
    fun onWarningTap_does_not_re_fire_query_when_already_Loaded() = runTest {
        val title = "Tomb Raider"
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns igdbDetails(id = 100L, name = title)
        everySuspend { igdbRepository.fetchSearchCandidatesByTitle(title) } returns persistentListOf()

        val viewModel = createViewModelByTitle(title)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        viewModel.onWarningTap()
        runCurrent()
        viewModel.onPickerDismiss()
        runCurrent()
        viewModel.onWarningTap()
        runCurrent()

        verifySuspend(exactly(1)) { igdbRepository.fetchSearchCandidatesByTitle(title) }
    }

    @Test
    fun onWarningTap_with_query_failure_emits_CandidatesState_Error() = runTest {
        val title = "Tomb Raider"
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns igdbDetails(id = 100L, name = title)
        everySuspend { igdbRepository.fetchSearchCandidatesByTitle(title) } calls { throw Exception("IGDB down") }

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        viewModel.onWarningTap()
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(GameDetailsViewModel.CandidatesState.Error, data.candidatesState)
        assertEquals(true, data.showPicker, "Sheet stays open so the user can retry")
    }

    @Test
    fun onCandidatePicked_swaps_game_via_fetchGameDetailsByIgdbId_and_preserves_resolvedByTitle() = runTest {
        val title = "Tomb Raider"
        val original = igdbDetails(id = 100L, name = "Tomb Raider")
        val picked = igdbDetails(id = 22L, name = "Tomb Raider II")
        val candidates = persistentListOf(
            IgdbGame.IgdbSimilarGame(id = 100L, name = "Tomb Raider", coverImageId = null),
            IgdbGame.IgdbSimilarGame(id = 22L, name = "Tomb Raider II", coverImageId = null),
        )
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns original
        everySuspend { igdbRepository.fetchSearchCandidatesByTitle(title) } returns candidates
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(22L) } returns picked

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        viewModel.onWarningTap()
        runCurrent()

        viewModel.onCandidatePicked(22L)
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(picked, data.game)
        assertEquals(true, data.resolvedByTitle, "Warning + picker must remain available after swap")
        assertEquals(GameDetailsViewModel.CandidatesState.Loaded(candidates), data.candidatesState)
        assertEquals(false, data.showPicker, "Picker closes on successful swap")
        verifySuspend(exactly(1)) { igdbRepository.fetchGameDetailsByIgdbId(22L) }
    }

    @Test
    fun onCandidatePicked_with_same_id_just_dismisses_picker_without_refetch() = runTest {
        val title = "Tomb Raider"
        val original = igdbDetails(id = 100L, name = title)
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns original
        everySuspend { igdbRepository.fetchSearchCandidatesByTitle(title) } returns persistentListOf(
            IgdbGame.IgdbSimilarGame(id = 100L, name = title, coverImageId = null),
        )

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        viewModel.onWarningTap()
        runCurrent()

        viewModel.onCandidatePicked(100L)
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(original, data.game)
        assertEquals(false, data.showPicker)
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsByIgdbId(any()) }
    }

    @Test
    fun onPickerDismiss_flips_showPicker_false_but_preserves_loaded_candidates() = runTest {
        val title = "Tomb Raider"
        val candidates = persistentListOf(
            IgdbGame.IgdbSimilarGame(id = 11L, name = "Tomb Raider", coverImageId = null),
        )
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns igdbDetails(id = 100L, name = title)
        everySuspend { igdbRepository.fetchSearchCandidatesByTitle(title) } returns candidates

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        viewModel.onWarningTap()
        runCurrent()

        viewModel.onPickerDismiss()
        runCurrent()

        val data = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(false, data.showPicker)
        assertEquals(GameDetailsViewModel.CandidatesState.Loaded(candidates), data.candidatesState, "Loaded list survives dismissal so re-opening is instant")
    }

    @Test
    fun steamAppId_miss_with_title_falls_back_to_title_lookup_and_marks_resolvedByTitle_true() = runTest {
        // CheapShark's "steamAppID" can actually be a Steam sub/bundle id IGDB doesn't track
        // (e.g. "Middle-earth: The Shadow Bundle" with steamAppID=648168, which is a /subs/ id).
        // When the Steam-id query returns null and a title was passed alongside, fall back to
        // title lookup so the user reaches the details screen instead of a dead-end Error.
        val steamId = 648168
        val title = "Middle-earth: The Shadow Bundle"
        val igdb = igdbDetails(name = "Middle-earth: Shadow of Mordor")
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns null
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns igdb

        val viewModel = GameDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("steamAppId" to steamId, "title" to title)),
            logger = TestingLoggingListener(),
            igdbRepository = igdbRepository,
            gamesRepository = gamesRepository,
            faviconResolver = FaviconResolverImpl(),
        )
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val last = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(igdb, last.game)
        assertEquals(true, last.resolvedByTitle, "Game came via title fallback — warn the user with the fuzzy-match indicator")
    }

    @Test
    fun steamAppId_miss_without_title_emits_Error_no_title_fallback_attempted() = runTest {
        val steamId = 648168
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns null

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsByTitle(any()) }
    }

    @Test
    fun steamAppId_miss_with_title_that_also_misses_emits_NoMatch_with_title() = runTest {
        val steamId = 648168
        val title = "Middle-earth: The Shadow Bundle"
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns null
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns null

        val viewModel = GameDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("steamAppId" to steamId, "title" to title)),
            logger = TestingLoggingListener(),
            igdbRepository = igdbRepository,
            gamesRepository = gamesRepository,
            faviconResolver = FaviconResolverImpl(),
        )
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.NoMatch(title), emissions.last())
    }

    @Test
    fun blank_title_in_savedState_is_treated_as_no_title_and_emits_Error() = runTest {
        // Defensive: if a destination ever carries an empty/whitespace title, don't fire a
        // wasted IGDB call or render a NoMatch card with an empty title fragment.
        val viewModel = GameDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("title" to "   ")),
            logger = TestingLoggingListener(),
            igdbRepository = igdbRepository,
            gamesRepository = gamesRepository,
            faviconResolver = FaviconResolverImpl(),
        )
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsByTitle(any()) }
    }

    @Test
    fun reload_from_NoMatch_state_re_attempts_the_cascade_and_recovers_on_subsequent_success() = runTest {
        val title = "Mystery Game"
        val igdb = igdbDetails(name = title)
        // First fetch returns null → NoMatch. Reload returns a game → Data.
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } sequentially {
            returns(null)
            returns(igdb)
        }

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.NoMatch(title), emissions.last())

        viewModel.reload()
        runCurrent()

        val last = emissions.last() as GameDetailsViewModel.GameDetailsScreenData.Data
        assertEquals(igdb, last.game)
    }

    @Test
    fun resolveDealsAction_returns_null_when_state_is_NoMatch() = runTest {
        val title = "Mystery Game"
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns null

        val viewModel = createViewModelByTitle(title)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        // Sanity — state should be NoMatch so the CTA-absence + null-action guards behave.
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.NoMatch(title), viewModel.uiState.value)
        assertNull(viewModel.resolveDealsAction())
    }

    @Test
    fun steamAppId_miss_then_title_fallback_throws_emits_Error() = runTest {
        val steamId = 648168
        val title = "Middle-earth: The Shadow Bundle"
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns null
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } calls { throw Exception("IGDB down") }

        val viewModel = GameDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("steamAppId" to steamId, "title" to title)),
            logger = TestingLoggingListener(),
            igdbRepository = igdbRepository,
            gamesRepository = gamesRepository,
            faviconResolver = FaviconResolverImpl(),
        )
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        // Exception from the title fallback bubbles into loadFlow.catch{} and surfaces as Error,
        // not as NoMatch. NoMatch is reserved for the deterministic empty-result case.
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun title_returning_null_emits_NoMatch_with_the_title_for_the_explainer_card() = runTest {
        val title = "Mystery Game - Definitive Edition"
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } returns null

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.NoMatch(title), emissions.last())
    }

    @Test
    fun title_exception_during_fetch_emits_Error() = runTest {
        val title = "Mystery Game"
        everySuspend { igdbRepository.fetchGameDetailsByTitle(title) } calls { throw Exception("IGDB down") }

        val viewModel = createViewModelByTitle(title)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun steamAppId_takes_precedence_over_title_when_both_are_present() = runTest {
        val steamId = 1240440
        val igdb = igdbDetails()
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdb

        val viewModel = GameDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("steamAppId" to steamId, "title" to "Halo Infinite")),
            logger = TestingLoggingListener(),
            igdbRepository = igdbRepository,
            gamesRepository = gamesRepository,
            faviconResolver = FaviconResolverImpl(),
        )
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(igdb), emissions.last())
        verifySuspend(exactly(1)) { igdbRepository.fetchGameDetailsBySteamId(steamId) }
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsByTitle(any()) }
    }

    @Test
    fun reload_after_initial_failure_flips_Error_back_to_Data() = runTest {
        val steamId = 1240440
        val igdb = igdbDetails()

        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } sequentially {
            calls { throw Exception("IGDB down") }
            returns(igdb)
        }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())

        viewModel.reload()
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(igdb), emissions.last())
    }

    private fun igdbDetails(
        id: Long = 1L,
        name: String = "Halo Infinite",
        summary: String? = "Master Chief stuff",
        websites: ImmutableList<IgdbGame.IgdbWebsite> = persistentListOf(),
        steamAppId: Int? = null,
    ): IgdbGame = IgdbGame(id = id, name = name, summary = summary, websites = websites, steamAppId = steamAppId)

    @Test
    fun resolveDealsAction_returns_OpenGame_when_steamAppId_present_and_cheapshark_match() = runTest {
        val steamId = 1240440
        val igdb = igdbDetails(steamAppId = steamId)
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdb
        everySuspend { gamesRepository.findGameIdBySteamAppId(steamId, "Halo Infinite") } returns "12345"

        val viewModel = createViewModel(steamId)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val action = viewModel.resolveDealsAction()
        assertEquals(GameDetailsViewModel.DealsAction.OpenGame("12345"), action)
    }

    @Test
    fun resolveDealsAction_returns_SearchByTitle_when_steamAppId_present_but_cheapshark_empty() = runTest {
        val steamId = 1240440
        val igdb = igdbDetails(steamAppId = steamId)
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdb
        everySuspend { gamesRepository.findGameIdBySteamAppId(steamId, "Halo Infinite") } returns null

        val viewModel = createViewModel(steamId)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val action = viewModel.resolveDealsAction()
        assertEquals(GameDetailsViewModel.DealsAction.SearchByTitle("Halo Infinite"), action)
    }

    @Test
    fun resolveDealsAction_returns_SearchByTitle_when_steamAppId_absent() = runTest {
        val igdbId = 3151L
        val igdb = igdbDetails(id = igdbId, name = "Hollow Knight", steamAppId = null)
        everySuspend { igdbRepository.fetchGameDetailsByIgdbId(igdbId) } returns igdb

        val viewModel = createViewModelByIgdbId(igdbId)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val action = viewModel.resolveDealsAction()
        assertEquals(GameDetailsViewModel.DealsAction.SearchByTitle("Hollow Knight"), action)
        verifySuspend(exactly(0)) { gamesRepository.findGameIdBySteamAppId(any(), any()) }
    }

    @Test
    fun resolveDealsAction_returns_null_when_state_is_not_Data() = runTest {
        val steamId = 1240440
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns null

        val viewModel = createViewModel(steamId)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        val action = viewModel.resolveDealsAction()
        assertNull(action)
    }
}
