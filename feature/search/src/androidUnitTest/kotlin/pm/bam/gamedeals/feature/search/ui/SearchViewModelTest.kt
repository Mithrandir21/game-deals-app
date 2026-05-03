package pm.bam.gamedeals.feature.search.ui

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.feature.search.ui.SearchViewModel.SearchData
import pm.bam.gamedeals.testing.MainCoroutineRule
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import pm.bam.gamedeals.testing.utils.third

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val gamesRepository: GamesRepository = mockk()

    private lateinit var viewModel: SearchViewModel


    @Before
    fun setup() {
        viewModel = SearchViewModel(TestingLoggingListener(), gamesRepository)
    }

    @Test
    fun `initially empty`() = runTest {
        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
    }

    @Test
    fun `search results in empty - title missing`() = runTest {
        viewModel.searchGames(title = null)

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
    }

    @Test
    fun `search results in no results`() = runTest {
        coEvery { gamesRepository.searchGames(searchParameters = any()) } returns emptyList()

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
    fun `search results in results`() = runTest {
        val deal: Deal = mockk()
        val deals = listOf(deal)

        coEvery { gamesRepository.searchGames(searchParameters = any()) } returns deals

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())

        viewModel.searchGames(title = "Title")

        assertEquals(2, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())
        assertEquals(SearchData.Loading, emissions.second())

        delay(1200) // Delay because Flow 'flatMapLatestDelayAtLeast'

        assertEquals(SearchData.SearchResults(deals.toImmutableList()), emissions.third())
    }

    @Test
    fun `search results in exception`() = runTest {
        coEvery { gamesRepository.searchGames(searchParameters = any()) } throws Exception()

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
    fun `subsequent search after error still produces results`() = runTest {
        val deal: Deal = mockk()
        val deals = listOf(deal)

        coEvery { gamesRepository.searchGames(searchParameters = any()) } throws Exception() andThen deals

        val emissions = observeStates()
        assertEquals(1, emissions.size)
        assertEquals(SearchData.Empty, emissions.first())

        // First search fails — the upstream collector must survive the error.
        viewModel.searchGames(title = "First")
        delay(1200)
        assertEquals(SearchData.Error, emissions.last())

        // Second search after the error should still drive the upstream collector.
        viewModel.searchGames(title = "Second")
        delay(1200)
        assertEquals(SearchData.SearchResults(deals.toImmutableList()), emissions.last())
    }


    private fun TestScope.observeStates() = viewModel.resultState.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)
}