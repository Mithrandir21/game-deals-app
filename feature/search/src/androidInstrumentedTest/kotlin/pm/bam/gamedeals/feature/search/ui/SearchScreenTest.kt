package pm.bam.gamedeals.feature.search.ui

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


    @Before
    fun setup() {
        every { searchViewModel.favouriteIds } returns MutableStateFlow(emptySet())
    }

    @Test
    fun onLoadEmptyState() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Empty)

        var emptyLabel = ""

        composeTestRule.setContent {
            emptyLabel = stringResource(Res.string.search_screen_list_empty_state_label)

            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithText(emptyLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun noResultsOnLoad() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.NoResults)

        var noResultsLabel = ""

        composeTestRule.setContent {
            noResultsLabel = stringResource(Res.string.search_screen_list_no_results_state_label)

            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithText(noResultsLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun searchLoading() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Loading)

        var loadingCd = ""

        composeTestRule.setContent {
            loadingCd = stringResource(Res.string.search_screen_loading_indicator)

            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingCd)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun errorState() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Error)
        every { searchViewModel.searchGames(any(), any(), any(), any(), any()) } just Runs

        var snackText = ""
        var snackRetry = ""
        var loadingCd = ""

        composeTestRule.setContent {
            snackText = stringResource(Res.string.search_screen_data_loading_error_msg)
            snackRetry = stringResource(Res.string.search_screen_data_loading_error_retry)
            loadingCd = stringResource(Res.string.search_screen_loading_indicator)

            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingCd)
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(snackText)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(snackRetry)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }


        composeTestRule.onNodeWithText(snackRetry)
            .assertIsDisplayed()
            .performClick()

        verify(exactly = 1) { searchViewModel.searchGames(any()) }
    }

    @Test
    fun onResults() {
        val dealId = "Id"
        val dealTitle = "Title"
        val dealSalePriceDenominated = "Price"

        val singleDeal: Deal = mockk {
            every { dealID } returns dealId
            every { gameID } returns 1
            every { title } returns dealTitle
            every { salePriceDenominated } returns dealSalePriceDenominated
            every { thumb } returns "Thumb"
        }
        val data = SearchViewModel.SearchData.SearchResults(persistentListOf(singleDeal))

        every { searchViewModel.resultState } returns MutableStateFlow(data)

        var loadingCd = ""

        composeTestRule.setContent {
            loadingCd = stringResource(Res.string.search_screen_loading_indicator)

            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingCd)
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(dealTitle)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
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
        val cds = openAndTestFilters()

        composeTestRule.onNodeWithText(valueString(SearchFilterMinRate, SearchFilterMaxRate))
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(cds.ratingSliderCd)
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
        val cds = openAndTestFilters()

        composeTestRule.onNodeWithContentDescription(cds.switchCd)
            .assertIsOff()
    }

    @Test
    fun toggleExactMatchSelection() {
        val cds = openAndTestFilters()

        composeTestRule.onNodeWithContentDescription(cds.switchCd)
            .assertIsOff()

        composeTestRule.onNodeWithContentDescription(cds.switchCd)
            .performTouchInput { swipeRight() }

        composeTestRule.onNodeWithContentDescription(cds.switchCd)
            .assertIsOn()
    }

    private data class FilterCds(
        val iconCd: String,
        val priceRangeLabel: String,
        val ratingSliderCd: String,
        val switchCd: String,
    )

    private fun openAndTestFilters(): FilterCds {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Empty)

        var iconCd = ""
        var priceRangeLabel = ""
        var ratingSliderCd = ""
        var switchCd = ""

        composeTestRule.setContent {
            iconCd = stringResource(Res.string.search_screen_filters_icon)
            priceRangeLabel = stringResource(Res.string.search_screen_filter_price_range_label)
            ratingSliderCd = stringResource(Res.string.search_screen_filters_rating_range_slider_description)
            switchCd = stringResource(Res.string.search_screen_filters_exact_match_switch_description)

            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(iconCd)
            .performClick()

        // Assert the sheet is visible via a known child label rather than a wrapper CD —
        // wrapper CDs mask their descendants in Compose's merged semantics tree.
        composeTestRule.onNodeWithText(priceRangeLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 2) { searchViewModel.resultState }

        return FilterCds(iconCd, priceRangeLabel, ratingSliderCd, switchCd)
    }
}
