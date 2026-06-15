package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_error_msg
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_go_to_giveaway
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_loading_indicator
import pm.bam.gamedeals.testing.fixtures.giveaway

class GiveawayDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GiveawayDetailViewModel = mockk(relaxed = true)

    private lateinit var semantics: Semantics

    private fun setupCompose(goToWeb: (String, String) -> Unit = { _, _ -> }) {
        composeTestRule.setContent {
            semantics = Semantics.load()
            GameDealsTheme {
                GiveawayDetailScreen(
                    onBack = {},
                    goToWeb = goToWeb,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun loadingState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawayDetailViewModel.GiveawayDetailScreenData.Loading)

        setupCompose()

        composeTestRule.onNodeWithContentDescription(semantics.loading).assertIsDisplayed()
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(GiveawayDetailViewModel.GiveawayDetailScreenData.Error)

        setupCompose()

        composeTestRule.onNodeWithText(semantics.errorMsg).assertIsDisplayed()
    }

    @Test
    fun dataState_goToGiveaway_opens_the_claim_url() {
        val giveaway = giveaway(id = 5, title = "Tell Me Why", openGiveawayUrl = "https://claim", endDate = null)
        every { viewModel.uiState } returns MutableStateFlow(
            GiveawayDetailViewModel.GiveawayDetailScreenData.Data(giveaway = giveaway, endDateMillis = null)
        )
        val goToWeb = mockk<(String, String) -> Unit>(relaxed = true)

        setupCompose(goToWeb = goToWeb)

        composeTestRule.onNodeWithText("Tell Me Why", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(semantics.goToGiveaway).performClick()

        verify { goToWeb("https://claim", "Tell Me Why") }
    }

    private data class Semantics(
        val loading: String,
        val errorMsg: String,
        val goToGiveaway: String,
    ) {
        companion object {
            @Composable
            fun load(): Semantics = Semantics(
                loading = stringResource(Res.string.giveaway_detail_loading_indicator),
                errorMsg = stringResource(Res.string.giveaway_detail_error_msg),
                goToGiveaway = stringResource(Res.string.giveaway_detail_go_to_giveaway),
            )
        }
    }
}
