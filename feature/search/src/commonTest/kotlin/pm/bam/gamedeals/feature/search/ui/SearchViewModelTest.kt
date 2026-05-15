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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
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

        delay(1200) // Delay because Flow 'flatMapLatestDelayAtLeast'

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

        delay(1200)

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
        delay(1200)
        assertEquals(SearchData.Error, emissions.last())

        viewModel.searchGames(title = "Second")
        delay(1200)
        assertEquals(SearchData.SearchResults(deals.toImmutableList()), emissions.last())
    }

    private fun TestScope.observeStates() =
        viewModel.resultState.observeEmissions(this.backgroundScope, testDispatcher)
}
