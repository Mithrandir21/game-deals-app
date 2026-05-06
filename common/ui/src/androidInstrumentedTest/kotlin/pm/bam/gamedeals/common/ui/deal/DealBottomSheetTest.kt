package pm.bam.gamedeals.common.ui.deal

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_no
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_on_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_store_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_msg
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_retry
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_title_label
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.cheapsharkDealRedirectUrl

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
    private val gameName = "Game Name"
    private val gamePrice = "Game Price"

    @Test
    fun loadingState() {
        val loadingData = DealBottomSheetData.DealDetailsLoading(
            store = store,
            gameName = gameName,
            dealId = dealId,
            gameSalesPriceDenominated = gamePrice
        )

        var expectedGameData = ""

        composeTestRule.setContent {
            expectedGameData = stringResource(Res.string.deal_details_title_label, mockStoreName, gamePrice)

            GameDealsTheme {
                DealBottomSheet(
                    data = loadingData,
                    onDismiss = {},
                    goToWeb = { _, _ -> },
                    onRetryDealDetails = {}
                )
            }
        }


        composeTestRule.onNodeWithTag(StoreDataGameDataTag)
            .assert(hasTextExactly(expectedGameData))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(StoreDataGameNameTag)
            .assert(hasTextExactly(gameName))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(DataLoadingTag)
            .assertIsDisplayed()
    }


    @Test
    fun errorState() {
        val loadingData = DealBottomSheetData.DealDetailsError(
            store = store,
            gameName = gameName,
            dealId = dealId,
            gameSalesPriceDenominated = gamePrice
        )

        var expectedMessage = ""
        var expectedBtnText = ""

        composeTestRule.setContent {
            expectedMessage = stringResource(Res.string.deal_details_data_loading_error_msg)
            expectedBtnText = stringResource(Res.string.deal_details_data_loading_error_retry)

            GameDealsTheme {
                DealBottomSheet(
                    data = loadingData,
                    onDismiss = {},
                    goToWeb = { _, _ -> },
                    onRetryDealDetails = {}
                )
            }
        }


        composeTestRule.onNodeWithTag(DataErrorMsgTag)
            .assert(hasTextExactly(expectedMessage))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(DataErrorBtnTag)
            .assert(hasTextExactly(expectedBtnText))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(DataLoadingTag)
            .assertIsNotDisplayed()
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
        val cheaperStoreName = "Store Name"
        val cheaperStore: Store = mockk {
            every { this@mockk.storeID } returns cheaperStoreId
            every { this@mockk.images.logo } returns "Logo"
            every { this@mockk.storeName } returns cheaperStoreName
        }

        val cheaperStoreDetailsDealId = "DealID"
        val cheaperStoreDetails: DealDetails.CheaperStore = mockk {
            every { this@mockk.dealID } returns cheaperStoreDetailsDealId
            every { this@mockk.salePriceDenominated } returns salePriceDenominated
        }

        val cheapestPriceDenominated = "Cheapest Price"
        val cheapestPriceDate = "Cheapest Date"
        val cheapestPrice: DealDetails.CheapestPrice = mockk {
            every { this@mockk.priceDenominated } returns cheapestPriceDenominated
            every { this@mockk.date } returns cheapestPriceDate
        }


        val dealDetailsData = DealBottomSheetData.DealDetailsData(
            store = store,
            gameName = gameName,
            dealId = dealId,
            gameSalesPriceDenominated = gamePrice,
            gameInfo = gameInfo,
            cheaperStores = listOf(cheaperStore to cheaperStoreDetails),
            cheapestPrice = cheapestPrice,
        )

        val goToActions: (url: String, gameTitle: String) -> Unit = mockk {
            every { this@mockk.invoke(any(), any()) } just Runs
        }

        var expectedGameData = ""
        var expectedCheapestStore = ""
        var expectedCheapestPrice = ""

        composeTestRule.setContent {
            expectedGameData = stringResource(Res.string.deal_details_title_label, mockStoreName, gamePrice)
            expectedCheapestStore = stringResource(Res.string.deal_details_cheapest_store_label)
                .plus(stringResource(Res.string.deal_details_cheapest_no))
            expectedCheapestPrice = stringResource(Res.string.deal_details_cheapest_on_label, cheapestPriceDenominated, cheapestPriceDate)

            GameDealsTheme {
                DealBottomSheet(
                    data = dealDetailsData,
                    onDismiss = {},
                    goToWeb = goToActions,
                    onRetryDealDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(StoreDataGameDataTag)
            .assert(hasTextExactly(expectedGameData))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(StoreDataGameNameTag)
            .assert(hasTextExactly(gameName))
            .assertIsDisplayed()

        // Loading not being shown
        composeTestRule.onNodeWithTag(DataLoadingTag)
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag(DealCheapestTag)
            .assert(hasTextExactly(expectedCheapestStore))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(CheapestPriceTag)
            .assert(hasTextExactly(expectedCheapestPrice))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(DealCheaperStoreRowTag.plus(cheaperStoreId))
            .assertIsDisplayed()

        verify(exactly = 0) { goToActions.invoke(any(), any()) }


        composeTestRule.onNodeWithTag(DealCheaperStoreRowTag.plus(cheaperStoreId))
            .performClick()

        verify(exactly = 1) { goToActions.invoke(cheapsharkDealRedirectUrl(cheaperStoreDetailsDealId), gameName) }

        composeTestRule.onNodeWithTag(GoToDealBtnTag)
            .performClick()

        verify(exactly = 1) { goToActions.invoke(cheapsharkDealRedirectUrl(dealId), gameName) }
    }
}
