@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.bundles.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundleDetailViewModelTest : MainDispatcherTest() {

    private val bundlesRepository: BundlesRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf())
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIgnoredIds() } returns flowOf(persistentSetOf())
    }
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun loads_bundle_by_id() = runTest {
        everySuspend { bundlesRepository.getBundle(7) } returns bundle(7)

        val viewModel = BundleDetailViewModel(SavedStateHandle(mapOf("bundleId" to 7)), TestingLoggingListener(), bundlesRepository, gamesRepository, storesRepository, waitlistRepository, ignoredRepository, dealShareTextBuilder)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundleDetailViewModel.BundleDetailScreenData.Data>(state)
        assertEquals(7, state.bundle.id)
    }

    @Test
    fun error_when_bundle_missing() = runTest {
        everySuspend { bundlesRepository.getBundle(7) } returns null

        val viewModel = BundleDetailViewModel(SavedStateHandle(mapOf("bundleId" to 7)), TestingLoggingListener(), bundlesRepository, gamesRepository, storesRepository, waitlistRepository, ignoredRepository, dealShareTextBuilder)
        advanceUntilIdle()

        assertIs<BundleDetailViewModel.BundleDetailScreenData.Error>(viewModel.uiState.value)
    }

    @Test
    fun error_when_no_id_argument() = runTest {
        val viewModel = BundleDetailViewModel(SavedStateHandle(), TestingLoggingListener(), bundlesRepository, gamesRepository, storesRepository, waitlistRepository, ignoredRepository, dealShareTextBuilder)
        advanceUntilIdle()

        assertIs<BundleDetailViewModel.BundleDetailScreenData.Error>(viewModel.uiState.value)
    }

    @Test
    fun enriches_games_with_prices_and_value_summary() = runTest {
        everySuspend { bundlesRepository.getBundle(7) } returns bundleWithGames(7)
        everySuspend { bundlesRepository.getBundleGamePrices(listOf("g1", "g2")) } returns listOf(
            price("g1", best = 4.0, bestDenominated = "$4.00", cut = 80, low = 3.0, lowDenominated = "$3.00"),
            price("g2", best = 6.0, bestDenominated = "$6.00", cut = 50, low = 5.0, lowDenominated = "$5.00"),
        )

        val viewModel = BundleDetailViewModel(SavedStateHandle(mapOf("bundleId" to 7)), TestingLoggingListener(), bundlesRepository, gamesRepository, storesRepository, waitlistRepository, ignoredRepository, dealShareTextBuilder)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundleDetailViewModel.BundleDetailScreenData.Data>(state)
        assertEquals(2, state.prices.size)
        assertEquals("GOG", state.prices["g1"]?.bestShopName)
        val summary = state.valueSummary
        assertEquals("$10.00", summary?.currentValueDenominated) // 4 + 6
        assertEquals("$8.00", summary?.historicalLowDenominated) // 3 + 5
        assertEquals("$5.00", summary?.bundlePriceDenominated)
        assertEquals(50, summary?.savingsPercent) // (10 - 5) / 10
        assertEquals(2, summary?.pricedGames)
        assertEquals(2, summary?.totalGames)
    }

    @Test
    fun price_fetch_failure_still_shows_bundle_with_empty_prices() = runTest {
        everySuspend { bundlesRepository.getBundle(7) } returns bundleWithGames(7)
        everySuspend { bundlesRepository.getBundleGamePrices(listOf("g1", "g2")) } throws RuntimeException("boom")

        val viewModel = BundleDetailViewModel(SavedStateHandle(mapOf("bundleId" to 7)), TestingLoggingListener(), bundlesRepository, gamesRepository, storesRepository, waitlistRepository, ignoredRepository, dealShareTextBuilder)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundleDetailViewModel.BundleDetailScreenData.Data>(state)
        assertTrue(state.prices.isEmpty())
        assertNull(state.valueSummary)
    }

    private fun bundle(id: Int) = Bundle(
        id = id,
        title = "Bundle $id",
        storeName = "Store",
        url = "https://example.com/$id",
        expiryEpochMs = null,
        gameCount = 0,
        priceDenominated = null,
        games = persistentListOf(),
    )

    private fun bundleWithGames(id: Int): Bundle {
        val games = persistentListOf(Bundle.BundleGame("g1", "Game 1", ""), Bundle.BundleGame("g2", "Game 2", ""))
        return Bundle(
            id = id,
            title = "Bundle $id",
            storeName = "Store",
            url = "https://example.com/$id",
            expiryEpochMs = null,
            gameCount = 2,
            priceDenominated = "$5.00",
            games = games,
            priceValue = 5.0,
            tiers = persistentListOf(Bundle.Tier(priceDenominated = "$5.00", priceValue = 5.0, games = games)),
        )
    }

    private fun price(gameId: String, best: Double, bestDenominated: String, cut: Int, low: Double, lowDenominated: String) =
        BundleGamePrice(
            gameId = gameId,
            bestShopName = "GOG",
            bestPriceValue = best,
            bestPriceDenominated = bestDenominated,
            bestCutPercent = cut,
            historicalLowValue = low,
            historicalLowDenominated = lowDenominated,
            currency = "USD",
        )
}
