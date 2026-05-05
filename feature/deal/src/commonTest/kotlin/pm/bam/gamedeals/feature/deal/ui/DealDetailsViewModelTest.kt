@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.deal.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import pm.bam.gamedeals.testing.utils.third
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DealDetailsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit)

    private lateinit var viewModel: DealDetailsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DealDetailsViewModel(TestingLoggingListener(), dealsRepository, storesRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initially_deal_details_is_null() = runTest {
        val emissions = viewModel.dealDealDetails.observeEmissions(this.backgroundScope, testDispatcher)
        assertEquals(1, emissions.size)
        assertNull(emissions.first())
    }

    @Test
    fun loading_deal_results_in_DealDetails() = runTest {
        val dealId = "Deal ID"
        val storeId = 2
        val dealTitle = "Deal Title"
        val dealSalePriceDenominated = "$12"

        val gameInfo = gameInfo()
        val cheapestPrice = cheapestPrice()
        val cheaperStore = cheaperStore(storeID = storeId)
        val dealDetails = dealDetails(
            gameInfo = gameInfo,
            cheapestPrice = cheapestPrice,
            cheaperStores = listOf(cheaperStore),
        )

        val store = store(storeID = storeId)

        everySuspend { dealsRepository.getDeal(dealId) } returns dealDetails
        everySuspend { storesRepository.getStore(storeId) } returns store

        val emissions = viewModel.dealDealDetails.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(1, emissions.size)
        assertNull(emissions.first())

        viewModel.loadDealDetails(
            dealId = dealId,
            dealStoreId = storeId,
            dealTitle = dealTitle,
            dealPriceDenominated = dealSalePriceDenominated,
        )

        assertEquals(2, emissions.size)
        assertNull(emissions.first())
        assertEquals(
            DealBottomSheetData.DealDetailsLoading(
                store = store,
                gameName = dealTitle,
                dealId = dealId,
                gameSalesPriceDenominated = dealSalePriceDenominated,
            ), emissions.second()
        )

        delay(1000) // mapDelayAtLeast

        assertEquals(3, emissions.size)
        assertEquals(
            DealBottomSheetData.DealDetailsData(
                store = store,
                gameName = dealTitle,
                dealId = dealId,
                gameSalesPriceDenominated = dealSalePriceDenominated,
                gameInfo = dealDetails.gameInfo,
                cheapestPrice = dealDetails.cheapestPrice,
                cheaperStores = dealDetails.cheaperStores.map { store to it }
            ), emissions.third()
        )
    }

    @Test
    fun dismissing_deal_details() = runTest {
        val emissions = viewModel.dealDealDetails.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(1, emissions.size)
        assertNull(emissions.first())

        viewModel.dismissDealDetails()

        assertEquals(1, emissions.size)
        assertNull(emissions.first())
    }

    @Test
    fun rapid_successive_loadDealDetails_calls_only_emit_the_latest_deal_data() = runTest {
        val firstDealId = "First Deal"
        val firstStoreId = 1
        val firstDealTitle = "First Title"
        val firstSalePriceDenominated = "$5"
        val firstStore = store(storeID = firstStoreId)
        val firstGameInfo = gameInfo()
        val firstCheapestPrice = cheapestPrice()
        val firstDealDetails = dealDetails(
            gameInfo = firstGameInfo,
            cheapestPrice = firstCheapestPrice,
            cheaperStores = emptyList(),
        )

        val secondDealId = "Second Deal"
        val secondStoreId = 2
        val secondDealTitle = "Second Title"
        val secondSalePriceDenominated = "$10"
        val secondStore = store(storeID = secondStoreId)
        val secondGameInfo = gameInfo(name = "Second Info")
        val secondCheapestPrice = cheapestPrice(priceDenominated = "$5")
        val secondDealDetails = dealDetails(
            gameInfo = secondGameInfo,
            cheapestPrice = secondCheapestPrice,
            cheaperStores = emptyList(),
        )

        everySuspend { dealsRepository.getDeal(firstDealId) } returns firstDealDetails
        everySuspend { dealsRepository.getDeal(secondDealId) } returns secondDealDetails
        everySuspend { storesRepository.getStore(firstStoreId) } returns firstStore
        everySuspend { storesRepository.getStore(secondStoreId) } returns secondStore

        val emissions = viewModel.dealDealDetails.observeEmissions(this.backgroundScope, testDispatcher)

        viewModel.loadDealDetails(
            dealId = firstDealId,
            dealStoreId = firstStoreId,
            dealTitle = firstDealTitle,
            dealPriceDenominated = firstSalePriceDenominated,
        )

        viewModel.loadDealDetails(
            dealId = secondDealId,
            dealStoreId = secondStoreId,
            dealTitle = secondDealTitle,
            dealPriceDenominated = secondSalePriceDenominated,
        )

        delay(1000)

        val terminal = emissions.last()
        assertEquals(
            DealBottomSheetData.DealDetailsData(
                store = secondStore,
                gameName = secondDealTitle,
                dealId = secondDealId,
                gameSalesPriceDenominated = secondSalePriceDenominated,
                gameInfo = secondGameInfo,
                cheapestPrice = secondCheapestPrice,
                cheaperStores = emptyList(),
            ), terminal
        )

        val firstDealDataEmissions = emissions.filterIsInstance<DealBottomSheetData.DealDetailsData>()
            .filter { it.dealId == firstDealId }
        assertEquals(0, firstDealDataEmissions.size)
    }
}

