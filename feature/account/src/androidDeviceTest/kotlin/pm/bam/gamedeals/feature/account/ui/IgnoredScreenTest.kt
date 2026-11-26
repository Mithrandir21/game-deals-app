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
import pm.bam.gamedeals.feature.account.generated.resources.account_ignored_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_ignored_unignore_cd
import pm.bam.gamedeals.feature.account.generated.resources.account_list_loading
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back

/**
 * Device UI coverage for [IgnoredScreen], expanded from the original a11y-only smoke test to cover the
 * loading/empty/data states and the row, un-ignore, and back interactions — driven through a mocked
 * [IgnoredViewModel].
 */
class IgnoredScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: IgnoredViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val onGameClick = mockk<(String) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private fun setContent(state: GameListState) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                IgnoredScreen(onBack = onBack, onGameClick = onGameClick, viewModel = viewModel)
            }
        }
    }

    private fun withGame() = GameListState(
        loading = false,
        items = persistentListOf(GameListItem(GAME_ID, GAME_TITLE, boxart = null)),
    )

    @Test
    fun loadingStateShowsSpinner() {
        setContent(GameListState(loading = true))

        composeTestRule.onNodeWithContentDescription(labels.loading).assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsMessage() {
        setContent(GameListState(loading = false))

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun dataStateRendersGameTitle() {
        setContent(withGame())

        composeTestRule.onNodeWithText(GAME_TITLE).assertIsDisplayed()
    }

    @Test
    fun tappingRowNavigatesToGame() {
        setContent(withGame())

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { onGameClick(GAME_ID) }
    }

    @Test
    fun unignoreButtonExposesGameSpecificContentDescription() {
        setContent(withGame())

        composeTestRule.onNodeWithContentDescription(labels.unignore).assertIsDisplayed()
    }

    @Test
    fun tappingUnignoreDispatchesToViewModel() {
        setContent(withGame())

        composeTestRule.onNodeWithContentDescription(labels.unignore).performClick()

        verify(exactly = 1) { viewModel.onUnignore(GAME_ID) }
    }

    @Test
    fun backIconDispatchesOnBack() {
        setContent(withGame())

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    private data class Labels(
        val loading: String,
        val empty: String,
        val unignore: String,
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                loading = stringResource(Res.string.account_list_loading),
                empty = stringResource(Res.string.account_ignored_empty),
                unignore = stringResource(Res.string.account_ignored_unignore_cd, GAME_TITLE),
                back = stringResource(Res.string.account_navigation_back),
            )
        }
    }

    private companion object {
        const val GAME_ID = "g1"
        const val GAME_TITLE = "Untitled Goose Game"
    }
}
