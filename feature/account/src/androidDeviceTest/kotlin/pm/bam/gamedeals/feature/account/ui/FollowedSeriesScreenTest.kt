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
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_unfollow
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back

/**
 * Device UI coverage for [FollowedSeriesScreen]: the empty state, a followed-series card (name + game
 * tile), and the unfollow / game-tap / back interactions — driven through a mocked [FollowedSeriesViewModel].
 */
class FollowedSeriesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: FollowedSeriesViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val onGameClick = mockk<(Long) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private fun setContent(state: FollowedSeriesState) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                FollowedSeriesScreen(onBack = onBack, onGameClick = onGameClick, viewModel = viewModel)
            }
        }
    }

    private fun withSeries() = FollowedSeriesState(
        items = persistentListOf(
            FollowedSeriesItem(
                franchiseId = FRANCHISE_ID,
                name = SERIES_NAME,
                games = persistentListOf(FollowedSeriesGame(GAME_ID, GAME_TITLE, null)),
            ),
        ),
    )

    @Test
    fun emptyStateShowsMessage() {
        setContent(FollowedSeriesState())

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun dataStateRendersSeriesName() {
        setContent(withSeries())

        composeTestRule.onNodeWithText(SERIES_NAME).assertIsDisplayed()
    }

    @Test
    fun tappingGameTileNavigates() {
        setContent(withSeries())

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { onGameClick(GAME_ID) }
    }

    @Test
    fun unfollowDispatchesToViewModel() {
        setContent(withSeries())

        composeTestRule.onNodeWithContentDescription(labels.unfollow).performClick()

        verify(exactly = 1) { viewModel.unfollow(FRANCHISE_ID) }
    }

    @Test
    fun backIconDispatchesOnBack() {
        setContent(withSeries())

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    private data class Labels(
        val empty: String,
        val unfollow: String,
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                empty = stringResource(Res.string.account_followed_series_empty),
                unfollow = stringResource(Res.string.account_followed_series_unfollow, SERIES_NAME),
                back = stringResource(Res.string.account_navigation_back),
            )
        }
    }

    private companion object {
        const val FRANCHISE_ID = 1L
        const val SERIES_NAME = "Halo"
        const val GAME_ID = 10L
        const val GAME_TITLE = "Halo: Combat Evolved"
    }
}
