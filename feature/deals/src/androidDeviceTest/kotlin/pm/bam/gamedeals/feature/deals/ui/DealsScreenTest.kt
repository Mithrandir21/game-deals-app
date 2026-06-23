package pm.bam.gamedeals.feature.deals.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.deals.generated.resources.Res
import pm.bam.gamedeals.feature.deals.generated.resources.deals_discover_by_tag
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_button
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_sheet_title
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_empty_label
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_loading_indicator
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_no_results_label
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel.DealsScreenData
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel.SearchResultsState

/**
 * Device UI coverage for the [DealsScreen] beyond the existing filter-sheet a11y test: the browse list
 * (render + tap-to-peek), the empty state, the filter/discover actions, and the reveal-on-search modes
 * (results + tap-to-peek, no-results, loading) — driven through a mocked [DealsViewModel].
 *
 * Uses createAndroidComposeRule<ComponentActivity> (not createComposeRule): the Deals screen has no top
 * bar (the app shell owns it), so states without focusable content otherwise trip
 * RootViewWithoutFocusException — same workaround as Home/WebView.
 */
class DealsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel: DealsViewModel = mockk(relaxed = true)

    private lateinit var labels: Labels

    @Before
    fun setup() {
        every { viewModel.waitlistIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.collectionIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.ignoredIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.stores } returns MutableStateFlow<ImmutableList<Store>>(persistentListOf())
        every { viewModel.selectedShops } returns MutableStateFlow<ImmutableSet<Int>>(persistentSetOf())
        every { viewModel.filter } returns MutableStateFlow(DealsFilter())
        every { viewModel.searchQuery } returns MutableStateFlow("")
        every { viewModel.searchResults } returns MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)
        every { viewModel.gamePeek } returns MutableStateFlow<GamePeekSheetData?>(null)
        every { viewModel.events } returns MutableSharedFlow()
    }

    private fun setContent(goToDiscover: () -> Unit = {}) {
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                DealsScreen(
                    goToWeb = { _, _ -> },
                    goToGame = {},
                    goToDiscover = goToDiscover,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun browseListRendersDealAndPeeksOnTap() {
        every { viewModel.uiState } returns MutableStateFlow(
            DealsScreenData(
                status = DealsScreenData.Status.DATA,
                deals = persistentListOf(PreviewDeal.copy(dealID = "d1", title = DEAL_TITLE, gameID = DEAL_GAME_ID)),
            )
        )

        setContent()

        composeTestRule.onNodeWithText(DEAL_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(DEAL_TITLE).performClick()

        verify(exactly = 1) { viewModel.peekGame(DEAL_GAME_ID, DEAL_TITLE, any()) }
    }

    @Test
    fun emptyBrowseStateShowsEmptyLabel() {
        every { viewModel.uiState } returns MutableStateFlow(
            DealsScreenData(status = DealsScreenData.Status.DATA, deals = persistentListOf())
        )

        setContent()

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun filterButtonOpensFilterSheet() {
        every { viewModel.uiState } returns MutableStateFlow(
            DealsScreenData(
                status = DealsScreenData.Status.DATA,
                deals = persistentListOf(PreviewDeal.copy(dealID = "d1", title = DEAL_TITLE, gameID = DEAL_GAME_ID)),
            )
        )

        setContent()

        composeTestRule.onNodeWithText(labels.filterButton).performClick()

        composeTestRule.onNodeWithText(labels.filterSheetTitle).assertIsDisplayed()
    }

    @Test
    fun discoverButtonDispatchesNavigation() {
        every { viewModel.uiState } returns MutableStateFlow(
            DealsScreenData(status = DealsScreenData.Status.DATA, deals = persistentListOf())
        )
        val goToDiscover = mockk<() -> Unit>(relaxed = true)

        setContent(goToDiscover = goToDiscover)

        composeTestRule.onNodeWithText(labels.discover).performClick()

        verify(exactly = 1) { goToDiscover() }
    }

    @Test
    fun searchResultsRenderAndPeekOnTap() {
        every { viewModel.searchQuery } returns MutableStateFlow("hades")
        every { viewModel.searchResults } returns MutableStateFlow(
            SearchResultsState.Results(
                persistentListOf(
                    GroupedSearchResult(
                        gameID = SEARCH_GAME_ID,
                        cheapestDeal = PreviewDeal.copy(dealID = "s1", title = SEARCH_TITLE, gameID = SEARCH_GAME_ID),
                        totalDealCount = 1,
                    ),
                )
            )
        )
        every { viewModel.uiState } returns MutableStateFlow(DealsScreenData(status = DealsScreenData.Status.DATA))

        setContent()

        composeTestRule.onNodeWithText(SEARCH_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(SEARCH_TITLE).performClick()

        verify(exactly = 1) { viewModel.peekGame(SEARCH_GAME_ID, SEARCH_TITLE, any()) }
    }

    @Test
    fun searchNoResultsShowsMessage() {
        every { viewModel.searchQuery } returns MutableStateFlow("zzzznotagame")
        every { viewModel.searchResults } returns MutableStateFlow<SearchResultsState>(SearchResultsState.NoResults)
        every { viewModel.uiState } returns MutableStateFlow(DealsScreenData(status = DealsScreenData.Status.DATA))

        setContent()

        composeTestRule.onNodeWithText(labels.searchNoResults).assertIsDisplayed()
    }

    @Test
    fun searchLoadingShowsSpinner() {
        every { viewModel.searchQuery } returns MutableStateFlow("hades")
        every { viewModel.searchResults } returns MutableStateFlow<SearchResultsState>(SearchResultsState.Loading)
        every { viewModel.uiState } returns MutableStateFlow(DealsScreenData(status = DealsScreenData.Status.DATA))

        setContent()

        composeTestRule.onNodeWithContentDescription(labels.searchLoading).assertIsDisplayed()
    }

    private data class Labels(
        val empty: String,
        val filterButton: String,
        val filterSheetTitle: String,
        val discover: String,
        val searchNoResults: String,
        val searchLoading: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                empty = stringResource(Res.string.deals_screen_empty_label),
                filterButton = stringResource(Res.string.deals_filter_button),
                filterSheetTitle = stringResource(Res.string.deals_filter_sheet_title),
                discover = stringResource(Res.string.deals_discover_by_tag),
                searchNoResults = stringResource(Res.string.deals_search_no_results_label),
                searchLoading = stringResource(Res.string.deals_search_loading_indicator),
            )
        }
    }

    private companion object {
        const val DEAL_TITLE = "No Man's Sky"
        const val DEAL_GAME_ID = "111"
        const val SEARCH_TITLE = "Hades"
        const val SEARCH_GAME_ID = "777"
    }
}
