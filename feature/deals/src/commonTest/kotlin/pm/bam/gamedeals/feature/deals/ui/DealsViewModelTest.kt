@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)

package pm.bam.gamedeals.feature.deals.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSortDirection
import pm.bam.gamedeals.domain.models.DealsSortField
import pm.bam.gamedeals.domain.models.ProductType
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.testing.FakeFeatureFlags
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.deal
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
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit) {
        every { observeCollectionIds() } returns flowOf(persistentSetOf())
    }
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        every { observeSelectedCountry() } returns flowOf(DEFAULT_COUNTRY)
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIgnoredIds() } returns flowOf(persistentSetOf())
    }
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)

    // Real backing flows so setMature / setDealsFilter persist and the browse list re-derives, like
    // SettingsRepositoryImpl (get*/set* read & write the same flow the init combine collects).
    private val matureFlow = MutableStateFlow(false)
    private val dealsFilterFlow = MutableStateFlow(DealsFilter())
    private val settingsRepository: SettingsRepository = mock(MockMode.autoUnit) {
        every { observeMatureOptIn() } returns matureFlow
        everySuspend { setMatureOptIn(any()) } calls { (enabled: Boolean) -> matureFlow.value = enabled }
        every { observeDealsFilter() } returns dealsFilterFlow
        everySuspend { getDealsFilter() } calls { dealsFilterFlow.value }
        everySuspend { setDealsFilter(any()) } calls { (filter: DealsFilter) -> dealsFilterFlow.value = filter }
    }

    private val featureFlags = FakeFeatureFlags()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel() = DealsViewModel(
        logger = TestingLoggingListener(),
        dealsRepository = dealsRepository,
        storesRepository = storesRepository,
        dealShareTextBuilder = dealShareTextBuilder,
        waitlistRepository = waitlistRepository,
        collectionRepository = collectionRepository,
        regionRepository = regionRepository,
        ignoredRepository = ignoredRepository,
        gamesRepository = gamesRepository,
        settingsRepository = settingsRepository,
        featureFlags = featureFlags,
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
        everySuspend { dealsRepository.getDeals(DealsQuery(offset = 0)) } returns fullPage // default sort = Hottest/Descending
        everySuspend { dealsRepository.getDeals(DealsQuery(offset = DealsQuery.DEALS_PAGE_SIZE)) } returns secondPage

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
    fun setSortField_reloads_from_offset_zero_and_resets_direction_to_field_default() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        // Price's default direction is Ascending, so selecting it also flips the direction from the
        // initial Hottest/Descending — mirroring the website's behaviour when a field is picked.
        vm.setSortField(DealsSortField.Price)
        advanceUntilIdle()

        assertEquals(DealsSortField.Price, vm.uiState.value.sortField)
        assertEquals(DealsSortDirection.Ascending, vm.uiState.value.sortDirection)
        verifySuspend(exactly(1)) {
            dealsRepository.getDeals(DealsQuery(sortField = DealsSortField.Price, sortDirection = DealsSortDirection.Ascending, offset = 0))
        }
    }

    @Test
    fun setSortDirection_reloads_from_offset_zero_keeping_the_current_field() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        // Flip the initial Hottest/Descending to ascending without changing the field.
        vm.setSortDirection(DealsSortDirection.Ascending)
        advanceUntilIdle()

        assertEquals(DealsSortField.Hottest, vm.uiState.value.sortField)
        assertEquals(DealsSortDirection.Ascending, vm.uiState.value.sortDirection)
        verifySuspend(exactly(1)) {
            dealsRepository.getDeals(DealsQuery(sortField = DealsSortField.Hottest, sortDirection = DealsSortDirection.Ascending, offset = 0))
        }
    }

    @Test
    fun toggleShop_reloads_page_zero_with_selected_shop_ids() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleShop(61)
        advanceUntilIdle()

        assertEquals(setOf(61), vm.uiState.value.shopIds)
        verifySuspend(exactly(1)) { dealsRepository.getDeals(DealsQuery(shopIds = listOf(61), offset = 0)) }
    }

    @Test
    fun toggle_waitlist_when_logged_out_emits_SignInRequired() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))
        everySuspend { waitlistRepository.toggleWaitlist("42") } returns RepoUpdateResult.NOT_LOGGED_IN

        val vm = createViewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.toggleWaitlist("42")
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(DealsViewModel.DealsUiEvent.SignInRequired, events.first())
    }

    @Test
    fun mature_opt_in_change_reloads_page_zero_with_mature() = runTest {
        // The mature toggle now lives in Account; the Deals list just reacts to the shared persisted flag.
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        matureFlow.value = true
        advanceUntilIdle()

        assertTrue(vm.uiState.value.mature)
        verifySuspend(exactly(1)) { dealsRepository.getDeals(DealsQuery(mature = true, offset = 0)) }
    }

    @Test
    fun setMinCut_reloads_page_zero_with_filter_and_persists() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setMinCut(50)
        advanceUntilIdle()

        val expectedFilter = DealsFilter(minCutPercent = 50)
        assertEquals(expectedFilter, dealsFilterFlow.value) // persisted via SettingsRepository
        assertEquals(expectedFilter, vm.uiState.value.filter) // reload carried the new filter
        verifySuspend(exactly(1)) {
            dealsRepository.getDeals(DealsQuery(filter = expectedFilter, offset = 0))
        }
    }

    @Test
    fun toggleType_accumulates_then_clearFilters_resets() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleType(ProductType.Game)
        advanceUntilIdle()
        vm.toggleType(ProductType.Dlc)
        advanceUntilIdle()
        assertEquals(setOf(ProductType.Game, ProductType.Dlc), dealsFilterFlow.value.types)

        vm.toggleType(ProductType.Game) // toggles Game back off
        advanceUntilIdle()
        assertEquals(setOf(ProductType.Dlc), dealsFilterFlow.value.types)

        vm.clearFilters()
        advanceUntilIdle()
        assertEquals(DealsFilter(), dealsFilterFlow.value)
        assertTrue(vm.uiState.value.filter.isEmpty())
    }

    @Test
    fun discover_hidden_by_default_then_reacts_to_the_feature_flag() = runTest {
        // Staged rollout: the Discover-by-Tag entry point defaults off and flips on when the flag provider
        // enables it — without recreating the screen (the value can arrive after first composition).
        everySuspend { dealsRepository.getDeals(any()) } returns emptyList()

        val vm = createViewModel()
        val emissions = vm.discoverEnabled.observeEmissions(this.backgroundScope, testDispatcher)
        advanceUntilIdle()
        assertFalse(emissions.last()) // default = hidden

        featureFlags.set(FeatureFlag.DiscoverByTag, true)
        advanceUntilIdle()
        assertTrue(emissions.last()) // reacts to the remote flag flipping on
    }

    @Test
    fun search_results_are_idle_until_a_query_is_set() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(DealsViewModel.SearchResultsState.Idle, vm.searchResults.value)
    }

    @Test
    fun setSearchQuery_emits_loading_then_grouped_results() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns emptyList()
        val deals = listOf(deal("d1", gameID = "g1"))
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns deals

        val vm = createViewModel()
        advanceUntilIdle()
        val emissions = vm.searchResults.observeEmissions(this.backgroundScope, testDispatcher)

        vm.setSearchQuery("Halo")
        runCurrent()
        assertEquals(DealsViewModel.SearchResultsState.Loading, emissions.last())

        advanceTimeBy(1200)
        runCurrent()
        assertEquals(DealsViewModel.SearchResultsState.Results(deals.groupByGame().toImmutableList()), emissions.last())
    }

    @Test
    fun setSearchQuery_with_no_matches_emits_no_results() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns emptyList()
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()
        val emissions = vm.searchResults.observeEmissions(this.backgroundScope, testDispatcher)

        vm.setSearchQuery("zzzz")
        advanceTimeBy(1200)
        runCurrent()

        assertEquals(DealsViewModel.SearchResultsState.NoResults, emissions.last())
    }

    @Test
    fun clearSearch_returns_results_to_idle() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns emptyList()
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns listOf(deal("d1", gameID = "g1"))

        val vm = createViewModel()
        advanceUntilIdle()
        vm.setSearchQuery("Halo")
        advanceTimeBy(1200)
        runCurrent()

        vm.clearSearch()
        advanceUntilIdle()

        assertEquals(DealsViewModel.SearchResultsState.Idle, vm.searchResults.value)
    }
}
