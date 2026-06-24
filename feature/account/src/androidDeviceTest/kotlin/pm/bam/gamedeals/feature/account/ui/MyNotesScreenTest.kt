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
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notes_empty

/**
 * Device UI coverage for [MyNotesScreen]: the empty state, a note row (title + note text), and the
 * row-tap + back interactions — driven through a mocked [MyNotesViewModel].
 */
class MyNotesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: MyNotesViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val onGameClick = mockk<(String) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private fun setContent(state: NotesListState) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                MyNotesScreen(onBack = onBack, onGameClick = onGameClick, viewModel = viewModel)
            }
        }
    }

    private fun withNote() = NotesListState(
        loading = false,
        items = persistentListOf(NotesListItem(GAME_ID, GAME_TITLE, boxart = null, note = NOTE_TEXT)),
    )

    @Test
    fun emptyStateShowsMessage() {
        setContent(NotesListState(loading = false))

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun dataStateRendersTitleAndNote() {
        setContent(withNote())

        composeTestRule.onNodeWithText(GAME_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(NOTE_TEXT).assertIsDisplayed()
    }

    @Test
    fun tappingRowNavigatesToGame() {
        setContent(withNote())

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { onGameClick(GAME_ID) }
    }

    @Test
    fun backIconDispatchesOnBack() {
        setContent(withNote())

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    private data class Labels(
        val empty: String,
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                empty = stringResource(Res.string.account_notes_empty),
                back = stringResource(Res.string.account_navigation_back),
            )
        }
    }

    private companion object {
        const val GAME_ID = "g1"
        const val GAME_TITLE = "Elden Ring"
        const val NOTE_TEXT = "Wait for a deeper sale."
    }
}
