package pm.bam.gamedeals.feature.search.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.feature.search.R

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val searchViewModel: SearchViewModel = mockk()


    @Test
    fun onLoadEmptyState() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Empty)

        composeTestRule.setContent {
            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(SearchResultsListItemTag)
            .assertDoesNotExist()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun noResultsOnLoad() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.NoResults)

        composeTestRule.setContent {
            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(SearchResultsListItemTag)
            .assertDoesNotExist()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun searchLoading() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Loading)

        composeTestRule.setContent {
            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(SearchLoadingTag)
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(SearchResultsListItemTag)
            .assertDoesNotExist()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun errorState() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Error)
        every { searchViewModel.searchGames(any(), any(), any(), any(), any()) } just Runs

        var snackText = ""
        var snackRetry = ""

        composeTestRule.setContent {
            snackText = stringResource(id = R.string.search_screen_data_loading_error_msg)
            snackRetry = stringResource(id = R.string.search_screen_data_loading_error_retry)

            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(SearchLoadingTag)
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(SearchResultsListItemTag)
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
            every { title } returns dealTitle
            every { salePriceDenominated } returns dealSalePriceDenominated
            every { thumb } returns "Thumb"
        }
        val data = SearchViewModel.SearchData.SearchResults(persistentListOf(singleDeal))

        every { searchViewModel.resultState } returns MutableStateFlow(data)

        composeTestRule.setContent {
            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(SearchLoadingTag)
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(SearchResultsListItemTag)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 1) { searchViewModel.resultState }
    }

    @Test
    fun onShowFilters() {
        openAndTestFilters()

        composeTestRule.onNodeWithTag(SearchResultsListItemTag)
            .assertDoesNotExist()
    }

    @Test
    fun initialPriceRange() {
        openAndTestFilters()

        composeTestRule.onNodeWithTag(SearchFiltersPriceRangeLabelTag)
            .assertTextContains(rangeString(SearchFilterMinPrice, SearchFilterMinPrice, SearchFilterMaxPrice))

    }
    // TODO - Add tests for Price range change when RangeSlider value change supported with Input action, like SwipeRight.

    @Test
    fun initialRateRange() {
        openAndTestFilters()

        composeTestRule.onNodeWithTag(SearchFiltersRatingRangeLabelTag)
            .assertTextContains(valueString(SearchFilterMinRate, SearchFilterMaxRate))

        composeTestRule.onNodeWithTag(SearchFiltersRatingRangeTag)
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

        composeTestRule.onNodeWithTag(SearchFiltersExactMatchSwitchTag)
            .assertIsOff()
    }

    @Test
    fun toggleExactMatchSelection() {
        openAndTestFilters()

        composeTestRule.onNodeWithTag(SearchFiltersExactMatchSwitchTag)
            .assertIsOff()

        composeTestRule.onNodeWithTag(SearchFiltersExactMatchSwitchTag)
            .performTouchInput { swipeRight() }

        composeTestRule.onNodeWithTag(SearchFiltersExactMatchSwitchTag)
            .assertIsOn()
    }

    private fun openAndTestFilters() {
        every { searchViewModel.resultState } returns MutableStateFlow(SearchViewModel.SearchData.Empty)

        composeTestRule.setContent {
            GameDealsTheme {
                SearchScreen(
                    onSearchedGame = { _ -> },
                    searchViewModel = searchViewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(SearchFiltersIconTag)
            .performClick()

        composeTestRule.onNodeWithTag(SearchFiltersTag)
            .assertIsDisplayed()

        verify(exactly = 0) { searchViewModel.searchGames(any()) }
        verify(exactly = 2) { searchViewModel.resultState }
    }
}