package pm.bam.gamedeals.feature.search.ui

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentSetOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.feature.search.generated.resources.Res
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filter_price_range_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filters_exact_match_switch_description
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filters_icon
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filters_rating_range_slider_description
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_list_empty_state_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_list_no_results_state_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_loading_indicator

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val searchViewModel: SearchViewModel = mockk()

    private lateinit var screenSemantics: ScreenSemantics

    @Before
    fun setup() {
        every { searchViewModel.favouriteIds } returns MutableStateFlow(persistentSetOf())
        every { searchViewModel.initialQuery } returns null
    }

    private fun setupCompose(
        onSearchedGame: (Int) -> Unit = { _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = onSearchedGame,
                    searchViewModel = searchViewModel,
                )
            }
        }
    }

    @Test
    fun onLoadEmptyState() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Empty)

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.emptyLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun noResultsOnLoad() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.NoResults)

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.noResultsLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun searchLoading() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Loading)

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun errorState() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Error)
        every { searchViewModel.searchGames(any(), any(), any(), any(), any()) } just Runs

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading)
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(screenSemantics.errorMsg)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(screenSemantics.retry)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }


        composeTestRule.onNodeWithText(screenSemantics.retry)
            .assertIsDisplayed()
            .performClick()

        verify(exactly = 1) { searchViewModel.searchGames(any()) }
    }

    @Test
    fun onResults() {
        val dealTitle = "Title"
        val singleDeal = makeMockDeal(dealId = "Id", gameId = 1, title = dealTitle)
        val data = SearchViewModel.SearchData.SearchResults(
            persistentListOf(GroupedSearchResult(gameID = 1, cheapestDeal = singleDeal, totalDealCount = 1))
        )

        every { searchViewModel.resultState } returns MutableStateFlow(data)

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading)
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(dealTitle)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun groupedResult_with_multiple_deals_renders_deal_count_badge() {
        val groupedDeal = makeMockDeal(dealId = "Id", gameId = 7, title = "Batman")
        val data = SearchViewModel.SearchData.SearchResults(
            persistentListOf(GroupedSearchResult(gameID = 7, cheapestDeal = groupedDeal, totalDealCount = 5))
        )

        every { searchViewModel.resultState } returns MutableStateFlow(data)

        setupCompose()

        composeTestRule.onNodeWithText("5 deals").assertIsDisplayed()
    }

    @Test
    fun groupedResult_with_single_deal_does_not_render_deal_count_badge() {
        val singleDeal = makeMockDeal(dealId = "Id", gameId = 7, title = "Solo Game")
        val data = SearchViewModel.SearchData.SearchResults(
            persistentListOf(GroupedSearchResult(gameID = 7, cheapestDeal = singleDeal, totalDealCount = 1))
        )

        every { searchViewModel.resultState } returns MutableStateFlow(data)

        setupCompose()

        composeTestRule.onNodeWithText("1 deals").assertDoesNotExist()
    }

    private fun makeMockDeal(dealId: String, gameId: Int, title: String): Deal = mockk {
        every { dealID } returns dealId
        every { gameID } returns gameId
        every { this@mockk.title } returns title
        every { salePriceDenominated } returns "Price"
        every { thumb } returns "Thumb"
    }

    @Test
    fun launched_with_initialQuery_prefills_search_field_with_that_text() {
        // Use a token unlikely to appear elsewhere on the screen so onNodeWithText resolves uniquely to the field.
        val prefill = "Halo Infinite Prefill Token"
        every { searchViewModel.initialQuery } returns prefill
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Loading)

        setupCompose()

        composeTestRule.onNodeWithText(prefill).assertIsDisplayed()
    }

    @Test
    fun onShowFilters() {
        openAndTestFilters()
    }

    @Test
    fun initialPriceRange() {
        openAndTestFilters()

        composeTestRule.onNodeWithText(rangeString(SearchFilterMinPrice, SearchFilterMaxPrice, SearchFilterMaxPrice))
            .assertIsDisplayed()
    }
    // TODO - Add tests for Price range change when RangeSlider value change supported with Input action, like SwipeRight.

    @Test
    fun initialRateRange() {
        openAndTestFilters()

        composeTestRule.onNodeWithText(valueString(SearchFilterMinRate, SearchFilterMaxRate))
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(screenSemantics.ratingSliderCd)
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(
                    current = SearchFilterMinRate,
                    range = SearchFilterMinRate..SearchFilterMaxRate,
                    steps = SearchFilterRateSteps
                )
            )
    }

    @Test
    fun initialExactMatchSelection() {
        openAndTestFilters()

        composeTestRule.onNodeWithContentDescription(screenSemantics.switchCd)
            .assertIsOff()
    }

    @Test
    fun toggleExactMatchSelection() {
        openAndTestFilters()

        composeTestRule.onNodeWithContentDescription(screenSemantics.switchCd)
            .assertIsOff()

        composeTestRule.onNodeWithContentDescription(screenSemantics.switchCd)
            .performTouchInput { swipeRight() }

        composeTestRule.onNodeWithContentDescription(screenSemantics.switchCd)
            .assertIsOn()
    }

    private fun openAndTestFilters() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Empty)

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.filtersIcon)
            .performClick()

        // Assert the sheet is visible via a known child label rather than a wrapper CD —
        // wrapper CDs mask their descendants in Compose's merged semantics tree.
        composeTestRule.onNodeWithText(screenSemantics.priceRangeLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 2) { searchViewModel.resultState }
    }

    private data class ScreenSemantics(
        val emptyLabel: String,
        val noResultsLabel: String,
        val loading: String,
        val errorMsg: String,
        val retry: String,
        val filtersIcon: String,
        val priceRangeLabel: String,
        val ratingSliderCd: String,
        val switchCd: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                emptyLabel = stringResource(Res.string.search_screen_list_empty_state_label),
                noResultsLabel = stringResource(Res.string.search_screen_list_no_results_state_label),
                loading = stringResource(Res.string.search_screen_loading_indicator),
                errorMsg = stringResource(Res.string.search_screen_data_loading_error_msg),
                retry = stringResource(Res.string.search_screen_data_loading_error_retry),
                filtersIcon = stringResource(Res.string.search_screen_filters_icon),
                priceRangeLabel = stringResource(Res.string.search_screen_filter_price_range_label),
                ratingSliderCd = stringResource(Res.string.search_screen_filters_rating_range_slider_description),
                switchCd = stringResource(Res.string.search_screen_filters_exact_match_switch_description),
            )
        }
    }
}
