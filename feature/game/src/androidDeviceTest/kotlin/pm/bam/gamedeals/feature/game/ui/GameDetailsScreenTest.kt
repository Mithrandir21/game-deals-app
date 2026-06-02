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
import io.mockk.coEvery
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
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_message
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_search_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_search_deals_cta
import pm.bam.gamedeals.feature.game.generated.resources.game_details_search_deals_cta_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_similar
import pm.bam.gamedeals.feature.game.generated.resources.game_details_similar_game_row_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_explanation
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_warning_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_view_deals_cta
import pm.bam.gamedeals.feature.game.generated.resources.game_details_view_deals_cta_cd
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
        every { viewModel.onWarningTap() } just runs
        every { viewModel.onPickerDismiss() } just runs
        every { viewModel.onCandidatePicked(any()) } just runs
    }

    private fun setupCompose(
        onBack: () -> Unit = {},
        onSimilarGameClick: (Long) -> Unit = {},
        onViewDealsClick: (String) -> Unit = {},
        onSearchDealsByTitle: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load(sampleGame.name)
            GameDealsTheme {
                GameDetailsScreen(
                    onBack = onBack,
                    onSimilarGameClick = onSimilarGameClick,
                    onViewDealsClick = onViewDealsClick,
                    onSearchDealsByTitle = onSearchDealsByTitle,
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
    fun loadingState_does_not_render_deals_cta() {
        every { viewModel.uiState } returns MutableStateFlow(GameDetailsViewModel.GameDetailsScreenData.Loading)

        setupCompose()

        // The CTA is gated on Data state — neither label should appear while loading.
        composeTestRule.onNodeWithText(screenSemantics.viewDealsLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(screenSemantics.searchDealsLabel).assertDoesNotExist()
    }

    @Test
    fun noMatchState_does_not_render_deals_cta() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.NoMatch("Some Decorated Title - Definitive Edition")
        )

        setupCompose()

        // The inline deal CTA (Data state only) carries a "<View|Search> deals for <gameName>"
        // contentDescription — these never appear in the NoMatch tree, which has its own
        // Search / Back affordances using the bare "Search deals" / "Go back" labels.
        composeTestRule.onNodeWithContentDescription(screenSemantics.viewDealsCd).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(screenSemantics.searchDealsCd).assertDoesNotExist()
    }

    @Test
    fun errorState_does_not_render_deals_cta() {
        every { viewModel.uiState } returns MutableStateFlow(GameDetailsViewModel.GameDetailsScreenData.Error)

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.viewDealsLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(screenSemantics.searchDealsLabel).assertDoesNotExist()
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
    fun dataState_with_steamAppId_shows_view_deals_label() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame.copy(steamAppId = 1240440))
        )

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.viewDealsLabel).assertIsDisplayed()
    }

    @Test
    fun dataState_without_steamAppId_shows_search_deals_label() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame.copy(steamAppId = null))
        )

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.searchDealsLabel).assertIsDisplayed()
    }

    @Test
    fun tapping_view_deals_with_steam_match_invokes_onViewDealsClick_with_cheapshark_id() {
        val onViewDealsClick: (String) -> Unit = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame.copy(steamAppId = 1240440))
        )
        coEvery { viewModel.resolveDealsAction() } returns GameDetailsViewModel.DealsAction.OpenGame("99")

        setupCompose(onViewDealsClick = onViewDealsClick)

        composeTestRule.onNodeWithContentDescription(screenSemantics.viewDealsCd)
            .performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 1) { onViewDealsClick.invoke("99") }
    }

    @Test
    fun tapping_search_deals_without_steam_invokes_onSearchDealsByTitle_with_game_name() {
        val onSearchDealsByTitle: (String) -> Unit = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(sampleGame.copy(steamAppId = null))
        )
        coEvery { viewModel.resolveDealsAction() } returns
            GameDetailsViewModel.DealsAction.SearchByTitle(sampleGame.name)

        setupCompose(onSearchDealsByTitle = onSearchDealsByTitle)

        composeTestRule.onNodeWithContentDescription(screenSemantics.searchDealsCd)
            .performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 1) { onSearchDealsByTitle.invoke(sampleGame.name) }
    }

    @Test
    fun warning_icon_absent_when_resolvedByTitle_false() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(game = sampleGame, resolvedByTitle = false)
        )

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.warningCd).assertDoesNotExist()
    }

    @Test
    fun warning_icon_visible_when_resolvedByTitle_true() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(game = sampleGame, resolvedByTitle = true)
        )

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.warningCd).assertIsDisplayed()
    }

    @Test
    fun tapping_warning_invokes_onWarningTap() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(game = sampleGame, resolvedByTitle = true)
        )

        setupCompose()

        val warning = composeTestRule.onNodeWithContentDescription(screenSemantics.warningCd)
        warning.assertHasClickAction()
        warning.performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        verify(exactly = 1) { viewModel.onWarningTap() }
    }

    @Test
    fun picker_renders_explanation_and_candidate_tiles_when_showPicker_true() {
        val candidates = persistentListOf(
            IgdbGame.IgdbSimilarGame(id = 100L, name = "Tomb Raider", coverImageId = null),
            IgdbGame.IgdbSimilarGame(id = 22L, name = "Tomb Raider II", coverImageId = null),
        )
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(
                game = sampleGame.copy(id = 100L, name = "Tomb Raider"),
                resolvedByTitle = true,
                candidatesState = GameDetailsViewModel.CandidatesState.Loaded(candidates),
                showPicker = true,
            )
        )

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.pickerTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.pickerExplanation).assertIsDisplayed()
        composeTestRule.onNodeWithText("Tomb Raider II").assertIsDisplayed()
    }

    @Test
    fun tapping_a_candidate_tile_invokes_onCandidatePicked_with_id() {
        val candidates = persistentListOf(
            IgdbGame.IgdbSimilarGame(id = 100L, name = "Tomb Raider", coverImageId = null),
            IgdbGame.IgdbSimilarGame(id = 22L, name = "Tomb Raider II", coverImageId = null),
        )
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.Data(
                game = sampleGame.copy(id = 100L, name = "Tomb Raider"),
                resolvedByTitle = true,
                candidatesState = GameDetailsViewModel.CandidatesState.Loaded(candidates),
                showPicker = true,
            )
        )

        setupCompose()

        val tileCd = stringResource_view_alternative("Tomb Raider II")
        val tile = composeTestRule.onNodeWithContentDescription(tileCd)
        tile.assertHasClickAction()
        tile.performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        verify(exactly = 1) { viewModel.onCandidatePicked(22L) }
    }

    private fun stringResource_view_alternative(name: String): String = "View details for $name"

    @Test
    fun noMatchState_renders_explainer_card_with_title_and_both_action_buttons() {
        val decoratedTitle = "Suicide Squad: Kill the Justice League - Digital Deluxe Edition"
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.NoMatch(decoratedTitle)
        )

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.noMatchTitle).assertIsDisplayed()
        // Message interpolates the full decorated title — assert the title fragment appears.
        composeTestRule.onNodeWithText(decoratedTitle, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.noMatchSearchButton).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.noMatchBackButton).assertIsDisplayed()
    }

    @Test
    fun tapping_search_button_in_no_match_invokes_onSearchDealsByTitle_with_title() {
        val decoratedTitle = "Mystery Game - Definitive Edition"
        val onSearchDealsByTitle: (String) -> Unit = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.NoMatch(decoratedTitle)
        )

        setupCompose(onSearchDealsByTitle = onSearchDealsByTitle)

        composeTestRule.onNodeWithText(screenSemantics.noMatchSearchButton).performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 1) { onSearchDealsByTitle.invoke(decoratedTitle) }
    }

    @Test
    fun tapping_back_button_in_no_match_invokes_onBack() {
        val onBack: () -> Unit = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            GameDetailsViewModel.GameDetailsScreenData.NoMatch("Anything")
        )

        setupCompose(onBack = onBack)

        composeTestRule.onNodeWithText(screenSemantics.noMatchBackButton).performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 1) { onBack.invoke() }
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
        val viewDealsLabel: String,
        val searchDealsLabel: String,
        val viewDealsCd: String,
        val searchDealsCd: String,
        val warningCd: String,
        val pickerTitle: String,
        val pickerExplanation: String,
        val noMatchTitle: String,
        val noMatchSearchButton: String,
        val noMatchBackButton: String,
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
                viewDealsLabel = stringResource(Res.string.game_details_view_deals_cta),
                searchDealsLabel = stringResource(Res.string.game_details_search_deals_cta),
                viewDealsCd = stringResource(Res.string.game_details_view_deals_cta_cd, gameName),
                searchDealsCd = stringResource(Res.string.game_details_search_deals_cta_cd, gameName),
                warningCd = stringResource(Res.string.game_details_title_match_warning_cd),
                pickerTitle = stringResource(Res.string.game_details_title_match_picker_title),
                pickerExplanation = stringResource(Res.string.game_details_title_match_picker_explanation),
                noMatchTitle = stringResource(Res.string.game_details_no_match_title),
                noMatchSearchButton = stringResource(Res.string.game_details_no_match_search_button),
                noMatchBackButton = stringResource(Res.string.game_details_no_match_back_button),
            )
        }
    }
}
