package pm.bam.gamedeals.feature.giveaways.ui

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
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_icon
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_platform_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_loading_indicator

class GiveawaysScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GiveawaysViewModel = mockk()

    @Test
    fun onLoadingState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING))

        var loadingCd = ""

        composeTestRule.setContent {
            loadingCd = stringResource(Res.string.giveaway_screen_loading_indicator)

            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingCd)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.ERROR))
        every { viewModel.reloadGiveaways() } just Runs

        var loadingCd = ""
        var snackText = ""
        var snackRetry = ""

        composeTestRule.setContent {
            loadingCd = stringResource(Res.string.giveaway_screen_loading_indicator)
            snackText = stringResource(Res.string.giveaway_screen_data_loading_error_msg)
            snackRetry = stringResource(Res.string.giveaway_screen_data_loading_error_retry)

            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingCd).assertDoesNotExist()
        composeTestRule.onNodeWithText(snackText).assertIsDisplayed()
        composeTestRule.onNodeWithText(snackRetry).assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }

        composeTestRule.onNodeWithText(snackRetry).performClick()

        verify(exactly = 1) { viewModel.reloadGiveaways() }
    }

    @Test
    fun onResults() {
        val giveawayId = 1
        val giveawayTitle = "Title"
        val giveaway = mockk<Giveaway> {
            every { title } returns giveawayTitle
            every { worthDenominated } returns "Worth"
            every { thumbnail } returns "Thumbnail"
            every { id } returns giveawayId
        }
        every { viewModel.uiState } returns MutableStateFlow(
            GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = persistentListOf(giveaway)
            )
        )

        var loadingCd = ""

        composeTestRule.setContent {
            loadingCd = stringResource(Res.string.giveaway_screen_loading_indicator)

            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingCd).assertDoesNotExist()
        composeTestRule.onNodeWithText(giveawayTitle).assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }

    @Test
    fun onShowFilters() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING))

        var filtersIconCd = ""
        var platformLabel = ""

        composeTestRule.setContent {
            filtersIconCd = stringResource(Res.string.giveaway_screen_filters_icon)
            platformLabel = stringResource(Res.string.giveaway_screen_filters_platform_label)

            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(filtersIconCd, useUnmergedTree = true)
            .performClick()

        // Assert the sheet is visible via a known child label rather than a wrapper CD —
        // wrapper CDs mask their descendants in Compose's merged semantics tree.
        composeTestRule.onNodeWithText(platformLabel)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }
}
