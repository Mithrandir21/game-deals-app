package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_go_to_giveaway
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_instructions_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_empty_live
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filter_button
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_platform_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_opens_detail
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_loading_indicator
import pm.bam.gamedeals.testing.fixtures.giveaway

class GiveawaysScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GiveawaysViewModel = mockk()

    private lateinit var screenSemantics: ScreenSemantics

    private fun setupCompose(
        goToWeb: (String, String) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            // The peek sheet resolves Analytics via koinInject, so provide an isolated Koin for it.
            KoinApplication(application = { modules(module { single<Analytics> { mockk(relaxed = true) } }) }) {
                GameDealsTheme {
                    GiveawaysScreen(
                        goToWeb = goToWeb,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    @Test
    fun onLoadingState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING))

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.ERROR))
        every { viewModel.reloadGiveaways() } just Runs

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertDoesNotExist()
        composeTestRule.onNodeWithText(screenSemantics.errorMsg).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.retry).assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }

        composeTestRule.onNodeWithText(screenSemantics.retry).performClick()

        verify(exactly = 1) { viewModel.reloadGiveaways() }
    }

    @Test
    fun onResults() {
        val giveawayId = 1
        val giveawayTitle = "Title"
        val giveaway = mockk<Giveaway> {
            every { title } returns giveawayTitle
            every { worthDenominated } returns "Worth"
            every { image } returns "Image"
            every { platforms } returns persistentListOf(GiveawayPlatform.PC)
            every { id } returns giveawayId
        }
        every { viewModel.uiState } returns MutableStateFlow(
            GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = persistentListOf(giveaway)
            )
        )

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertDoesNotExist()
        composeTestRule.onNodeWithText(giveawayTitle, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(screenSemantics.opensDetail, substring = true).assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }

    @Test
    fun emptyShowsPlaceholder() {
        every { viewModel.uiState } returns MutableStateFlow(
            GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = persistentListOf(),
            )
        )

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertDoesNotExist()
        composeTestRule.onNodeWithText(screenSemantics.emptyLive).assertIsDisplayed()
    }

    @Test
    fun emptyPlaceholderIsPoliteLiveRegion() {
        every { viewModel.uiState } returns MutableStateFlow(
            GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = persistentListOf(),
            )
        )

        setupCompose()

        // The empty message must announce itself when it replaces the loading spinner.
        composeTestRule.onNodeWithText(screenSemantics.emptyLive)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun tappingCard_opensPeekSheet_andClaimOpensUrl() {
        val item = giveaway(
            id = 5,
            title = "Tell Me Why",
            openGiveawayUrl = "https://claim",
            endDate = null,
            description = "A great narrative game",
            instructions = "1. Sign in to Steam",
        )
        every { viewModel.uiState } returns MutableStateFlow(
            GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = persistentListOf(item),
            )
        )
        val goToWeb = mockk<(String, String) -> Unit>(relaxed = true)

        setupCompose(goToWeb = goToWeb)

        // The sheet's net-new content isn't shown until the card is tapped.
        composeTestRule.onNodeWithText(screenSemantics.howToClaim).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(screenSemantics.opensDetail, substring = true).performClick()

        // The peek sheet surfaces the description/instructions the card doesn't show.
        composeTestRule.onNodeWithText(screenSemantics.howToClaim).assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Sign in to Steam", substring = true).assertIsDisplayed()

        // Claiming from the sheet (the last of the two identically-labelled buttons) opens the claim URL.
        composeTestRule.onAllNodesWithText(screenSemantics.goToGiveaway).onLast().performClick()

        verify { goToWeb("https://claim", "Tell Me Why") }
    }

    @Test
    fun onShowFilters() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING))

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.filterButton)
            .performClick()

        composeTestRule.onNodeWithText(screenSemantics.platformLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }

    private data class ScreenSemantics(
        val loading: String,
        val errorMsg: String,
        val retry: String,
        val filterButton: String,
        val platformLabel: String,
        val opensDetail: String,
        val emptyLive: String,
        val howToClaim: String,
        val goToGiveaway: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                loading = stringResource(Res.string.giveaway_screen_loading_indicator),
                errorMsg = stringResource(Res.string.giveaway_screen_data_loading_error_msg),
                retry = stringResource(Res.string.giveaway_screen_data_loading_error_retry),
                filterButton = stringResource(Res.string.giveaway_screen_filter_button),
                platformLabel = stringResource(Res.string.giveaway_screen_filters_platform_label),
                opensDetail = stringResource(Res.string.giveaway_screen_list_item_opens_detail),
                emptyLive = stringResource(Res.string.giveaway_screen_empty_live),
                howToClaim = stringResource(Res.string.giveaway_detail_instructions_heading),
                goToGiveaway = stringResource(Res.string.giveaway_detail_go_to_giveaway),
            )
        }
    }
}
