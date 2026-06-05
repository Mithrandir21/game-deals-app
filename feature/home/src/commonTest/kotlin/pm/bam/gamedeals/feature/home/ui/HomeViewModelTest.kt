@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.home.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.verify
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.deal
import pm.bam.gamedeals.testing.fixtures.dealDetails
import pm.bam.gamedeals.testing.fixtures.gameInfo
import pm.bam.gamedeals.testing.fixtures.giveaway
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HomeViewModelTest : MainDispatcherTest() {

    private lateinit var viewModel: HomeViewModel

    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit)
    private val releasesRepository: ReleasesRepository = mock(MockMode.autoUnit)
    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit)
    private val bundlesRepository: BundlesRepository = mock(MockMode.autoUnit) {
        everySuspend { getBundles() } returns emptyList()
    }
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf())
    }
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        every { observeSelectedCountry() } returns flowOf(DEFAULT_COUNTRY)
    }

    private val logger: TestingLoggingListener = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun initially_loading_state() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.SUCCESS), emissions.first())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(0)) { dealsRepository.getStoreDeals(any(), any()) }
    }

    @Test
    fun load_new_releases_capped_at_five() = runTest {
        val releases = (1..8).map { Release(title = "r$it", date = 0, image = "") }

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(releases)
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(
            HomeScreenData(state = HomeScreenStatus.SUCCESS, releases = releases.take(LIMIT_RELEASES).toImmutableList()),
            emissions.first()
        )
    }

    @Test
    fun load_store_deals_from_source() = runTest {
        val store = store(storeID = topStores.first())
        val deal = deal()

        every { storesRepository.observeStores() } returns flowOf(listOf(store))
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) } returns listOf(deal)

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        val data = mutableListOf<HomeScreenListData>().apply {
            add(HomeScreenListData.StoreData(store))
            add(HomeScreenListData.DealData(deal))
            add(HomeScreenListData.ViewAllData(store))
        }

        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.SUCCESS, items = data.toImmutableList()), emissions.first())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(1)) { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) }
    }

    @Test
    fun load_store_deals_from_source_failure() = runTest {
        every { storesRepository.observeStores() } throws Exception()

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.first())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(0)) { dealsRepository.getStoreDeals(any(), any()) }
    }

    @Test
    fun onReleaseGame_resolves_deal_and_opens_bottom_sheet() = runTest {
        val releaseTitle = "title"
        val releaseDeal = deal(dealID = "deal-1", storeID = 1, gameID = "42", title = "Halo", salePriceDenominated = "$9.99")

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { gamesRepository.getReleaseDeal(releaseTitle) } returns releaseDeal
        everySuspend { storesRepository.getStore(any()) } returns store(storeID = 1)
        everySuspend { dealsRepository.getDeal(any()) } returns dealDetails()

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val dealEmissions = viewModel.dealDetails.observeEmissions(this.backgroundScope, testDispatcher)
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        viewModel.onReleaseGame(releaseTitle)
        advanceUntilIdle()

        val loaded = assertNotNull(dealEmissions.last())
        assertIs<DealBottomSheetData.DealDetailsData>(loaded)
        assertEquals("42", loaded.gameId)
        assertEquals("Halo", loaded.gameName)

        assertNull(events.firstOrNull())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(1)) { gamesRepository.getReleaseDeal(releaseTitle) }
    }

    @Test
    fun onReleaseGame_title_exception_surfaces_ERROR_without_crashing() = runTest {
        val releaseTitle = "title"

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { gamesRepository.getReleaseDeal(any()) } throws Exception()

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        viewModel.onReleaseGame(releaseTitle)

        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.last())
        assertNull(events.firstOrNull())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(1)) { gamesRepository.getReleaseDeal(releaseTitle) }
    }

    @Test
    fun onReleaseGame_missing_game_surfaces_ERROR_without_crashing() = runTest {
        val releaseTitle = "title"

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { gamesRepository.getReleaseDeal(releaseTitle) } returns null

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        viewModel.onReleaseGame(releaseTitle)

        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.last())
        assertNull(events.firstOrNull())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(1)) { gamesRepository.getReleaseDeal(releaseTitle) }
    }

    @Test
    fun loadTopStoresDeals_cancels_prior_collector_before_relaunching() = runTest {
        val store = store(storeID = topStores.first())
        val deal = deal()
        val storesFlow = MutableSharedFlow<List<Store>>(replay = 1)

        every { storesRepository.observeStores() } returns storesFlow
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) } returns listOf(deal)

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        runCurrent()

        viewModel.loadTopStoresDeals()
        viewModel.loadTopStoresDeals()
        viewModel.loadTopStoresDeals()
        runCurrent()

        assertEquals(1, storesFlow.subscriptionCount.value)

        storesFlow.emit(listOf(store))
        runCurrent()

        verifySuspend(exactly(1)) { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) }
    }

    @Test
    fun loadTopStoresDeals_cancellation_propagates_to_in_flight_per_store_deal_fetches() = runTest {
        val storeOne = store(storeID = topStores[0])
        val storeTwo = store(storeID = topStores[1])
        val storesFlow = MutableSharedFlow<List<Store>>(replay = 1)

        // The single-threaded TestScope means a regular `var` works as the in-flight counter (no JVM AtomicInteger required for KMP portability).
        var activeFetches = 0
        val gateOne = CompletableDeferred<List<Deal>>()
        val gateTwo = CompletableDeferred<List<Deal>>()

        every { storesRepository.observeStores() } returns storesFlow
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { dealsRepository.getStoreDeals(topStores[0], LIMIT_DEALS) } calls {
            activeFetches++
            try {
                gateOne.await()
            } finally {
                activeFetches--
            }
        }
        everySuspend { dealsRepository.getStoreDeals(topStores[1], LIMIT_DEALS) } calls {
            activeFetches++
            try {
                gateTwo.await()
            } finally {
                activeFetches--
            }
        }

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        runCurrent()

        storesFlow.emit(listOf(storeOne, storeTwo))
        runCurrent()

        assertEquals(2, activeFetches)

        every { storesRepository.observeStores() } returns flowOf(listOf())
        viewModel.loadTopStoresDeals()
        runCurrent()

        assertEquals(0, activeFetches)
    }

    @Test
    fun toggleWaitlistFromDeal_delegates_to_repository_with_data_fields() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)

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
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(emptySet<String>(), ids.first())
    }

    @Test
    fun waitlistIds_emits_values_from_repository() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf("1", "2", "3"))

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(setOf("1", "2", "3"), ids.last())
    }

    @Test
    fun waitlistIds_recovers_from_repository_error_with_empty_set() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        every { waitlistRepository.observeWaitlistIds() } returns flow { throw Exception() }

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(emptySet<String>(), ids.last())
    }


    @Test
    fun onShareDealClicked_emits_ShareDeal_event_with_built_text() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        every { dealShareTextBuilder.build(any(), any(), any(), any()) } returns "Built share text"

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
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
        assertEquals(HomeViewModel.HomeUiEvent.ShareDeal("Built share text"), events.first())

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
    fun loadDealDetails_then_dismiss_round_trip() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { storesRepository.getStore(any()) } returns store(storeID = 1)
        everySuspend { dealsRepository.getDeal(any()) } returns dealDetails()

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
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

    @Test
    fun loadGiveaways_filters_out_Expired_status() = runTest {
        val active = giveaway(id = 1, title = "Active one", status = "Active")
        val expired = giveaway(id = 2, title = "Expired one", status = "Expired")

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf(active, expired))

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        val giveaways = emissions.last().giveaways
        assertEquals(1, giveaways.size)
        assertEquals(active.id, giveaways.first().id)
    }

    @Test
    fun loadGiveaways_caps_to_LIMIT_GIVEAWAYS_active_after_filtering_Expired() = runTest {
        val active = (1..7).map { giveaway(id = it, title = "Active $it", status = "Active") }
        val expired = (100..102).map { giveaway(id = it, title = "Expired $it", status = "Expired") }

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(active + expired)

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        val giveaways = emissions.last().giveaways
        assertEquals(LIMIT_GIVEAWAYS, giveaways.size)
        assertEquals(0, giveaways.count { it.status.equals("Expired", ignoreCase = true) })
    }

    @Test
    fun loadGiveaways_Expired_filter_is_case_insensitive() = runTest {
        val mixed = listOf(
            giveaway(id = 1, status = "Expired"),
            giveaway(id = 2, status = "EXPIRED"),
            giveaway(id = 3, status = "expired"),
            giveaway(id = 4, status = "Active"),
        )

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(mixed)

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        val giveaways = emissions.last().giveaways
        assertEquals(1, giveaways.size)
        assertEquals(4, giveaways.first().id)
    }

    @Test
    fun loadNewReleases_failure_does_not_crash_uiState_pipeline() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flow { throw Exception("releases boom") }
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        // The .catch on loadNewReleases swaps the failure for an empty list, so the outer pipeline still produces SUCCESS — not ERROR — even though releases
        // threw mid-stream.
        assertEquals(HomeScreenStatus.SUCCESS, emissions.last().state)
        assertEquals(0, emissions.last().releases.size)
    }

    @Test
    fun loadGiveaways_failure_does_not_crash_uiState_pipeline() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flow { throw Exception("giveaways boom") }

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        // Same shape as loadNewReleases above — the per-source .catch isolates the failure so the rest of the screen still resolves to SUCCESS with an empty
        // giveaways list.
        assertEquals(HomeScreenStatus.SUCCESS, emissions.last().state)
        assertEquals(0, emissions.last().giveaways.size)
    }

    @Test
    fun loadGiveaways_failure_in_refreshGiveaways_does_not_crash_uiState_pipeline() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { giveawaysRepository.refreshGiveaways() } throws Exception("refresh boom")

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, bundlesRepository, waitlistRepository, dealShareTextBuilder, regionRepository, logger)
        val emissions = observeStates()

        // refreshGiveaways is called inside `.onStart { ... }` and a throw there is caught by the outer `.catch` on the loadGiveaways flow.
        assertEquals(HomeScreenStatus.SUCCESS, emissions.last().state)
    }

    private fun TestScope.observeStates() =
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
}
