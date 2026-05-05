@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)

package pm.bam.gamedeals.feature.search.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.feature.search.ui.SearchViewModel.SearchData
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import pm.bam.gamedeals.testing.utils.third
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lifted to commonTest in phase-A5c. The previous Android-only version used
 * MockK + JUnit's `@get:Rule MainCoroutineRule()`. Mokkery replaces MockK; the
 * `MainCoroutineRule` JUnit-`TestWatcher` shape doesn't have a KMP equivalent,
 * so the per-test `Dispatchers.setMain` / `resetMain` is inlined into
 * `@BeforeTest` / `@AfterTest`. `mockk<Deal>()` becomes a constructed real
 * Deal — Mokkery cannot mock final classes, and the previous mock was an
 * opaque pass-through value with no method calls on it.
 */
class SearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)

    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SearchViewModel(TestingLoggingListener(), gamesRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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

private fun deal(
    dealID: String = "deal-1",
    internalName: String = "TEST",
    title: String = "Test Deal",
    storeID: Int = 1,
    gameID: Int = 100,
    salePriceValue: Double = 9.99,
    salePriceDenominated: String = "$9.99",
    normalPriceValue: Double = 19.99,
    normalPriceDenominated: String = "$19.99",
    isOnSale: Boolean = true,
    savings: Double = 50.0,
    metacriticScore: Int = 80,
    steamRatingPercent: Int = 90,
    steamRatingCount: String = "100",
    releaseDate: Int = 0,
    lastChange: Int = 0,
    dealRating: Double = 9.0,
    thumb: String = "thumb",
) = Deal(
    dealID = dealID,
    internalName = internalName,
    title = title,
    storeID = storeID,
    gameID = gameID,
    salePriceValue = salePriceValue,
    salePriceDenominated = salePriceDenominated,
    normalPriceValue = normalPriceValue,
    normalPriceDenominated = normalPriceDenominated,
    isOnSale = isOnSale,
    savings = savings,
    metacriticScore = metacriticScore,
    steamRatingPercent = steamRatingPercent,
    steamRatingCount = steamRatingCount,
    releaseDate = releaseDate,
    lastChange = lastChange,
    dealRating = dealRating,
    thumb = thumb,
)
