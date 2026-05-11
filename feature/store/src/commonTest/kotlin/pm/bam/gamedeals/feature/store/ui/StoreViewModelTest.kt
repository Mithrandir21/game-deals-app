@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.store.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreViewModelTest : MainDispatcherTest() {

    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit)
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(storeId: Int?): StoreViewModel = StoreViewModel(
        savedStateHandle = if (storeId == null) SavedStateHandle() else SavedStateHandle(mapOf("storeId" to storeId)),
        logger = TestingLoggingListener(),
        dealsRepository = dealsRepository,
        storesRepository = storesRepository,
        dealShareTextBuilder = dealShareTextBuilder,
    )

    @Test
    fun initially_store_details_is_loading_then_error_on_failure() = runTest {
        val storeId = 1
        everySuspend { storesRepository.getStore(storeId) } throws Exception()

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(2, emissions.size, "Expected Loading and Error")
        assertEquals(StoreViewModel.StoreScreenData.Loading, emissions.first())
        assertEquals(StoreViewModel.StoreScreenData.Error, emissions.last())
    }

    @Test
    fun seeded_storeId_loads_StoreDetails_Data_state() = runTest {
        val storeId = 1
        val store = store(storeID = storeId)

        everySuspend { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(2, emissions.size, "Expected Loading and Data")
        assertEquals(StoreViewModel.StoreScreenData.Loading, emissions.first())
        assertEquals(StoreViewModel.StoreScreenData.Data(store), emissions.last())
    }

    @Test
    fun missing_storeId_emits_Error_state() = runTest {
        val viewModel = createViewModel(storeId = null)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(2, emissions.size, "Expected Loading and Error")
        assertEquals(StoreViewModel.StoreScreenData.Loading, emissions.first())
        assertEquals(StoreViewModel.StoreScreenData.Error, emissions.last())
    }

    @Test
    fun deals_StateFlow_does_not_tombstone_when_refresh_fails() = runTest {
        val storeId = 1
        val store = store(storeID = storeId)

        everySuspend { storesRepository.getStore(storeId) } returns store
        every { dealsRepository.observeStoreDeals(storeId) } returns flow { throw RuntimeException("boom") }

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.deals.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(persistentListOf(), viewModel.deals.value)
        assertEquals(persistentListOf(), emissions.last())

        val uiEmissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        assertEquals(StoreViewModel.StoreScreenData.Data(store), uiEmissions.last())
    }
}
