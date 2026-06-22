package pm.bam.gamedeals.feature.account.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_ignored_unignore_cd

/**
 * Locks the accessibility fix where the "Un-ignore" button announced only "Un-ignore" with no game
 * context. The button must expose a target-specific contentDescription ("Un-ignore <title>") so a
 * TalkBack user navigating button-by-button knows which game they're acting on.
 */
class IgnoredScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: IgnoredViewModel = mockk(relaxed = true)

    private val title = "Untitled Goose Game"
    private lateinit var unignoreCd: String

    @Test
    fun unignoreButtonExposesGameSpecificContentDescription() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameListState(loading = false, items = persistentListOf(GameListItem("g1", title, boxart = null))),
        )
        composeTestRule.setContent {
            unignoreCd = stringResource(Res.string.account_ignored_unignore_cd, title)
            GameDealsTheme {
                IgnoredScreen(onBack = {}, onGameClick = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithContentDescription(unignoreCd).assertIsDisplayed()
    }
}
