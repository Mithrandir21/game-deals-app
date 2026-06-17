package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_empty_live
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filter_button
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_platform_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_opens_detail
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_loading_indicator

class GiveawaysScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GiveawaysViewModel = mockk()

    private lateinit var screenSemantics: ScreenSemantics

    private fun setupCompose(
        goToWeb: (String, String) -> Unit = { _, _ -> },
        goToGiveawayDetail: (Int) -> Unit = {},
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            GameDealsTheme {
                GiveawaysScreen(
                    goToWeb = goToWeb,
                    goToGiveawayDetail = goToGiveawayDetail,
                    viewModel = viewModel,
                )
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
            )
        }
    }
}
