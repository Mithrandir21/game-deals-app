package pm.bam.gamedeals.feature.home.ui


import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus
import pm.bam.gamedeals.testing.MainCoroutineRule
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel

    private val storesRepository: StoresRepository = mockk()

    private val gamesRepository: GamesRepository = mockk()

    private val dealsRepository: DealsRepository = mockk()

    private val releasesRepository: ReleasesRepository = mockk()

    private val giveawaysRepository: GiveawaysRepository = mockk()

    private val logger: TestingLoggingListener = TestingLoggingListener()


    @Test
    fun `initially loading state`() = runTest {
        coEvery { storesRepository.observeStores() } returns flowOf(listOf())
        coEvery { releasesRepository.observeReleases() } returns flowOf(listOf())
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        coEvery { giveawaysRepository.refreshGiveaways() } returns Unit

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, logger)

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.SUCCESS), emissions.first())

        coVerify(exactly = 1) { storesRepository.observeStores() }
        coVerify(exactly = 0) { dealsRepository.getStoreDeals(any(), any()) }
    }

    @Test
    fun `load store deals from source`() = runTest {
        val store: Store = mockk {
            every { storeID } returns topStores.first()
        }

        val deal: Deal = mockk()

        coEvery { storesRepository.observeStores() } returns flowOf(listOf(store))
        coEvery { releasesRepository.observeReleases() } returns flowOf(listOf())
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        coEvery { giveawaysRepository.refreshGiveaways() } returns Unit
        coEvery { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) } returns listOf(deal)

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, logger)
        val emissions = observeStates()

        val data = mutableListOf<HomeScreenListData>()
            .apply {
                add(HomeScreenListData.StoreData(store))
                add(HomeScreenListData.DealData(deal))
                add(HomeScreenListData.ViewAllData(store))
            }


        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.SUCCESS, items = data), emissions.first())


        coVerify(exactly = 1) { storesRepository.observeStores() }
        coVerify(exactly = 1) { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) }
    }

    @Test
    fun `load store deals from source failure`() = runTest {
        coEvery { storesRepository.observeStores() } throws Exception()

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, logger)
        val emissions = observeStates()

        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.first())


        coVerify(exactly = 1) { storesRepository.observeStores() }
        coVerify(exactly = 0) { dealsRepository.getStoreDeals(any(), any()) }
    }

    @Test
    fun `onReleaseGame title success`() = runTest {
        val releaseTitle = "title"
        val gameId = 1

        coEvery { storesRepository.observeStores() } returns flowOf(listOf())
        coEvery { releasesRepository.observeReleases() } returns flowOf(listOf())
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        coEvery { giveawaysRepository.refreshGiveaways() } returns Unit
        coEvery { gamesRepository.getReleaseGameId(releaseTitle) } returns gameId

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, logger)
        val emissions = observeStates()
        val events = viewModel.events.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

        viewModel.onReleaseGame(releaseTitle)

        assertEquals(1, emissions.size)
        assertNotNull(emissions.first())
        assertEquals(HomeScreenData(state = HomeScreenStatus.SUCCESS), emissions.first())

        assertEquals(1, events.size)
        assertNotNull(events.first())
        assertEquals(HomeViewModel.HomeUiEvent.NavigateToGame(gameId), events.first())

        coVerify(exactly = 1) { storesRepository.observeStores() }
        coVerify(exactly = 1) { gamesRepository.getReleaseGameId(releaseTitle) }
    }

    @Test(expected = Exception::class)
    fun `onReleaseGame title exception`() = runTest {
        val releaseTitle = "title"

        coEvery { storesRepository.observeStores() } returns flowOf(listOf())
        coEvery { releasesRepository.observeReleases() } returns flowOf(listOf())
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        coEvery { giveawaysRepository.refreshGiveaways() } returns Unit
        coEvery { gamesRepository.getReleaseGameId(any()) } throws Exception()

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, logger)
        val emissions = observeStates()
        val events = viewModel.events.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

        viewModel.onReleaseGame(releaseTitle)

        assertEquals(2, emissions.size)
        assertNotNull(emissions.second())
        assertEquals(HomeScreenData(state = HomeScreenStatus.ERROR), emissions.second())

        assertNull(events.firstOrNull())

        coVerify(exactly = 1) { storesRepository.observeStores() }
        coVerify(exactly = 1) { gamesRepository.getReleaseGameId(releaseTitle) }
    }

    @Test
    fun `loadTopStoresDeals cancels prior collector before relaunching`() = runTest {
        val store: Store = mockk { every { storeID } returns topStores.first() }
        val deal: Deal = mockk()
        val storesFlow = MutableSharedFlow<List<Store>>(replay = 1)

        coEvery { storesRepository.observeStores() } returns storesFlow
        coEvery { releasesRepository.observeReleases() } returns flowOf(listOf())
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(listOf())
        coEvery { giveawaysRepository.refreshGiveaways() } returns Unit
        coEvery { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) } returns listOf(deal)

        viewModel = HomeViewModel(storesRepository, dealsRepository, gamesRepository, releasesRepository, giveawaysRepository, logger)
        runCurrent()

        viewModel.loadTopStoresDeals()
        viewModel.loadTopStoresDeals()
        viewModel.loadTopStoresDeals()
        runCurrent()

        assertEquals(1, storesFlow.subscriptionCount.value)

        storesFlow.emit(listOf(store))
        runCurrent()

        coVerify(exactly = 1) { dealsRepository.getStoreDeals(topStores.first(), LIMIT_DEALS) }
    }

    private fun TestScope.observeStates() = viewModel.uiState.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

}