package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.NotificationShopDeal
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_detail_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_open_game
import pm.bam.gamedeals.feature.account.ui.NotificationDayViewModel.NotificationDayScreenData

/**
 * Device UI coverage for [NotificationDayScreen]: the empty state, a game deal card, the per-card
 * mark-viewed side effect, and the open-game + back interactions — driven through a mocked
 * [NotificationDayViewModel].
 */
class NotificationDayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: NotificationDayViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private val game = NotificationDealGame(
        gameId = GAME_ID,
        title = GAME_TITLE,
        historicalLowDenominated = "€17.99",
        deals = listOf(NotificationShopDeal("GOG", 17.09, "€17.09", "€59.99", cutPercent = 72, url = "")),
    )

    private fun setContent(data: NotificationDayScreenData) {
        every { viewModel.uiState } returns MutableStateFlow(data)
        every { viewModel.events } returns MutableSharedFlow()
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                NotificationDayScreen(onBack = onBack, onGameClick = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun emptyStateShowsMessage() {
        setContent(NotificationDayScreenData(loading = false))

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun dataStateRendersGameTitle() {
        setContent(NotificationDayScreenData(loading = false, games = persistentListOf(game)))

        composeTestRule.onNodeWithText(GAME_TITLE).assertIsDisplayed()
    }

    @Test
    fun composingCardMarksGameViewed() {
        setContent(NotificationDayScreenData(loading = false, games = persistentListOf(game)))

        // The per-game read action fires from a LaunchedEffect once the card composes.
        verify(exactly = 1) { viewModel.onGameViewed(GAME_ID) }
    }

    @Test
    fun openGameDispatchesToViewModel() {
        setContent(NotificationDayScreenData(loading = false, games = persistentListOf(game)))

        composeTestRule.onNodeWithText(labels.openGame).performClick()

        verify(exactly = 1) { viewModel.onOpenGame(GAME_ID) }
    }

    @Test
    fun backIconDispatchesOnBack() {
        setContent(NotificationDayScreenData(loading = false, games = persistentListOf(game)))

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    private data class Labels(
        val empty: String,
        val openGame: String,
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                empty = stringResource(Res.string.account_notification_detail_empty),
                openGame = stringResource(Res.string.account_notification_open_game),
                back = stringResource(Res.string.account_navigation_back),
            )
        }
    }

    private companion object {
        const val GAME_ID = "g1"
        const val GAME_TITLE = "Cyberpunk 2077"
    }
}
