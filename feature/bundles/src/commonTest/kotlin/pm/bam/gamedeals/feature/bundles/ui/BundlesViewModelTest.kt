@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.bundles.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BundlesViewModelTest : MainDispatcherTest() {

    private val bundlesRepository: BundlesRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun loads_bundles_into_data_state() = runTest {
        everySuspend { bundlesRepository.getBundles() } returns listOf(bundle(1), bundle(2))

        val viewModel = BundlesViewModel(TestingLoggingListener(), bundlesRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundlesViewModel.BundlesScreenData.Data>(state)
        assertEquals(2, state.bundles.size)
    }

    @Test
    fun error_state_on_failure() = runTest {
        everySuspend { bundlesRepository.getBundles() } throws RuntimeException("boom")

        val viewModel = BundlesViewModel(TestingLoggingListener(), bundlesRepository)
        advanceUntilIdle()

        assertIs<BundlesViewModel.BundlesScreenData.Error>(viewModel.uiState.value)
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
}
