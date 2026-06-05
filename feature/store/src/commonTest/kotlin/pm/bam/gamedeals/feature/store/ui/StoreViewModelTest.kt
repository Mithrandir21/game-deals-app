@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.store.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.dealDetails
import pm.bam.gamedeals.testing.fixtures.gameInfo
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StoreViewModelTest : MainDispatcherTest() {

    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit)
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf())
    }
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        every { observeSelectedCountry() } returns flowOf(DEFAULT_COUNTRY)
    }

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(storeId: Int?): StoreViewModel = StoreViewModel(
        savedStateHandle = if (storeId == null) SavedStateHandle() else SavedStateHandle(mapOf("storeId" to storeId)),
        logger = TestingLoggingListener(),
        dealsRepository = dealsRepository,
        storesRepository = storesRepository,
        dealShareTextBuilder = dealShareTextBuilder,
        waitlistRepository = waitlistRepository,
        regionRepository = regionRepository,
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

    @Test
    fun toggleWaitlistFromDeal_delegates_to_repository_with_game_id() = runTest {
        val viewModel = createViewModel(storeId = 1)
        val data = DealBottomSheetData.DealDetailsData(
            store = store(),
            gameId = "42",
            gameName = "Halo",
            dealId = "deal-1",
            gameSalesPriceDenominated = "$9.99",
            gameInfo = gameInfo(gameID = "42", thumb = "thumb-42"),
            cheaperStores = persistentListOf(),
            cheapestPrice = null,
        )
        viewModel.toggleWaitlistFromDeal(data)
        runCurrent()

        verifySuspend(exactly(1)) {
            waitlistRepository.toggleWaitlist("42")
        }
    }

    @Test
    fun waitlistIds_initial_value_is_empty_set() = runTest {
        val viewModel = createViewModel(storeId = 1)
        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(emptySet<String>(), ids.first())
    }

    @Test
    fun waitlistIds_emits_values_from_repository() = runTest {
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf("1", "2", "3"))

        val viewModel = createViewModel(storeId = 1)
        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(setOf("1", "2", "3"), ids.last())
    }

    @Test
    fun waitlistIds_recovers_from_repository_error_with_empty_set() = runTest {
        every { waitlistRepository.observeWaitlistIds() } returns flow { throw Exception() }

        val viewModel = createViewModel(storeId = 1)
        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(emptySet<String>(), ids.last())
    }

    @Test
    fun onShareDealClicked_emits_ShareDeal_event_with_built_text() = runTest {
        every { dealShareTextBuilder.build(any(), any(), any(), any()) } returns "Built share text"

        val viewModel = createViewModel(storeId = 1)
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        val data = DealBottomSheetData.DealDetailsLoading(
            store = store(storeName = "Steam"),
            gameId = "42",
            gameName = "Halo",
            dealId = "deal-1",
            dealUrl = "https://deal-url",
            gameSalesPriceDenominated = "$9.99",
        )
        viewModel.onShareDealClicked(data)
        runCurrent()

        assertEquals(1, events.size)
        assertEquals(StoreViewModel.StoreUiEvent.ShareDeal("Built share text"), events.first())

        verify(exactly(1)) {
            dealShareTextBuilder.build(
                gameTitle = "Halo",
                salePriceDenominated = "$9.99",
                storeName = "Steam",
                dealUrl = "https://deal-url",
            )
        }
    }

    @Test
    fun retry_after_failed_store_details_load_flips_Error_back_to_Data() = runTest {
        val storeId = 1
        val store = store(storeID = storeId)
        var callCount = 0
        everySuspend { storesRepository.getStore(storeId) } calls {
            callCount++
            if (callCount == 1) throw RuntimeException("boom") else store
        }

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        // First load failed.
        assertEquals(StoreViewModel.StoreScreenData.Error, emissions.last())

        viewModel.retry()
        advanceUntilIdle()

        // After retry, the store details load succeeds.
        assertEquals(StoreViewModel.StoreScreenData.Data(store), emissions.last())
        verifySuspend(exactly(2)) { storesRepository.getStore(storeId) }
    }

    @Test
    fun retry_resubscribes_deals_hot_source() = runTest {
        val storeId = 1
        val store = store(storeID = storeId)
        everySuspend { storesRepository.getStore(storeId) } returns store

        // Use MutableSharedFlow for the hot 'observeStoreDeals' source — flowOf would complete after one emission and mask second-emission/re-subscription
        // behaviour (L-2026-05-02-05).
        val dealsFlow = MutableSharedFlow<List<Deal>>(replay = 1)
        every { dealsRepository.observeStoreDeals(storeId) } returns dealsFlow

        val viewModel = createViewModel(storeId)
        viewModel.deals.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        viewModel.retry()
        runCurrent()

        // Each subscription (initial + retry) results in a separate call to observeStoreDeals.
        verify(exactly(2)) { dealsRepository.observeStoreDeals(storeId) }
    }

    @Test
    fun retry_emits_Loading_before_re_running_failed_store_details_load() = runTest {
        val storeId = 1
        everySuspend { storesRepository.getStore(storeId) } throws Exception()

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(StoreViewModel.StoreScreenData.Error, emissions.last())

        viewModel.retry()
        advanceUntilIdle()

        // After retry the chain re-runs onStart { emit(Loading) } before the catch lands again. The user-visible flow is Loading→Error→Loading→Error, with at
        // least one Loading mid-stream.
        assertEquals(
            true,
            emissions.contains(StoreViewModel.StoreScreenData.Loading),
            "Expected a Loading emission across the initial load and retry; got $emissions",
        )
        assertEquals(StoreViewModel.StoreScreenData.Error, emissions.last())
    }

    @Test
    fun rapid_retry_calls_settle_in_Data_state_without_emitting_an_intermediate_Error() = runTest {
        val storeId = 1
        val store = store(storeID = storeId)
        everySuspend { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        // Five rapid retry() calls before the dispatcher gets a chance to drain. retryTrigger is a MutableStateFlow<Int>; .update { it+1 } x5 will compact
        // through conflation, but even with all five distinct values reaching combine the final state must be Data.
        repeat(5) { viewModel.retry() }
        runCurrent()

        assertEquals(StoreViewModel.StoreScreenData.Data(store), emissions.last())
    }

    @Test
    fun retry_after_repeated_failures_eventually_recovers_when_repository_starts_succeeding() = runTest {
        val storeId = 1
        val store = store(storeID = storeId)
        var callCount = 0
        everySuspend { storesRepository.getStore(storeId) } calls {
            callCount++
            if (callCount <= 2) throw RuntimeException("boom $callCount") else store
        }

        val viewModel = createViewModel(storeId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        assertEquals(StoreViewModel.StoreScreenData.Error, emissions.last())

        viewModel.retry() // call #2 — still throws
        runCurrent()
        assertEquals(StoreViewModel.StoreScreenData.Error, emissions.last())

        viewModel.retry() // call #3 — succeeds
        runCurrent()
        assertEquals(StoreViewModel.StoreScreenData.Data(store), emissions.last())
    }

    @Test
    fun loadDealDetails_then_dismiss_round_trip() = runTest {
        everySuspend { storesRepository.getStore(any()) } returns store(storeID = 1)
        everySuspend { dealsRepository.getDeal(any()) } returns dealDetails()

        val viewModel = createViewModel(storeId = 1)
        val emissions = viewModel.dealDetails.observeEmissions(this.backgroundScope, testDispatcher)

        assertNull(emissions.last())

        viewModel.loadDealDetails(
            dealId = "deal-1",
            dealStoreId = 1,
            dealGameId = "42",
            dealTitle = "Halo",
            dealPriceDenominated = "$9.99",
            dealUrl = "https://deal-url",
        )
        advanceUntilIdle()

        val loaded = assertNotNull(emissions.last())
        assertIs<DealBottomSheetData.DealDetailsData>(loaded)
        assertEquals("42", loaded.gameId)
        assertEquals("Halo", loaded.gameName)

        viewModel.dismissDealDetails()
        advanceUntilIdle()

        assertNull(emissions.last())
    }
}
