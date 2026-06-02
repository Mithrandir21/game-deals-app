package pm.bam.gamedeals.common.ui.deal

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheaper_store_row_description
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_no
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_on_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_store_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_msg
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_retry
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_go_to_deal_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_loading_indicator
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_title_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_view_game_details_label
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Store

class DealBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockStoreId = 1
    private val mockStoreName = "Store Name"
    private val store: Store = mockk {
        every { this@mockk.storeID } returns mockStoreId
        every { this@mockk.images.logo } returns "Logo"
        every { this@mockk.storeName } returns mockStoreName
    }
    private val dealId = "Deal ID"
    private val gameId = 42
    private val gameName = "Game Name"
    private val gamePrice = "Game Price"

    private lateinit var screenSemantics: ScreenSemantics

    private var titleLabel: String = ""
    private var cheapestOnLabel: String = ""
    private var cheaperStoreRowCd: String = ""

    private fun setupCompose(
        data: DealBottomSheetData?,
        onDismiss: () -> Unit = {},
        onShare: (DealBottomSheetData) -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
        goToGameDetails: (Int, String) -> Unit = { _, _ -> },
        goToGameDetailsByTitle: (String) -> Unit = {},
        onRetryDealDetails: () -> Unit = {},
        cheapestOnArgs: Pair<String, String>? = null,
        cheaperStoreRowArgs: Pair<String, String>? = null,
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            titleLabel = ScreenSemantics.titleLabel(mockStoreName, gamePrice)
            cheapestOnArgs?.let { (price, date) ->
                cheapestOnLabel = ScreenSemantics.cheapestOnLabel(price, date)
            }
            cheaperStoreRowArgs?.let { (storeName, price) ->
                cheaperStoreRowCd = ScreenSemantics.cheaperStoreRowCd(storeName, price)
            }
            GameDealsTheme {
                DealBottomSheet(
                    data = data,
                    onDismiss = onDismiss,
                    onShare = onShare,
                    goToWeb = goToWeb,
                    goToGameDetails = goToGameDetails,
                    goToGameDetailsByTitle = goToGameDetailsByTitle,
                    onRetryDealDetails = onRetryDealDetails,
                )
            }
        }
    }

    @Test
    fun loadingState() {
        val loadingData = DealBottomSheetData.DealDetailsLoading(
            store = store,
            gameId = gameId,
            gameName = gameName,
            dealId = dealId,
            gameSalesPriceDenominated = gamePrice
        )

        setupCompose(data = loadingData)

        composeTestRule.onNodeWithText(titleLabel)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(gameName)
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading)
            .assertIsDisplayed()
    }


    @Test
    fun errorState() {
        val loadingData = DealBottomSheetData.DealDetailsError(
            store = store,
            gameId = gameId,
            gameName = gameName,
            dealId = dealId,
            gameSalesPriceDenominated = gamePrice
        )

        setupCompose(data = loadingData)

        composeTestRule.onNodeWithText(screenSemantics.errorMsg)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(screenSemantics.retry)
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading)
            .assertDoesNotExist()
    }


    @Test
    fun dataScreen() {
        val gameInfoThumb = "Thumb"
        val gameInfoName = "Name"
        val gameInfoScore = 1
        val gameInfoPercentage = 2
        val gameInfoReleaseDate = "Thumb"
        val gameInfoSteamId = 3
        val gameInfoSteamworks = true
        val gameInfo: DealDetails.GameInfo = mockk {
            every { this@mockk.thumb } returns gameInfoThumb
            every { this@mockk.name } returns gameInfoName
            every { this@mockk.metacriticScore } returns gameInfoScore
            every { this@mockk.steamRatingPercent } returns gameInfoPercentage
            every { this@mockk.releaseDate } returns gameInfoReleaseDate
            every { this@mockk.steamAppID } returns gameInfoSteamId
            every { this@mockk.steamworks } returns gameInfoSteamworks
        }

        val salePriceDenominated = "Cheaper"
        val cheaperStoreId = 1
        val cheaperStoreName = "Cheaper Store"
        val cheaperStore: Store = mockk {
            every { this@mockk.storeID } returns cheaperStoreId
            every { this@mockk.images.logo } returns "Logo"
            every { this@mockk.storeName } returns cheaperStoreName
        }

        val cheaperStoreDetailsDealId = "DealID"
        val cheaperStoreUrl = "https://example.com/cheaper-store-deal"
        val cheaperStoreDetails: DealDetails.CheaperStore = mockk {
            every { this@mockk.dealID } returns cheaperStoreDetailsDealId
            every { this@mockk.salePriceDenominated } returns salePriceDenominated
            every { this@mockk.url } returns cheaperStoreUrl
        }

        val cheapestPriceDenominated = "Cheapest Price"
        val cheapestPriceDate = "Cheapest Date"
        val cheapestPrice: DealDetails.CheapestPrice = mockk {
            every { this@mockk.priceDenominated } returns cheapestPriceDenominated
            every { this@mockk.date } returns cheapestPriceDate
        }


        val mainDealUrl = "https://example.com/main-deal"
        val dealDetailsData = DealBottomSheetData.DealDetailsData(
            store = store,
            gameId = gameId,
            gameName = gameName,
            dealId = dealId,
            dealUrl = mainDealUrl,
            gameSalesPriceDenominated = gamePrice,
            gameInfo = gameInfo,
            cheaperStores = persistentListOf(StoreCheaperStorePair(store = cheaperStore, cheaperStore = cheaperStoreDetails)),
            cheapestPrice = cheapestPrice,
        )

        val goToActions: (url: String, gameTitle: String) -> Unit = mockk {
            every { this@mockk.invoke(any(), any()) } just Runs
        }

        setupCompose(
            data = dealDetailsData,
            goToWeb = goToActions,
            cheapestOnArgs = cheapestPriceDenominated to cheapestPriceDate,
            cheaperStoreRowArgs = cheaperStoreName to salePriceDenominated,
        )

        composeTestRule.onNodeWithText(titleLabel)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(gameName)
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading)
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(screenSemantics.cheapestStoreLabel + screenSemantics.cheapestNo)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(cheapestOnLabel)
            .assertIsDisplayed()

        composeTestRule.onNode(hasContentDescription(cheaperStoreRowCd) and hasRole(Role.Button))
            .assertIsDisplayed()

        verify(exactly = 0) { goToActions.invoke(any(), any()) }

        composeTestRule.onNode(hasContentDescription(cheaperStoreRowCd) and hasRole(Role.Button))
            .performClick()

        verify(exactly = 1) { goToActions.invoke(cheaperStoreUrl, gameName) }

        composeTestRule.onNodeWithText(screenSemantics.goToDeal)
            .performClick()

        verify(exactly = 1) { goToActions.invoke(mainDealUrl, gameName) }
    }

    @Test
    fun gameDetailsButton_visible_when_steamAppID_present() {
        val steamId = 1240440
        val data = dealDetailsData(steamAppID = steamId)

        setupCompose(data = data)

        composeTestRule.onNodeWithText(screenSemantics.gameDetails)
            .assertIsDisplayed()
    }

    @Test
    fun gameDetailsButton_visible_when_steamAppID_null_so_non_Steam_deals_can_route_by_title() {
        val data = dealDetailsData(steamAppID = null)

        setupCompose(data = data)

        composeTestRule.onNodeWithText(screenSemantics.gameDetails)
            .assertIsDisplayed()
    }

    @Test
    fun gameDetailsButton_click_invokes_callback_with_steamAppID_and_title() {
        val steamId = 1240440
        val data = dealDetailsData(steamAppID = steamId)
        val goToGameDetails: (Int, String) -> Unit = mockk {
            every { this@mockk.invoke(any(), any()) } just Runs
        }
        val goToGameDetailsByTitle: (String) -> Unit = mockk {
            every { this@mockk.invoke(any()) } just Runs
        }

        setupCompose(
            data = data,
            goToGameDetails = goToGameDetails,
            goToGameDetailsByTitle = goToGameDetailsByTitle,
        )

        verify(exactly = 0) { goToGameDetails.invoke(any(), any()) }

        composeTestRule.onNodeWithText(screenSemantics.gameDetails)
            .performClick()

        // Steam-id path now carries the title for the VM's fallback cascade (handles bundles / sub-ids).
        verify(exactly = 1) { goToGameDetails.invoke(steamId, gameName) }
        verify(exactly = 0) { goToGameDetailsByTitle.invoke(any()) }
    }

    @Test
    fun gameDetailsButton_click_routes_via_title_when_steamAppID_null() {
        val data = dealDetailsData(steamAppID = null)
        val goToGameDetails: (Int, String) -> Unit = mockk {
            every { this@mockk.invoke(any(), any()) } just Runs
        }
        val goToGameDetailsByTitle: (String) -> Unit = mockk {
            every { this@mockk.invoke(any()) } just Runs
        }

        setupCompose(
            data = data,
            goToGameDetails = goToGameDetails,
            goToGameDetailsByTitle = goToGameDetailsByTitle,
        )

        composeTestRule.onNodeWithText(screenSemantics.gameDetails)
            .performClick()

        verify(exactly = 1) { goToGameDetailsByTitle.invoke(gameName) }
        verify(exactly = 0) { goToGameDetails.invoke(any(), any()) }
    }

    private fun dealDetailsData(steamAppID: Int?): DealBottomSheetData.DealDetailsData {
        val gameInfo: DealDetails.GameInfo = mockk {
            every { this@mockk.thumb } returns "Thumb"
            every { this@mockk.name } returns "Name"
            every { this@mockk.metacriticScore } returns null
            every { this@mockk.steamRatingPercent } returns null
            every { this@mockk.releaseDate } returns null
            every { this@mockk.steamAppID } returns steamAppID
            every { this@mockk.steamworks } returns null
        }
        return DealBottomSheetData.DealDetailsData(
            store = store,
            gameId = gameId,
            gameName = gameName,
            dealId = dealId,
            gameSalesPriceDenominated = gamePrice,
            gameInfo = gameInfo,
            cheaperStores = persistentListOf(),
            cheapestPrice = null,
        )
    }

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private data class ScreenSemantics(
        val loading: String,
        val errorMsg: String,
        val retry: String,
        val cheapestStoreLabel: String,
        val cheapestNo: String,
        val goToDeal: String,
        val gameDetails: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                loading = stringResource(Res.string.deal_details_loading_indicator),
                errorMsg = stringResource(Res.string.deal_details_data_loading_error_msg),
                retry = stringResource(Res.string.deal_details_data_loading_error_retry),
                cheapestStoreLabel = stringResource(Res.string.deal_details_cheapest_store_label),
                cheapestNo = stringResource(Res.string.deal_details_cheapest_no),
                goToDeal = stringResource(Res.string.deal_details_go_to_deal_label),
                gameDetails = stringResource(Res.string.deal_details_view_game_details_label),
            )

            @Composable
            fun titleLabel(storeName: String, price: String): String =
                stringResource(Res.string.deal_details_title_label, storeName, price)

            @Composable
            fun cheapestOnLabel(price: String, date: String): String =
                stringResource(Res.string.deal_details_cheapest_on_label, price, date)

            @Composable
            fun cheaperStoreRowCd(storeName: String, price: String): String =
                stringResource(Res.string.deal_details_cheaper_store_row_description, storeName, price)
        }
    }
}
