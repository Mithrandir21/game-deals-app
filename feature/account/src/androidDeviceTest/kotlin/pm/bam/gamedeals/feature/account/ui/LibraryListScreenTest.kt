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
import pm.bam.gamedeals.feature.account.generated.resources.account_collection_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_list_loading
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_empty

/**
 * Device UI coverage for the library list screens ([WaitlistListScreen] + [CollectionListScreen], which
 * share GameListScaffold): loading/empty/data states and the row-tap + back interactions — driven
 * through mocked [WaitlistListViewModel] / [CollectionListViewModel].
 */
class LibraryListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val waitlistViewModel: WaitlistListViewModel = mockk(relaxed = true)
    private val collectionViewModel: CollectionListViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val onGameClick = mockk<(String) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private fun withGame() = GameListState(
        loading = false,
        items = persistentListOf(GameListItem(GAME_ID, GAME_TITLE, boxart = null)),
    )

    private fun setWaitlist(state: GameListState) {
        every { waitlistViewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                WaitlistListScreen(onBack = onBack, onGameClick = onGameClick, viewModel = waitlistViewModel)
            }
        }
    }

    private fun setCollection(state: GameListState) {
        every { collectionViewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                CollectionListScreen(onBack = onBack, onGameClick = onGameClick, viewModel = collectionViewModel)
            }
        }
    }

    @Test
    fun waitlistLoadingShowsSpinner() {
        setWaitlist(GameListState(loading = true))

        composeTestRule.onNodeWithContentDescription(labels.loading).assertIsDisplayed()
    }

    @Test
    fun waitlistEmptyShowsMessage() {
        setWaitlist(GameListState(loading = false))

        composeTestRule.onNodeWithText(labels.waitlistEmpty).assertIsDisplayed()
    }

    @Test
    fun waitlistRowTapNavigatesToGame() {
        setWaitlist(withGame())

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { onGameClick(GAME_ID) }
    }

    @Test
    fun waitlistBackDispatchesOnBack() {
        setWaitlist(withGame())

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    @Test
    fun collectionEmptyShowsMessage() {
        setCollection(GameListState(loading = false))

        composeTestRule.onNodeWithText(labels.collectionEmpty).assertIsDisplayed()
    }

    @Test
    fun collectionRowTapNavigatesToGame() {
        setCollection(withGame())

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { onGameClick(GAME_ID) }
    }

    private data class Labels(
        val loading: String,
        val waitlistEmpty: String,
        val collectionEmpty: String,
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                loading = stringResource(Res.string.account_list_loading),
                waitlistEmpty = stringResource(Res.string.account_waitlist_empty),
                collectionEmpty = stringResource(Res.string.account_collection_empty),
                back = stringResource(Res.string.account_navigation_back),
            )
        }
    }

    private companion object {
        const val GAME_ID = "g1"
        const val GAME_TITLE = "Hades"
    }
}
