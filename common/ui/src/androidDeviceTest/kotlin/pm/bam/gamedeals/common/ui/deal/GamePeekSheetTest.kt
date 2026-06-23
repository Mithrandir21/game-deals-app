package pm.bam.gamedeals.common.ui.deal

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_collection_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_msg
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_retry
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_go_to_deal_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_loading_indicator
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_view_game_page_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_ignore_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_best_price_at_store
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_more_actions
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_no_deals_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_other_stores_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_store_row_description

/**
 * Device UI test for the shared [GamePeekSheet] — the quick-peek surface opened from every deal/game
 * row across Home, Deals, Store, Discover and Bundle Detail. It takes hoisted state + lambda
 * callbacks, so it's driven directly with fixture [GamePeekSheetData] (no ViewModel/Koin) and
 * asserts both rendered state and that each action dispatches its callback. Matches by visible text /
 * accessibility semantics, consistent with the rest of the suite (no testTags).
 */
class GamePeekSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val onDismiss = mockk<() -> Unit>(relaxed = true)
    private val onShare = mockk<(GamePeekSheetData.Data) -> Unit>(relaxed = true)
    private val onToggleWaitlist = mockk<(String) -> Unit>(relaxed = true)
    private val onToggleCollection = mockk<(String) -> Unit>(relaxed = true)
    private val onToggleIgnore = mockk<(String) -> Unit>(relaxed = true)
    private val goToWeb = mockk<(String, String) -> Unit>(relaxed = true)
    private val onViewGamePage = mockk<(GamePeekSheetData.Data) -> Unit>(relaxed = true)
    private val onRetry = mockk<() -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private val dataState = GamePeekSheetData.Data(
        gameId = GAME_ID,
        gameName = GAME_NAME,
        thumb = null,
        bestDeal = StoreDealPair(store = PreviewStore, deal = bestDeal()),
        otherStores = persistentListOf(
            StoreDealPair(store = PreviewStore.copy(storeID = 7, storeName = OTHER_STORE), deal = otherDeal()),
        ),
        cheapestPriceEver = GameDetails.GameCheapestPriceEver(priceValue = 14.99, priceDenominated = "$14.99", date = ""),
        upcoming = false,
    )

    private fun setContent(data: GamePeekSheetData) {
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                GamePeekSheet(
                    data = data,
                    isWaitlisted = false,
                    isCollected = false,
                    isIgnored = false,
                    onDismiss = onDismiss,
                    onShare = onShare,
                    onToggleWaitlist = onToggleWaitlist,
                    onToggleCollection = onToggleCollection,
                    onToggleIgnore = onToggleIgnore,
                    goToWeb = goToWeb,
                    onViewGamePage = onViewGamePage,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun dataStateRendersGameBestPriceAndOtherStores() {
        setContent(dataState)

        composeTestRule.onNodeWithText(GAME_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.bestPriceSubtitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.otherStoresHeader).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(labels.otherStoreRow).assertIsDisplayed()
    }

    @Test
    fun tappingWaitlistDispatchesToggleWithGameId() {
        setContent(dataState)

        composeTestRule.onNodeWithContentDescription(labels.waitlistAdd).performClick()

        verify(exactly = 1) { onToggleWaitlist(GAME_ID) }
    }

    @Test
    fun tappingCollectionDispatchesToggleWithGameId() {
        setContent(dataState)

        composeTestRule.onNodeWithContentDescription(labels.collectionAdd).performClick()

        verify(exactly = 1) { onToggleCollection(GAME_ID) }
    }

    @Test
    fun overflowShareDispatchesShare() {
        setContent(dataState)

        composeTestRule.onNodeWithContentDescription(labels.moreActions).performClick()
        composeTestRule.onNodeWithText(labels.share).performClick()

        verify(exactly = 1) { onShare(dataState) }
    }

    @Test
    fun overflowIgnoreDispatchesToggleIgnore() {
        setContent(dataState)

        composeTestRule.onNodeWithContentDescription(labels.moreActions).performClick()
        composeTestRule.onNodeWithText(labels.ignoreAdd).performClick()

        verify(exactly = 1) { onToggleIgnore(GAME_ID) }
    }

    @Test
    fun tappingViewGamePageDispatchesNavigation() {
        setContent(dataState)

        composeTestRule.onNodeWithText(labels.viewGamePage).performClick()

        verify(exactly = 1) { onViewGamePage(dataState) }
    }

    @Test
    fun tappingGoToDealOpensBestDealUrl() {
        setContent(dataState)

        composeTestRule.onNodeWithText(labels.goToDeal).performClick()

        verify(exactly = 1) { goToWeb(BEST_URL, GAME_NAME) }
    }

    @Test
    fun tappingOtherStoreRowOpensThatStoreUrl() {
        setContent(dataState)

        composeTestRule.onNodeWithContentDescription(labels.otherStoreRow).performClick()

        verify(exactly = 1) { goToWeb(OTHER_URL, GAME_NAME) }
    }

    @Test
    fun upcomingStateShowsNoDealsAndHidesGoToDeal() {
        setContent(
            GamePeekSheetData.Data(
                gameId = GAME_ID,
                gameName = GAME_NAME,
                thumb = null,
                bestDeal = null,
                upcoming = true,
            ),
        )

        composeTestRule.onNodeWithText(labels.noDeals).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.goToDeal).assertDoesNotExist()
    }

    @Test
    fun loadingStateShowsSpinner() {
        setContent(GamePeekSheetData.Loading(gameId = GAME_ID, gameName = GAME_NAME, thumb = null))

        composeTestRule.onNodeWithContentDescription(labels.loading).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.goToDeal).assertDoesNotExist()
    }

    @Test
    fun errorStateShowsMessageAndRetries() {
        setContent(GamePeekSheetData.Error(gameId = GAME_ID, gameName = GAME_NAME, thumb = null))

        composeTestRule.onNodeWithText(labels.errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { onRetry() }
    }

    private data class Labels(
        val bestPriceSubtitle: String,
        val otherStoresHeader: String,
        val otherStoreRow: String,
        val waitlistAdd: String,
        val collectionAdd: String,
        val moreActions: String,
        val share: String,
        val ignoreAdd: String,
        val viewGamePage: String,
        val goToDeal: String,
        val noDeals: String,
        val loading: String,
        val errorMessage: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                bestPriceSubtitle = stringResource(Res.string.game_peek_best_price_at_store, BEST_PRICE, BEST_STORE),
                otherStoresHeader = stringResource(Res.string.game_peek_other_stores_label),
                otherStoreRow = stringResource(Res.string.game_peek_store_row_description, OTHER_PRICE, OTHER_STORE),
                waitlistAdd = stringResource(Res.string.deal_favourite_add_action),
                collectionAdd = stringResource(Res.string.deal_collection_add_action),
                moreActions = stringResource(Res.string.game_peek_more_actions),
                share = stringResource(Res.string.deal_share_label),
                ignoreAdd = stringResource(Res.string.deal_ignore_add_action),
                viewGamePage = stringResource(Res.string.deal_details_view_game_page_label),
                goToDeal = stringResource(Res.string.deal_details_go_to_deal_label),
                noDeals = stringResource(Res.string.game_peek_no_deals_label),
                loading = stringResource(Res.string.deal_details_loading_indicator),
                errorMessage = stringResource(Res.string.deal_details_data_loading_error_msg),
                retry = stringResource(Res.string.deal_details_data_loading_error_retry),
            )
        }
    }

    private companion object {
        const val GAME_ID = "123"
        const val GAME_NAME = "No Man's Sky"
        const val BEST_PRICE = "$18.86"
        const val BEST_STORE = "Store Name" // PreviewStore.storeName
        const val OTHER_PRICE = "$21.99"
        const val OTHER_STORE = "GOG"
        const val BEST_URL = "https://example.com/deal"
        const val OTHER_URL = "https://example.com/other"

        fun bestDeal() = GameDetails.GameDeal(
            storeID = PreviewStore.storeID,
            dealID = "deal-1",
            priceValue = 18.86,
            priceDenominated = BEST_PRICE,
            retailPriceValue = 58.99,
            retailPriceDenominated = "$58.99",
            savings = 68,
            url = BEST_URL,
        )

        fun otherDeal() = GameDetails.GameDeal(
            storeID = 7,
            dealID = "deal-2",
            priceValue = 21.99,
            priceDenominated = OTHER_PRICE,
            retailPriceValue = 58.99,
            retailPriceDenominated = "$58.99",
            savings = 62,
            url = OTHER_URL,
        )
    }
}
