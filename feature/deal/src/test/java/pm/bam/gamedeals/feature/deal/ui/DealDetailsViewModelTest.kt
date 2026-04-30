package pm.bam.gamedeals.feature.deal.ui

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.testing.MainCoroutineRule
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import pm.bam.gamedeals.testing.utils.third

@OptIn(ExperimentalCoroutinesApi::class)
class DealDetailsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val storesRepository: StoresRepository = mockk()

    private val dealsRepository: DealsRepository = mockk()

    private lateinit var viewModel: DealDetailsViewModel

    @Before
    fun setup() {
        viewModel = DealDetailsViewModel(TestingLoggingListener(), dealsRepository, storesRepository)
    }

    @Test
    fun `initially deal details is null`() = runTest {
        val emissions = viewModel.dealDealDetails.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)
        assertEquals(1, emissions.size)
        assertNull(emissions.first())
    }


    @Test
    fun `loading Deal results in DealDetails`() = runTest {
        val dealId = "Deal ID"
        val storeId = 2
        val dealTitle = "Deal Title"
        val dealSalePriceDenominated = "$12"
        val deal: Deal = mockk {
            every { dealID } returns dealId
            every { storeID } returns storeId
            every { title } returns dealTitle
            every { salePriceDenominated } returns dealSalePriceDenominated
        }

        val mockGameInfo: DealDetails.GameInfo = mockk()
        val mockCheapestPrice: DealDetails.CheapestPrice = mockk()
        val mockCheaperStore: DealDetails.CheaperStore = mockk {
            every { storeID } returns storeId
        }
        val dealDetails: DealDetails = mockk {
            every { gameInfo } returns mockGameInfo
            every { cheapestPrice } returns mockCheapestPrice
            every { cheaperStores } returns listOf(mockCheaperStore)
        }

        val store: Store = mockk()

        coEvery { dealsRepository.getDeal(dealId) } returns dealDetails
        coEvery { storesRepository.getStore(storeId) } returns store

        val emissions = viewModel.dealDealDetails.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertNull(emissions.first())

        viewModel.loadDealDetails(
            dealId = dealId,
            dealStoreId = storeId,
            dealTitle = dealTitle,
            dealPriceDenominated = dealSalePriceDenominated
        )


        assertEquals(2, emissions.size)
        assertNull(emissions.first())
        assertEquals(
            DealBottomSheetData.DealDetailsLoading(
                store = store,
                gameName = deal.title,
                dealId = dealId,
                gameSalesPriceDenominated = dealSalePriceDenominated
            ), emissions.second()
        )

        delay(1000) // Delay because Flow 'mapDelayAtLeast'


        assertEquals(3, emissions.size)
        assertEquals(
            DealBottomSheetData.DealDetailsData(
                store = store,
                gameName = deal.title,
                dealId = dealId,
                gameSalesPriceDenominated = deal.salePriceDenominated,
                gameInfo = dealDetails.gameInfo,
                cheapestPrice = dealDetails.cheapestPrice,
                cheaperStores = dealDetails.cheaperStores.map { store to it }
            ), emissions.third()
        )
    }

    @Test
    fun `dismissing deal details`() = runTest {
        val emissions = viewModel.dealDealDetails.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertNull(emissions.first())

        viewModel.dismissDealDetails()

        assertEquals(1, emissions.size)
        assertNull(emissions.first())
    }
}