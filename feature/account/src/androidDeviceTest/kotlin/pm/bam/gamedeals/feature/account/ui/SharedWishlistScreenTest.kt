package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_also_on_list
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_both_want
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_both_want_none
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_failed
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_itad_busy
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_on_list
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_retry
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_sign_in_title

/** Device UI coverage for a friend's shared wishlist, driven through a mocked [SharedWishlistViewModel]. */
class SharedWishlistScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: SharedWishlistViewModel = mockk(relaxed = true)
    private val peek: GamePeekDelegate = mockk(relaxed = true)

    private lateinit var labels: Labels

    @Before
    fun setup() {
        every { peek.data } returns MutableStateFlow<GamePeekSheetData?>(null)
        every { peek.waitlistIds } returns MutableStateFlow(persistentSetOf())
        every { peek.collectionIds } returns MutableStateFlow(persistentSetOf())
        every { peek.ignoredIds } returns MutableStateFlow(persistentSetOf())
        every { peek.events } returns MutableSharedFlow<GamePeekEvent>().asSharedFlow()
        every { viewModel.peek } returns peek
    }

    private fun row(id: String, title: String, waitlisted: Boolean) = SharedWishlistRowUi(
        gameId = id, title = title, imageUrl = null, salePrice = "$4.99", regularPrice = "$19.99", discountPercent = 75,
        storeName = "Steam", storeIconUrl = null, hasVoucher = false, isNewHistoricalLow = false, isStoreLow = false,
        isLowestEver = false, isWaitlisted = waitlisted, bestPriceValue = 4.99,
    )

    private fun show(state: SharedWishlistUiState) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                SharedWishlistScreen(onBack = {}, goToGame = {}, goToWeb = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun signedOutOffersSignInAboveTheirList() {
        show(SharedWishlistUiState(loading = false, signedIn = false, others = persistentListOf(row("2", "Celeste", false))))

        composeTestRule.onNodeWithText(labels.signInTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.onList).assertIsDisplayed()
        composeTestRule.onNodeWithText("Celeste").assertIsDisplayed()
    }

    @Test
    fun signedInShowsTheGamesYouBothWantFirst() {
        show(
            SharedWishlistUiState(
                loading = false,
                signedIn = true,
                bothWant = persistentListOf(row("1", "Hades", true)),
                others = persistentListOf(row("2", "Celeste", false)),
            ),
        )

        composeTestRule.onNodeWithText(labels.bothWant).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.alsoOnList).assertIsDisplayed()
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
    }

    @Test
    fun signedInWithNoOverlapSaysSo() {
        show(SharedWishlistUiState(loading = false, signedIn = true, others = persistentListOf(row("2", "Celeste", false))))

        composeTestRule.onNodeWithText(labels.bothWantNone).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.onList).assertIsDisplayed()
    }

    @Test
    fun gamesThatCouldNotLoadAreMentionedAndOtherwiseNot() {
        show(SharedWishlistUiState(loading = false, others = persistentListOf(row("2", "Celeste", false)), unavailableCount = 2))

        composeTestRule.onNodeWithText("Some games on this list couldn't be loaded right now.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Celeste").assertIsDisplayed()
    }

    @Test
    fun aListWithEverythingLoadedDoesNotMentionMissingGames() {
        show(SharedWishlistUiState(loading = false, others = persistentListOf(row("2", "Celeste", false))))

        composeTestRule.onNodeWithText("Some games on this list couldn't be loaded right now.").assertDoesNotExist()
    }

    @Test
    fun aRowTapOpensThePeekSheet() {
        show(SharedWishlistUiState(loading = false, others = persistentListOf(row("2", "Celeste", false))))

        composeTestRule.onNodeWithText("Celeste").performClick()

        verify(exactly = 1) { peek.peek("2", "Celeste", null) }
    }

    @Test
    fun aFailedLoadOffersRetry() {
        show(SharedWishlistUiState(loading = false, failed = true))

        composeTestRule.onNodeWithText(labels.failed).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { viewModel.retry() }
    }

    @Test
    fun whenItadIsBusyTheMessageSaysSoInsteadOfBlamingTheConnection() {
        show(SharedWishlistUiState(loading = false, failed = true, itadBusy = true))

        composeTestRule.onNodeWithText(labels.itadBusy).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.failed).assertDoesNotExist()
        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { viewModel.retry() }
    }

    private data class Labels(
        val signInTitle: String,
        val bothWant: String,
        val bothWantNone: String,
        val alsoOnList: String,
        val onList: String,
        val failed: String,
        val itadBusy: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                signInTitle = stringResource(Res.string.shared_wishlist_sign_in_title),
                bothWant = stringResource(Res.string.shared_wishlist_both_want),
                bothWantNone = stringResource(Res.string.shared_wishlist_both_want_none),
                alsoOnList = stringResource(Res.string.shared_wishlist_also_on_list),
                onList = stringResource(Res.string.shared_wishlist_on_list),
                failed = stringResource(Res.string.shared_wishlist_failed),
                itadBusy = stringResource(Res.string.shared_wishlist_itad_busy),
                retry = stringResource(Res.string.shared_wishlist_retry),
            )
        }
    }
}
