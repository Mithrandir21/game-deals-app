package pm.bam.gamedeals.feature.game.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.espresso.device.action.ScreenOrientation
import androidx.test.espresso.device.rules.ScreenOrientationRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.GameDetails

/**
 * Smoke coverage for the unified [GamePageScreen] (epic #291, Phase 8 — replaced GameScreenTest +
 * GameDetailsScreenTest). Renders the public screen against a mocked [GamePageViewModel].
 */
class GamePageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenOrientationRule: ScreenOrientationRule = ScreenOrientationRule(ScreenOrientation.PORTRAIT)

    private val viewModel: GamePageViewModel = mockk(relaxed = true)

    @Before
    fun setup() {
        every { viewModel.isWaitlisted } returns MutableStateFlow(false)
        every { viewModel.isIgnored } returns MutableStateFlow(false)
        every { viewModel.note } returns MutableStateFlow(null)
        every { viewModel.priceWatch } returns MutableStateFlow(null)
        every { viewModel.followedFranchiseIds } returns MutableStateFlow(emptySet())
        every { viewModel.events } returns MutableSharedFlow<GamePageViewModel.GameUiEvent>().asSharedFlow()
    }

    @Test
    fun rendersTitleInDataState() {
        val title = "Halo Infinite"
        every { viewModel.uiState } returns MutableStateFlow(
            GamePageViewModel.GamePageData.Data(
                title = title,
                gameDetails = GameDetails(
                    info = GameDetails.GameInfo(title = title, steamAppID = null, artwork = GameArtwork(banner300 = "t")),
                    cheapestPriceEver = GameDetails.GameCheapestPriceEver(priceValue = 0.0, priceDenominated = "$0", date = "2026-01-01"),
                    deals = persistentListOf(),
                ),
            )
        )

        composeTestRule.setContent {
            GameDealsTheme {
                GamePageScreen(onBack = {}, goToWeb = { _, _ -> }, viewModel = viewModel)
            }
        }

        // The title appears in the app bar and the hero; assert at least one is shown.
        composeTestRule.onAllNodesWithText(title).onFirst().assertIsDisplayed()
    }
}
