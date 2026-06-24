package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.device.action.ScreenOrientation
import androidx.test.espresso.device.rules.ScreenOrientationRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_message
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_search_button
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_collection_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_favourite_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_ignore_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_loading_indicator
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_more_actions
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_navigation_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_error
import pm.bam.gamedeals.feature.game.generated.resources.game_page_stats_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_tab_stats
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.GamePageData

/**
 * Device UI coverage for the unified [GamePageScreen] (epic #291), expanded from the original title-only
 * smoke test to also cover the loading/error/no-match states and the toolbar actions (back, waitlist,
 * collection, ignore) — each asserted against a mocked [GamePageViewModel].
 */
class GamePageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenOrientationRule: ScreenOrientationRule = ScreenOrientationRule(ScreenOrientation.PORTRAIT)

    private val viewModel: GamePageViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val onSearchByTitle = mockk<(String) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    @Before
    fun setup() {
        every { viewModel.isWaitlisted } returns MutableStateFlow(false)
        every { viewModel.isCollected } returns MutableStateFlow(false)
        every { viewModel.isIgnored } returns MutableStateFlow(false)
        every { viewModel.note } returns MutableStateFlow(null)
        every { viewModel.followedFranchiseIds } returns MutableStateFlow(emptySet())
        every { viewModel.events } returns MutableSharedFlow<GamePageViewModel.GameUiEvent>().asSharedFlow()
    }

    private fun dataState(title: String = TITLE) = GamePageData.Data(
        title = title,
        deals = SectionState.Loaded(
            GameDetails(
                info = GameDetails.GameInfo(title = title, steamAppID = null, artwork = GameArtwork(banner300 = "t")),
                cheapestPriceEver = GameDetails.GameCheapestPriceEver(priceValue = 0.0, priceDenominated = "$0", date = "2026-01-01"),
                deals = persistentListOf(),
            )
        ),
    )

    private fun setContent(state: GamePageData) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                GamePageScreen(
                    onBack = onBack,
                    goToWeb = { _, _ -> },
                    onSearchDealsByTitle = onSearchByTitle,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun rendersTitleInDataState() {
        setContent(dataState())

        // The title appears in the app bar and the hero; assert at least one is shown.
        composeTestRule.onAllNodesWithText(TITLE).onFirst().assertIsDisplayed()
    }

    @Test
    fun loadingStateShowsSpinner() {
        setContent(GamePageData.Loading)

        composeTestRule.onNodeWithContentDescription(labels.loading).assertIsDisplayed()
    }

    @Test
    fun errorStateShowsSnackbarMessage() {
        setContent(GamePageData.Error)

        composeTestRule.onNodeWithText(labels.errorMsg).assertIsDisplayed()
    }

    @Test
    fun backIconDispatchesOnBack() {
        setContent(dataState())

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    @Test
    fun waitlistActionDispatchesToggle() {
        setContent(dataState())

        composeTestRule.onNodeWithContentDescription(labels.waitlistAdd).performClick()

        verify(exactly = 1) { viewModel.toggleWaitlist() }
    }

    @Test
    fun collectionActionDispatchesToggle() {
        setContent(dataState())

        composeTestRule.onNodeWithContentDescription(labels.collectionAdd).performClick()

        verify(exactly = 1) { viewModel.toggleCollection() }
    }

    @Test
    fun overflowIgnoreDispatchesToggle() {
        setContent(dataState())

        composeTestRule.onNodeWithContentDescription(labels.moreActions).performClick()
        composeTestRule.onNodeWithText(labels.ignoreAdd).performClick()

        verify(exactly = 1) { viewModel.toggleIgnore() }
    }

    @Test
    fun noMatchStateShowsMessageAndSearches() {
        setContent(GamePageData.NoMatch(TITLE))

        composeTestRule.onNodeWithText(labels.searchButton).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.searchButton).performClick()

        verify(exactly = 1) { onSearchByTitle(TITLE) }
    }

    @Test
    fun statsTabShowsEmptyMessageWhenNoMeta() {
        // Default dataState has no game meta (gameMeta = Loaded(null)) → Stats tab is genuinely empty.
        setContent(dataState())

        composeTestRule.onNodeWithText(labels.statsTab).performClick()

        composeTestRule.onNodeWithText(labels.statsEmpty).assertIsDisplayed()
    }

    @Test
    fun statsTabErrorShowsRetryAndDispatches() {
        setContent(dataState().copy(gameMeta = SectionState.Error))

        composeTestRule.onNodeWithText(labels.statsTab).performClick()

        composeTestRule.onNodeWithText(labels.sectionError).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { viewModel.retryGameMeta() }
    }

    private data class Labels(
        val loading: String,
        val errorMsg: String,
        val back: String,
        val waitlistAdd: String,
        val collectionAdd: String,
        val moreActions: String,
        val ignoreAdd: String,
        val searchButton: String,
        val noMatchMessage: String,
        val statsTab: String,
        val statsEmpty: String,
        val sectionError: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                loading = stringResource(Res.string.game_screen_loading_indicator),
                errorMsg = stringResource(Res.string.game_screen_data_loading_error_msg),
                back = stringResource(Res.string.game_screen_navigation_back_button),
                waitlistAdd = stringResource(Res.string.game_screen_favourite_add_action),
                collectionAdd = stringResource(Res.string.game_screen_collection_add_action),
                moreActions = stringResource(Res.string.game_screen_more_actions),
                ignoreAdd = stringResource(Res.string.game_screen_ignore_add_action),
                searchButton = stringResource(Res.string.game_details_no_match_search_button),
                noMatchMessage = stringResource(Res.string.game_details_no_match_message, TITLE),
                statsTab = stringResource(Res.string.game_page_tab_stats),
                statsEmpty = stringResource(Res.string.game_page_stats_empty),
                sectionError = stringResource(Res.string.game_page_section_error),
                retry = stringResource(Res.string.game_screen_data_loading_error_retry),
            )
        }
    }

    private companion object {
        const val TITLE = "Halo Infinite"
    }
}
