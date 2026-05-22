@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.search.ui

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
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
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
    private val favouritesRepository: FavouritesRepository = mock(MockMode.autoUnit) {
        every { observeFavouriteIds() } returns flowOf(persistentSetOf())
    }

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setup() {
        installMainDispatcher()
        viewModel = SearchViewModel(TestingLoggingListener(), gamesRepository, favouritesRepository)
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

        assertEquals(SearchData.SearchResults(deals.toImmutableList()), emissions.third())
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
        assertEquals(SearchData.SearchResults(deals.toImmutableList()), emissions.last())
    }

    @Test
    fun favouriteIds_initial_value_is_empty_set() = runTest {
        val ids = viewModel.favouriteIds.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(emptySet<Int>(), ids.first())
    }

    @Test
    fun favouriteIds_emits_values_from_repository() = runTest {
        every { favouritesRepository.observeFavouriteIds() } returns flowOf(persistentSetOf(1, 2, 3))
        // Rebuild because the @BeforeTest viewModel captured the original stub.
        viewModel = SearchViewModel(TestingLoggingListener(), gamesRepository, favouritesRepository)

        val ids = viewModel.favouriteIds.observeEmissions(this.backgroundScope, testDispatcher)
        assertEquals(setOf(1, 2, 3), ids.last())
    }

    @Test
    fun favouriteIds_recovers_from_repository_error_with_empty_set() = runTest {
        every { favouritesRepository.observeFavouriteIds() } returns flow { throw Exception() }
        viewModel = SearchViewModel(TestingLoggingListener(), gamesRepository, favouritesRepository)

        val ids = viewModel.favouriteIds.observeEmissions(this.backgroundScope, testDispatcher)
        assertEquals(emptySet<Int>(), ids.last())
    }

    @Test
    fun searchGames_with_only_lowerPrice_filter_triggers_search_even_when_title_is_null() = runTest {
        val deal = deal()
        everySuspend { gamesRepository.searchGames(searchParameters = any()) } returns listOf(deal)

        val emissions = observeStates()
        viewModel.searchGames(lowerPrice = 5)

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        // The init flow's `dealsSearch.title == null && lowerPrice == null && ...` branch must
        // be false when lowerPrice is set; otherwise the search short-circuits to Empty.
        assertEquals(SearchData.SearchResults(listOf(deal).toImmutableList()), emissions.last())
    }

    @Test
    fun rapid_searchGames_calls_settle_on_the_latest_query() = runTest {
        val firstDeal = deal(dealID = "first")
        val secondDeal = deal(dealID = "second")
        val thirdDeal = deal(dealID = "third")
        // Each title queries a different result so we can verify the final emission is the
        // result of the LATEST query, not any of the earlier ones.
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

        // flatMapLatestDelayAtLeast cancels prior pads before they fire `emit`, so only the
        // final query's SearchResults reaches the resultState. The intermediate first/second
        // deals never surface even though the mock was invoked for them.
        assertEquals(SearchData.SearchResults(listOf(thirdDeal).toImmutableList()), emissions.last())
    }

    private fun TestScope.observeStates() =
        viewModel.resultState.observeEmissions(this.backgroundScope, testDispatcher)
}