private fun store(
    storeID: Int = 1,
    storeName: String = "Test Store",
    isActive: Boolean = true,
    images: Store.StoreImages = Store.StoreImages(banner = "banner", logo = "logo", icon = "icon"),
    expires: Long = 0L,
) = Store(storeID, storeName, isActive, images, expires)

private fun deal(
    dealID: String = "deal-1",
    storeID: Int = 1,
    title: String = "Test Deal",
    salePriceDenominated: String = "$9.99",
) = Deal(
    dealID = dealID,
    internalName = "TEST",
    title = title,
    storeID = storeID,
    gameID = 100,
    salePriceValue = 9.99,
    salePriceDenominated = salePriceDenominated,
    normalPriceValue = 19.99,
    normalPriceDenominated = "$19.99",
    isOnSale = true,
    savings = 50.0,
    metacriticScore = 80,
    steamRatingPercent = 90,
    steamRatingCount = "100",
    releaseDate = 0,
    lastChange = 0,
    dealRating = 9.0,
    thumb = "thumb",
)

private fun dealDetails(
    gameInfo: DealDetails.GameInfo = gameInfo(),
    cheaperStores: List<DealDetails.CheaperStore> = emptyList(),
    cheapestPrice: DealDetails.CheapestPrice? = cheapestPrice(),
) = DealDetails(gameInfo, cheaperStores, cheapestPrice)

private fun gameInfo(
    storeID: Int = 1,
    gameID: Int = 100,
    name: String = "Test Game",
    salePriceValue: Double = 9.99,
    salePriceDenominated: String = "$9.99",
    retailPriceValue: Double = 19.99,
    retailPriceDenominated: String = "$19.99",
    steamRatingCount: String = "100",
    publisher: String = "ACME",
    thumb: String = "thumb",
) = DealDetails.GameInfo(
    storeID = storeID,
    gameID = gameID,
    name = name,
    salePriceValue = salePriceValue,
    salePriceDenominated = salePriceDenominated,
    retailPriceValue = retailPriceValue,
    retailPriceDenominated = retailPriceDenominated,
    steamRatingCount = steamRatingCount,
    publisher = publisher,
    thumb = thumb,
)

private fun cheaperStore(
    dealID: String = "cheaper-deal",
    storeID: Int = 1,
    salePriceValue: Double = 4.99,
    salePriceDenominated: String = "$4.99",
    retailPriceValue: Double = 19.99,
    retailPriceDenominated: String = "$19.99",
) = DealDetails.CheaperStore(
    dealID = dealID,
    storeID = storeID,
    salePriceValue = salePriceValue,
    salePriceDenominated = salePriceDenominated,
    retailPriceValue = retailPriceValue,
    retailPriceDenominated = retailPriceDenominated,
)

private fun cheapestPrice(
    priceValue: Double = 4.99,
    priceDenominated: String = "$4.99",
    date: String = "2026-01-01",
) = DealDetails.CheapestPrice(priceValue, priceDenominated, date)
