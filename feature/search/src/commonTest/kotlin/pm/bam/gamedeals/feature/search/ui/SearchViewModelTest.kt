@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.search.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.feature.search.ui.SearchViewModel.SearchData
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.deal
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import pm.bam.gamedeals.testing.utils.third
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchViewModelTest : MainDispatcherTest() {

    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit) {
        every { observeStores() } returns flowOf(emptyList())
    }
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf())
    }

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setup() {
        installMainDispatcher()
        viewModel = SearchViewModel(SavedStateHandle(), TestingLoggingListener(), gamesRepository, storesRepository, waitlistRepository)
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun initially_empty() = runTest {
        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
    }

    @Test
    fun search_results_in_empty_when_title_missing() = runTest {
        viewModel.searchGames(title = null)

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
    }

    @Test
    fun search_results_in_no_results() = runTest {
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns emptyList()

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())

        viewModel.searchGames(title = "Title")

        assertEquals(2, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
        assertEquals(SearchData.Loading, emissions.second())

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        assertEquals(SearchData.NoResults, emissions.third())
    }

    @Test
    fun search_results_in_results() = runTest {
        val deal = deal()
        val deals = listOf(deal)

        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns deals

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())

        viewModel.searchGames(title = "Title")

        assertEquals(2, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
        assertEquals(SearchData.Loading, emissions.second())

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        assertEquals(SearchData.SearchResults(deals.groupByGame().toImmutableList()), emissions.third())
    }

    @Test
    fun search_results_in_exception() = runTest {
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } throws Exception()

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())

        viewModel.searchGames(title = "Title")

        assertEquals(3, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
        assertEquals(SearchData.Loading, emissions.second())
        assertEquals(SearchData.Error, emissions.third())
    }

    @Test
    fun subsequent_search_after_error_still_produces_results() = runTest {
        val deal = deal()
        val deals = listOf(deal)

        everySuspend { gamesRepository.searchGames(searchParameters = any()) } sequentially {
            throws(Exception())
            returns(deals)
        }

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())

        viewModel.searchGames(title = "First")
        testScheduler.advanceTimeBy(1200)
        runCurrent()
        assertEquals(SearchData.Error, emissions.last())

        viewModel.searchGames(title = "Second")
        testScheduler.advanceTimeBy(1200)
        runCurrent()
        assertEquals(SearchData.SearchResults(deals.groupByGame().toImmutableList()), emissions.last())
    }

    @Test
    fun waitlistIds_initial_value_is_empty_set() = runTest {
        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(emptySet<String>(), ids.first())
    }

    @Test
    fun waitlistIds_emits_values_from_repository() = runTest {
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf("1", "2", "3"))
        // Rebuild because the @BeforeTest viewModel captured the original stub.
        viewModel = SearchViewModel(SavedStateHandle(), TestingLoggingListener(), gamesRepository, storesRepository, waitlistRepository)

        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)
        assertEquals(setOf("1", "2", "3"), ids.last())
    }

    @Test
    fun waitlistIds_recovers_from_repository_error_with_empty_set() = runTest {
        every { waitlistRepository.observeWaitlistIds() } returns flow { throw Exception() }
        viewModel = SearchViewModel(SavedStateHandle(), TestingLoggingListener(), gamesRepository, storesRepository, waitlistRepository)

        val ids = viewModel.waitlistIds.observeEmissions(this.backgroundScope, testDispatcher)
        assertEquals(emptySet<String>(), ids.last())
    }

    @Test
    fun searchGames_with_only_lowerPrice_filter_triggers_search_even_when_title_is_null() = runTest {
        val deal = deal()
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns listOf(deal)

        val emissions = observeStates()
        viewModel.searchGames(lowerPrice = 5)

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        // The init flow's `dealsSearch.title == null && lowerPrice == null && ...` branch must be false when lowerPrice is set; otherwise the search
        // short-circuits to Empty.
        assertEquals(SearchData.SearchResults(listOf(deal).groupByGame().toImmutableList()), emissions.last())
    }

    @Test
    fun rapid_searchGames_calls_settle_on_the_latest_query() = runTest {
        val firstDeal = deal(dealID = "first")
        val secondDeal = deal(dealID = "second")
        val thirdDeal = deal(dealID = "third")
        // Each title queries a different result so we can verify the final emission is the result of the LATEST query, not any of the earlier ones.
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } sequentially {
            returns(listOf(firstDeal))
            returns(listOf(secondDeal))
            returns(listOf(thirdDeal))
        }

        val emissions = observeStates()

        viewModel.searchGames(title = "first")
        viewModel.searchGames(title = "second")
        viewModel.searchGames(title = "third")

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        // flatMapLatestDelayAtLeast cancels prior pads before they fire `emit`, so only the final query's SearchResults reaches the resultState. The
        // intermediate first/second deals never surface even though the mock was invoked for them.
        assertEquals(SearchData.SearchResults(listOf(thirdDeal).groupByGame().toImmutableList()), emissions.last())
    }

    @Test
    fun initialQuery_in_saved_state_seeds_search_parameters_and_triggers_search() = runTest {
        val deal = deal()
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns listOf(deal)

        viewModel = SearchViewModel(
            SavedStateHandle(mapOf("initialQuery" to "Halo Infinite")),
            TestingLoggingListener(),
            gamesRepository,
            storesRepository,
            waitlistRepository,
        )

        assertEquals("Halo Infinite", viewModel.initialQuery)

        val emissions = observeStates()
        // The seed flow already fired before observeStates; first replay shows Loading.
        assertEquals(SearchData.Loading, emissions.first())

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        assertEquals(SearchData.SearchResults(listOf(deal).groupByGame().toImmutableList()), emissions.last())
    }

    @Test
    fun blank_initialQuery_does_not_seed_search() = runTest {
        viewModel = SearchViewModel(
            SavedStateHandle(mapOf("initialQuery" to "")),
            TestingLoggingListener(),
            gamesRepository,
            storesRepository,
            waitlistRepository,
        )

        assertEquals(null, viewModel.initialQuery)

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
    }

    private fun TestScope.observeStates() =
        viewModel.resultState.observeEmissions(this.backgroundScope, testDispatcher)
}
