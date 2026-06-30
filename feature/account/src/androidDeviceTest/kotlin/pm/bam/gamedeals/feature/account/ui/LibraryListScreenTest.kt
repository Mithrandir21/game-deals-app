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
 * share LibraryScaffold): loading/empty/data states, the back interaction, and the row-tap that opens the
 * shared game-centric peek sheet (verified via the mocked [GamePeekDelegate]). Driven through mocked
 * [WaitlistListViewModel] / [CollectionListViewModel].
 */
class LibraryListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val waitlistViewModel: WaitlistListViewModel = mockk(relaxed = true)
    private val collectionViewModel: CollectionListViewModel = mockk(relaxed = true)
    // The screens read viewModel.peek (a concrete property) and a row tap calls peek.peek(...); a relaxed
    // mock returns relaxed StateFlows the sheet host can collect, and lets us verify the open.
    private val peek: GamePeekDelegate = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val goToGame = mockk<(String) -> Unit>(relaxed = true)
    private val goToWeb = mockk<(String) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private fun waitlistRow() = WaitlistRowUi(
        gameId = GAME_ID, title = GAME_TITLE, imageUrl = null, addedEpochMs = null,
        salePrice = null, regularPrice = null, discountPercent = 0, storeName = null, storeIconUrl = null,
        hasVoucher = false, isNewHistoricalLow = false, isStoreLow = false, isAtHistoricalLow = false,
        bestPriceValue = null,
    )

    private fun collectionRow() = CollectionRowUi(
        gameId = GAME_ID, title = GAME_TITLE, imageUrl = null, addedEpochMs = null, type = "game",
    )

    private fun setWaitlist(state: WaitlistUiState) {
        every { waitlistViewModel.peek } returns peek
        every { waitlistViewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                WaitlistListScreen(onBack = onBack, goToGame = goToGame, goToWeb = goToWeb, viewModel = waitlistViewModel)
            }
        }
    }

    private fun setCollection(state: CollectionUiState) {
        every { collectionViewModel.peek } returns peek
        every { collectionViewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                CollectionListScreen(onBack = onBack, goToGame = goToGame, goToWeb = goToWeb, viewModel = collectionViewModel)
            }
        }
    }

    @Test
    fun waitlistLoadingShowsSpinner() {
        setWaitlist(WaitlistUiState(loading = true))

        composeTestRule.onNodeWithContentDescription(labels.loading).assertIsDisplayed()
    }

    @Test
    fun waitlistEmptyShowsMessage() {
        setWaitlist(WaitlistUiState(loading = false))

        composeTestRule.onNodeWithText(labels.waitlistEmpty).assertIsDisplayed()
    }

    @Test
    fun waitlistRowTapOpensPeek() {
        setWaitlist(WaitlistUiState(loading = false, rows = persistentListOf(waitlistRow())))

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { peek.peek(GAME_ID, GAME_TITLE, null) }
    }

    @Test
    fun waitlistBackDispatchesOnBack() {
        setWaitlist(WaitlistUiState(loading = false, rows = persistentListOf(waitlistRow())))

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    @Test
    fun collectionEmptyShowsMessage() {
        setCollection(CollectionUiState(loading = false))

        composeTestRule.onNodeWithText(labels.collectionEmpty).assertIsDisplayed()
    }

    @Test
    fun collectionRowTapOpensPeek() {
        setCollection(CollectionUiState(loading = false, rows = persistentListOf(collectionRow())))

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { peek.peek(GAME_ID, GAME_TITLE, null) }
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
