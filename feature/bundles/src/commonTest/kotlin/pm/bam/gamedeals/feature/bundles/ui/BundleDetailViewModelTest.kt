@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.bundles.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
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

class BundleDetailViewModelTest : MainDispatcherTest() {

    private val bundlesRepository: BundlesRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun loads_bundle_by_id() = runTest {
        everySuspend { bundlesRepository.getBundle(7) } returns bundle(7)

        val viewModel = BundleDetailViewModel(SavedStateHandle(mapOf("bundleId" to 7)), TestingLoggingListener(), bundlesRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<BundleDetailViewModel.BundleDetailScreenData.Data>(state)
        assertEquals(7, state.bundle.id)
    }

    @Test
    fun error_when_bundle_missing() = runTest {
        everySuspend { bundlesRepository.getBundle(7) } returns null

        val viewModel = BundleDetailViewModel(SavedStateHandle(mapOf("bundleId" to 7)), TestingLoggingListener(), bundlesRepository)
        advanceUntilIdle()

        assertIs<BundleDetailViewModel.BundleDetailScreenData.Error>(viewModel.uiState.value)
    }

    @Test
    fun error_when_no_id_argument() = runTest {
        val viewModel = BundleDetailViewModel(SavedStateHandle(), TestingLoggingListener(), bundlesRepository)
        advanceUntilIdle()

        assertIs<BundleDetailViewModel.BundleDetailScreenData.Error>(viewModel.uiState.value)
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
