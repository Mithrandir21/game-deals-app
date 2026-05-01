package pm.bam.gamedeals.feature.store.ui

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.testing.MainCoroutineRule
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val storesRepository: StoresRepository = mockk()

    private val dealsRepository: DealsRepository = mockk()

    private fun createViewModel(storeId: Int): StoreViewModel = StoreViewModel(
        // The typed route stores its args under their property names in the SavedStateHandle,
        // matching what the Compose Navigation runtime hands to a destination's ViewModel.
        savedStateHandle = SavedStateHandle(mapOf("storeId" to storeId)),
        logger = TestingLoggingListener(),
        dealsRepository = dealsRepository,
        storesRepository = storesRepository,
    )

    @Test
    fun `initially store details is null when no store data is loaded`() = runTest {
        val storeId = 1
        val exception: Exception = mockk { every { printStackTrace() } just runs }
        coEvery { storesRepository.getStore(storeId) } throws exception

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.storeDetails.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertNull(emissions.first())
    }

    @Test
    fun `seeded storeId loads StoreDetails`() = runTest {
        val storeId = 1
        val store: Store = mockk()

        coEvery { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.storeDetails.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

        // The ID is now seeded from SavedStateHandle at construction, so the load
        // happens immediately after the flow becomes active rather than via setStoreId.
        assertEquals(2, emissions.size)
        assertNull(emissions.first())
        assertEquals(store, emissions.second())
    }

    @Test
    fun `seeded storeId failure - throws caught error`() = runTest {
        val storeId = 1
        val exception: Exception = mockk {
            every { printStackTrace() } just runs
        }

        coEvery { storesRepository.getStore(storeId) } throws exception

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.storeDetails.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertNull(emissions.first())
    }
}
