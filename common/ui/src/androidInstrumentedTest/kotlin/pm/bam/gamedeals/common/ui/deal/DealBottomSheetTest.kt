package pm.bam.gamedeals.common.ui.deal

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
    private val gameId = 42
    private val gameName = "Game Name"
    private val gamePrice = "Game Price"

    @Test
    fun loadingState() {
        val loadingData = DealBottomSheetData.DealDetailsLoading(
            store = store,
            gameId = gameId,
            gameName = gameName,
            dealId = dealId,
            gameSalesPriceDenominated = gamePrice
        )

        var expectedGameData = ""
        var loadingCd = ""

        composeTestRule.setContent {
            expectedGameData = stringResource(Res.string.deal_details_title_label, mockStoreName, gamePrice)
            loadingCd = stringResource(Res.string.deal_details_loading_indicator)

            GameDealsTheme {
                DealBottomSheet(
                    data = loadingData,
                    onDismiss = {},
                    onShare = {},
                    goToWeb = { _, _ -> },
                    onRetryDealDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expectedGameData)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(gameName)
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(loadingCd)
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

        var expectedMessage = ""
        var expectedBtnText = ""
        var loadingCd = ""

        composeTestRule.setContent {
            expectedMessage = stringResource(Res.string.deal_details_data_loading_error_msg)
            expectedBtnText = stringResource(Res.string.deal_details_data_loading_error_retry)
            loadingCd = stringResource(Res.string.deal_details_loading_indicator)

            GameDealsTheme {
                DealBottomSheet(
                    data = loadingData,
                    onDismiss = {},
                    onShare = {},
                    goToWeb = { _, _ -> },
                    onRetryDealDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expectedMessage)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(expectedBtnText)
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(loadingCd)
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
            gameId = gameId,
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
        var cheaperStoreRowCd = ""
        var loadingCd = ""
        var goToDealLabel = ""

        composeTestRule.setContent {
            expectedGameData = stringResource(Res.string.deal_details_title_label, mockStoreName, gamePrice)
            expectedCheapestStore = stringResource(Res.string.deal_details_cheapest_store_label)
                .plus(stringResource(Res.string.deal_details_cheapest_no))
            expectedCheapestPrice = stringResource(Res.string.deal_details_cheapest_on_label, cheapestPriceDenominated, cheapestPriceDate)
            cheaperStoreRowCd = stringResource(Res.string.deal_details_cheaper_store_row_description, cheaperStoreName, salePriceDenominated)
            loadingCd = stringResource(Res.string.deal_details_loading_indicator)
            goToDealLabel = stringResource(Res.string.deal_details_go_to_deal_label)

            GameDealsTheme {
                DealBottomSheet(
                    data = dealDetailsData,
                    onDismiss = {},
                    onShare = {},
                    goToWeb = goToActions,
                    onRetryDealDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expectedGameData)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(gameName)
            .assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(loadingCd)
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(expectedCheapestStore)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(expectedCheapestPrice)
            .assertIsDisplayed()

        composeTestRule.onNode(hasContentDescription(cheaperStoreRowCd) and hasRole(Role.Button))
            .assertIsDisplayed()

        verify(exactly = 0) { goToActions.invoke(any(), any()) }

        composeTestRule.onNode(hasContentDescription(cheaperStoreRowCd) and hasRole(Role.Button))
            .performClick()

        verify(exactly = 1) { goToActions.invoke(cheapsharkDealRedirectUrl(cheaperStoreDetailsDealId), gameName) }

        composeTestRule.onNodeWithText(goToDealLabel)
            .performClick()

        verify(exactly = 1) { goToActions.invoke(cheapsharkDealRedirectUrl(dealId), gameName) }
    }

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
}
