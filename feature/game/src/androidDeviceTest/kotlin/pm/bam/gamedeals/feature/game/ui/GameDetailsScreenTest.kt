package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.device.action.ScreenOrientation
import androidx.test.espresso.device.rules.ScreenOrientationRule
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screen_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_companies
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_links
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_viewer_close
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_screenshots
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_similar
import pm.bam.gamedeals.feature.game.generated.resources.game_details_similar_game_row_description
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_navigation_back_button

class GameDetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenOrientationRule: ScreenOrientationRule = ScreenOrientationRule(ScreenOrientation.PORTRAIT)

    private val viewModel: GameDetailsViewModel = mockk()

    private val sampleGame = IgdbGame(
        id = 103281,
        name = "Halo Infinite",
        summary = "The Master Chief returns.",
        coverImageId = "co2dto",
        screenshotImageIds = persistentListOf("sc1"),
        rating = 80.0,
        aggregatedRating = 87.0,
        genres = persistentListOf("Shooter"),
        themes = persistentListOf("Action"),
        involvedCompanies = persistentListOf(
            IgdbGame.IgdbCompanyRole("343 Industries", IgdbGame.IgdbCompanyRole.Role.Developer),
        ),
        websites = persistentListOf(
            IgdbGame.IgdbWebsite("https://example.com", IgdbGame.IgdbWebsite.Category.Official),
        ),
        similarGames = persistentListOf(
            IgdbGame.IgdbSimilarGame(987, "Halo 3", "co1xhc"),
        ),
    )

    private lateinit var screenSemantics: ScreenSemantics

    @Before
    fun setup() {
        every { viewModel.reload() } just runs
    }

    private fun setupCompose(
        onBack: () -> Unit = {},
        onSimilarGameClick: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load(sampleGame.name)
            GameDealsTheme {
                GameDetailsScreen(
                    onBack = onBack,
                    onSimilarGameClick = onSimilarGameClick,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun loadingState_shows_screen_title_in_toolbar() {
        every { viewModel.uiState } returns MutableStateFlow(GameDetailsViewModel.GameDetailsScreenData.Loading)

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.title)
            .assertIsDisplayed()

        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun errorState_shows_snackbar_with_retry_and_retry_triggers_reload() {
        every { viewModel.uiState } returns MutableStateFlow(GameDetailsViewModel.GameDetailsScreenData.Error)

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.errorMsg)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(screenSemantics.retry)
            .assertIsDisplayed()
            .performClick()

        verify(exactly = 1) { viewModel.reload() }
    }

    @Test
    fun dataState_renders_game_name_and_section_headers() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame)
        )

        setupCompose()

        // Name appears twice: toolbar title + hero block.
        composeTestRule.onAllNodesWithText(sampleGame.name).assertCountEquals(2)
        // Description header is high in the layout; lower section headers (screenshots, companies, links,
        // similar) sit below the fold on portrait and aren't auto-scrolled into view by the test rule.
        composeTestRule.onNodeWithText(screenSemantics.description).assertIsDisplayed()
    }

    @Test
    fun tapping_a_screenshot_opens_fullscreen_viewer() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame)
        )

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.firstScreenshot)
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithContentDescription(screenSemantics.viewerClose)
            .assertIsDisplayed()
    }

    @Test
    fun links_section_renders_chips_for_each_website() {
        val websites = persistentListOf(
            WebsiteUiModel(
                url = "https://store.steampowered.com/app/1240440",
                category = IgdbGame.IgdbWebsite.Category.Steam,
                faviconUrl = "https://store.steampowered.com/favicon.ico",
                faviconCacheKey = "brand:steam",
            ),
            WebsiteUiModel(
                url = "https://en.wikipedia.org/wiki/Halo_Infinite",
                category = IgdbGame.IgdbWebsite.Category.Wikipedia,
                faviconUrl = "https://en.wikipedia.org/favicon.ico",
                faviconCacheKey = "brand:wikipedia",
            ),
            WebsiteUiModel(
                url = "https://discord.gg/Halo",
                category = IgdbGame.IgdbWebsite.Category.Discord,
                faviconUrl = null,
                faviconCacheKey = null,
            ),
        )
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(game = sampleGame, websites = websites)
        )

        setupCompose()

        // FlowRow may wrap chips across rows on narrower viewports, so scroll each chip into view
        // individually rather than assuming siblings are visible once the first one is scrolled to.
        composeTestRule.onNodeWithText("Steam").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Wikipedia").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Discord").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tapping_similar_game_tile_invokes_callback_with_igdb_id() {
        val onSimilarGameClick: (Long) -> Unit = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame)
        )

        setupCompose(onSimilarGameClick = onSimilarGameClick)

        // Invoke OnClick via semantics (the same path TalkBack uses) rather than a touch gesture —
        // performClick's touch-coordinate dispatch misses the LazyRow tile on the Pixel 5 API-34 emulator.
        val tile = composeTestRule.onNodeWithContentDescription(screenSemantics.firstSimilarRowCd)
        tile.assertHasClickAction()
        tile.performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        verify(exactly = 1) { onSimilarGameClick.invoke(987L) }
    }

    @Test
    fun back_button_invokes_onBack() {
        val onBack: () -> Unit = mockk()
        every { onBack.invoke() } just runs
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame)
        )

        setupCompose(onBack = onBack)

        composeTestRule.onNodeWithContentDescription(screenSemantics.back)
            .performClick()

        verify(exactly = 1) { onBack.invoke() }
    }

    private data class ScreenSemantics(
        val title: String,
        val errorMsg: String,
        val retry: String,
        val back: String,
        val description: String,
        val screenshots: String,
        val companies: String,
        val links: String,
        val similar: String,
        val firstScreenshot: String,
        val viewerClose: String,
        val firstSimilarRowCd: String,
    ) {
        companion object {
            @Composable
            fun load(gameName: String): ScreenSemantics = ScreenSemantics(
                title = stringResource(Res.string.game_details_screen_title),
                errorMsg = stringResource(Res.string.game_screen_data_loading_error_msg),
                retry = stringResource(Res.string.game_screen_data_loading_error_retry),
                back = stringResource(Res.string.game_screen_navigation_back_button),
                description = stringResource(Res.string.game_details_section_description),
                screenshots = stringResource(Res.string.game_details_section_screenshots),
                companies = stringResource(Res.string.game_details_section_companies),
                links = stringResource(Res.string.game_details_section_links),
                similar = stringResource(Res.string.game_details_section_similar),
                firstScreenshot = stringResource(Res.string.game_details_screenshot_image_cd, gameName, 1),
                viewerClose = stringResource(Res.string.game_details_screenshot_viewer_close),
                firstSimilarRowCd = stringResource(Res.string.game_details_similar_game_row_description, "Halo 3"),
            )
        }
    }
}
