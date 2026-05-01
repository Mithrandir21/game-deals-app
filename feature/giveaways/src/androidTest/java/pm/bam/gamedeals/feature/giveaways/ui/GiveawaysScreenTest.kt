package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.feature.giveaways.R

class GiveawaysScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GiveawaysViewModel = mockk()

    @Test
    fun onLoadingState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING))

        composeTestRule.setContent {
            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(LoadingDataTag)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.ERROR))
        every { viewModel.reloadGiveaways() } just Runs

        var snackText = ""
        var snackRetry = ""

        composeTestRule.setContent {
            snackText = stringResource(id = R.string.giveaway_screen_data_loading_error_msg)
            snackRetry = stringResource(id = R.string.giveaway_screen_data_loading_error_retry)

            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }


        composeTestRule.onNodeWithTag(LoadingDataTag)
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(snackText)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(snackRetry)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }


        composeTestRule.onNodeWithText(snackRetry)
            .assertIsDisplayed()
            .performClick()

        verify(exactly = 1) { viewModel.reloadGiveaways() }
    }

    @Test
    fun onResults() {
        val giveawayId = 1
        val giveaway = mockk<Giveaway> {
            every { title } returns "Title"
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

        composeTestRule.setContent {
            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(LoadingDataTag)
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag(GiveawayListItemTag.plus(giveaway.id))
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }

    @Test
    fun onShowFilters() {
        openAndTestFilters()
    }


    private fun openAndTestFilters() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING))

        composeTestRule.setContent {
            GameDealsTheme {
                GiveawaysScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(GiveawayFiltersIconTag, useUnmergedTree = true)
            .performClick()

        composeTestRule.onNodeWithTag(GiveawayFiltersTag)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.loadGiveaway(any()) }
        verify(exactly = 0) { viewModel.reloadGiveaways() }
    }
}