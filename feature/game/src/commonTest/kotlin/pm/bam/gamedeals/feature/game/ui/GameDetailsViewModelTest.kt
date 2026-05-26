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

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(steamAppId: Int?): GameDetailsViewModel = GameDetailsViewModel(
        savedStateHandle = if (steamAppId == null) SavedStateHandle() else SavedStateHandle(mapOf("steamAppId" to steamAppId)),
        logger = TestingLoggingListener(),
        igdbRepository = igdbRepository,
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
    ): IgdbGame = IgdbGame(id = id, name = name, summary = summary, websites = websites)
}
