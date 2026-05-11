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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.deal
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HomeViewModelTest : MainDispatcherTest() {

    private lateinit var viewModel: HomeViewModel

    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit)
    private val releasesRepository: ReleasesRepository = mock(MockMode.autoUnit)
    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit)
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)

    private val logger: TestingLoggingListener = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun initially_loading_state() = runTest {
        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.SUCCESS), emissions.first())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(0)) { dealsRepository.getStoreDeals(any(), any()) }
    }

    @Test
    fun load_store_deals_from_source() = runTest {
        val store = store(storeID = topStores.first())
        val deal = deal()

        every { storesRepository.observeStores() } returns flowOf(listOf(store))
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) } returns listOf(deal)

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)
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

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)
        val emissions = observeStates()

        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.first())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(0)) { dealsRepository.getStoreDeals(any(), any()) }
    }

    @Test
    fun onReleaseGame_title_success() = runTest {
        val releaseTitle = "title"
        val gameId = 1

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { gamesRepository.getReleaseGameId(releaseTitle) } returns gameId

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)
        val emissions = observeStates()
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        viewModel.onReleaseGame(releaseTitle)

        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.SUCCESS), emissions.first())

        assertEquals(1, events.size)
        assertNotNull(events.first())
        assertEquals(HomeViewModel.HomeUiEvent.NavigateToGame(gameId), events.first())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(1)) { gamesRepository.getReleaseGameId(releaseTitle) }
    }

    @Test
    fun onReleaseGame_title_exception_surfaces_ERROR_without_crashing() = runTest {
        val releaseTitle = "title"

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { gamesRepository.getReleaseGameId(any()) } throws Exception()

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)
        val emissions = observeStates()
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        viewModel.onReleaseGame(releaseTitle)

        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.last())
        assertNull(events.firstOrNull())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(1)) { gamesRepository.getReleaseGameId(releaseTitle) }
    }

    @Test
    fun onReleaseGame_missing_game_surfaces_ERROR_without_crashing() = runTest {
        val releaseTitle = "title"

        every { storesRepository.observeStores() } returns flowOf(listOf())
        every { releasesRepository.observeReleases() } returns flowOf(listOf())
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        everySuspend { gamesRepository.getReleaseGameId(releaseTitle) } returns null

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)
        val emissions = observeStates()
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        viewModel.onReleaseGame(releaseTitle)

        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.last())
        assertNull(events.firstOrNull())

        verify(exactly(1)) { storesRepository.observeStores() }
        verifySuspend(exactly(1)) { gamesRepository.getReleaseGameId(releaseTitle) }
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

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)
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

        // The single-threaded TestScope means a regular `var` works as the in-flight counter
        // (no JVM AtomicInteger required for KMP portability).
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

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, dealShareTextBuilder, logger)
        runCurrent()

        storesFlow.emit(listOf(storeOne, storeTwo))
        runCurrent()

        assertEquals(2, activeFetches)

        every { storesRepository.observeStores() } returns flowOf(listOf())
        viewModel.loadTopStoresDeals()
        runCurrent()

        assertEquals(0, activeFetches)
    }

    private fun TestScope.observeStates() =
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
}
