@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.deals.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSort
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistToggleResult
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.deal
import pm.bam.gamedeals.testing.fixtures.gameInfo
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DealsViewModelTest : MainDispatcherTest() {

    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit) {
        every { observeStores() } returns flowOf(emptyList())
    }
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf())
    }
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        every { observeSelectedCountry() } returns flowOf(DEFAULT_COUNTRY)
    }

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel() = DealsViewModel(
        logger = TestingLoggingListener(),
        dealsRepository = dealsRepository,
        storesRepository = storesRepository,
        dealShareTextBuilder = dealShareTextBuilder,
        waitlistRepository = waitlistRepository,
        regionRepository = regionRepository,
    )

    @Test
    fun loads_first_page_into_data_state() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"), deal("d2"))

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(DealsViewModel.DealsScreenData.Status.DATA, state.status)
        assertEquals(2, state.deals.size)
        assertTrue(state.endReached) // short page (< DEALS_PAGE_SIZE) => no more pages
    }

    @Test
    fun error_state_on_first_page_failure() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } throws RuntimeException("boom")

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(DealsViewModel.DealsScreenData.Status.ERROR, vm.uiState.value.status)
    }

    @Test
    fun load_next_page_appends_until_short_page() = runTest {
        val fullPage = List(DealsQuery.DEALS_PAGE_SIZE) { deal("a$it", gameID = "a$it") }
        val secondPage = listOf(deal("b1", gameID = "b1"))
        everySuspend { dealsRepository.getDeals(DealsQuery(sort = DealsSort.TopDiscount, offset = 0)) } returns fullPage
        everySuspend { dealsRepository.getDeals(DealsQuery(sort = DealsSort.TopDiscount, offset = DealsQuery.DEALS_PAGE_SIZE)) } returns secondPage

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(DealsQuery.DEALS_PAGE_SIZE, vm.uiState.value.deals.size)
        assertFalse(vm.uiState.value.endReached) // full first page => more available

        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(DealsQuery.DEALS_PAGE_SIZE + 1, vm.uiState.value.deals.size)
        assertTrue(vm.uiState.value.endReached)
    }

    @Test
    fun setSort_reloads_from_offset_zero_with_new_sort() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setSort(DealsSort.PriceLowToHigh)
        advanceUntilIdle()

        assertEquals(DealsSort.PriceLowToHigh, vm.uiState.value.sort)
        verifySuspend(exactly(1)) { dealsRepository.getDeals(DealsQuery(sort = DealsSort.PriceLowToHigh, offset = 0)) }
    }

    @Test
    fun toggleShop_reloads_page_zero_with_selected_shop_ids() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleShop(61)
        advanceUntilIdle()

        assertEquals(setOf(61), vm.uiState.value.shopIds)
        verifySuspend(exactly(1)) { dealsRepository.getDeals(DealsQuery(sort = DealsSort.TopDiscount, shopIds = listOf(61), offset = 0)) }
    }

    @Test
    fun toggle_waitlist_when_logged_out_emits_SignInRequired() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))
        everySuspend { waitlistRepository.toggleWaitlist("42") } returns WaitlistToggleResult.NOT_LOGGED_IN

        val vm = createViewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.toggleWaitlistFromDeal(
            DealBottomSheetData.DealDetailsData(
                store = store(),
                gameId = "42",
                gameName = "Halo",
                dealId = "deal-1",
                gameSalesPriceDenominated = "$9.99",
                gameInfo = gameInfo(gameID = "42", thumb = "thumb-42"),
                cheaperStores = persistentListOf(),
                cheapestPrice = null,
            )
        )
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(DealsViewModel.DealsUiEvent.SignInRequired, events.first())
    }
}
