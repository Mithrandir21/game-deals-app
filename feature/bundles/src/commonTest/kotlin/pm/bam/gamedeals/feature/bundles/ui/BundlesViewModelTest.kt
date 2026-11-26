@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.bundles.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BundlesViewModelTest : MainDispatcherTest() {

    private val bundlesRepository: BundlesRepository = mock(MockMode.autoUnit)
    private val settingsRepository: SettingsRepository = mock(MockMode.autoUnit) {
        every { observeMatureOptIn() } returns flowOf(false)
    }

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel() = BundlesViewModel(TestingLoggingListener(), bundlesRepository, settingsRepository)

    @Test
    fun loads_bundles_into_data_state() = runTest {
        everySuspend { bundlesRepository.getBundles() } returns listOf(bundle(1), bundle(2))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundlesViewModel.BundlesScreenData.Data>(state)
        assertEquals(2, state.bundles.size)
    }

    @Test
    fun error_state_on_failure() = runTest {
        everySuspend { bundlesRepository.getBundles() } throws RuntimeException("boom")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertIs<BundlesViewModel.BundlesScreenData.Error>(viewModel.uiState.value)
    }

    @Test
    fun mature_bundles_hidden_unless_opted_in() = runTest {
        everySuspend { bundlesRepository.getBundles() } returns listOf(bundle(1), bundle(2, isMature = true))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundlesViewModel.BundlesScreenData.Data>(state)
        assertEquals(listOf(1), state.bundles.map { it.id }) // mature bundle filtered out (opt-in off)
    }

    @Test
    fun mature_bundles_shown_when_opted_in() = runTest {
        every { settingsRepository.observeMatureOptIn() } returns flowOf(true)
        everySuspend { bundlesRepository.getBundles() } returns listOf(bundle(1), bundle(2, isMature = true))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundlesViewModel.BundlesScreenData.Data>(state)
        assertEquals(setOf(1, 2), state.bundles.map { it.id }.toSet())
    }

    @Test
    fun default_sort_is_newest_by_publish_date_descending() = runTest {
        everySuspend { bundlesRepository.getBundles() } returns listOf(
            bundle(1, publishEpochMs = 100),
            bundle(2, publishEpochMs = 300),
            bundle(3, publishEpochMs = 200),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundlesViewModel.BundlesScreenData.Data>(state)
        assertEquals(listOf(2, 3, 1), state.bundles.map { it.id })
    }

    @Test
    fun expiring_soon_sort_orders_by_expiry_ascending_nulls_last() = runTest {
        everySuspend { bundlesRepository.getBundles() } returns listOf(
            bundle(1, expiryEpochMs = 300),
            bundle(2, expiryEpochMs = null),
            bundle(3, expiryEpochMs = 100),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setSort(BundleSort.ExpiringSoon)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundlesViewModel.BundlesScreenData.Data>(state)
        assertEquals(listOf(3, 1, 2), state.bundles.map { it.id })
        assertEquals(BundleSort.ExpiringSoon, state.sort)
    }

    @Test
    fun price_sort_orders_by_price_ascending_nulls_last() = runTest {
        everySuspend { bundlesRepository.getBundles() } returns listOf(
            bundle(1, priceValue = 9.0),
            bundle(2, priceValue = null),
            bundle(3, priceValue = 4.0),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setSort(BundleSort.Price)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundlesViewModel.BundlesScreenData.Data>(state)
        assertEquals(listOf(3, 1, 2), state.bundles.map { it.id })
    }

    private fun bundle(
        id: Int,
        isMature: Boolean = false,
        publishEpochMs: Long? = null,
        expiryEpochMs: Long? = null,
        priceValue: Double? = null,
    ) = Bundle(
        id = id,
        title = "Bundle $id",
        storeName = "Store",
        url = "https://example.com/$id",
        expiryEpochMs = expiryEpochMs,
        gameCount = 0,
        priceDenominated = null,
        games = persistentListOf(),
        publishEpochMs = publishEpochMs,
        isMature = isMature,
        priceValue = priceValue,
    )
}
