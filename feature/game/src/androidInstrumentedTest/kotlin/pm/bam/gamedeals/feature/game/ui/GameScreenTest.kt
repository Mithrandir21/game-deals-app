package pm.bam.gamedeals.feature.game.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.device.DeviceInteraction.Companion.setScreenOrientation
import androidx.test.espresso.device.EspressoDevice.Companion.onDevice
import androidx.test.espresso.device.action.ScreenOrientation
import androidx.test.espresso.device.rules.ScreenOrientationRule
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry

class GameScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenOrientationRule: ScreenOrientationRule = ScreenOrientationRule(ScreenOrientation.PORTRAIT)


    private val viewModel: GameViewModel = mockk()


    private val gameTitle = "Title"

    private val mockCheapestPriceEver: GameDetails.GameCheapestPriceEver = mockk {
        every { priceDenominated } returns "PriceDenominated"
        every { date } returns "Date"
    }
    private val gameInfo: GameDetails.GameInfo = mockk {
        every { thumb } returns "Thumb"
        every { title } returns gameTitle
    }

    private val dealId = "ID"
    private val dealSavings = 10
    private val price = 1.1
    private val priceDenominatedText = "$price"
    private val gameDeal: GameDetails.GameDeal = mockk {
        every { dealID } returns dealId
        every { savings } returns dealSavings
        every { priceValue } returns price
        every { priceDenominated } returns priceDenominatedText
    }
    private val gameDetails: GameDetails = mockk {
        every { info } returns gameInfo
        every { deals } returns persistentListOf(gameDeal)
        every { cheapestPriceEver } returns mockCheapestPriceEver
    }

    private val mockStoreName = "Store Name"
    private val storeImages: Store.StoreImages = mockk {
        every { icon } returns "Icon"
    }
    private val store: Store = mockk {
        every { storeName } returns mockStoreName
        every { images } returns storeImages
    }


    @Before
    fun setup() {
        every { viewModel.reloadGameDetails() } just runs
    }

    @Test
    fun initialLoading() {
        every { viewModel.uiState } returns MutableStateFlow(GameViewModel.GameScreenData.Loading)

        composeTestRule.setContent {
            GameDealsTheme {
                GameScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LoadingDataTag)
            .assertIsDisplayed()

        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(GameViewModel.GameScreenData.Error)

        var snackText = ""
        var snackRetry = ""

        composeTestRule.setContent {
            snackText = stringResource(Res.string.game_screen_data_loading_error_msg)
            snackRetry = stringResource(Res.string.game_screen_data_loading_error_retry)

            GameDealsTheme {
                GameScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText(snackText)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(snackRetry)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.reloadGameDetails() }
        verify(exactly = 1) { viewModel.uiState }

        // Retry button clicked
        composeTestRule.onNodeWithText(snackRetry)
            .assertIsDisplayed()
            .performClick()

        verify(exactly = 1) { viewModel.reloadGameDetails() }
    }


    @Test
    fun gameDetailsLoaded() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameViewModel.GameScreenData.Data(
                gameDetails = gameDetails,
                dealDetails = persistentListOf(store to gameDeal)
            )
        )

        composeTestRule.setContent {
            GameDealsTheme {
                GameScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(GameDetailsTag)
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(GameDetailsTitleTag)
            .assertIsDisplayed()
            .assertTextEquals(gameTitle)

        composeTestRule.onNodeWithTag(GameDealItemStoreTitleLabelTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(mockStoreName)

        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun gameDetailsLoadedWide() {
        onDevice().setScreenOrientation(ScreenOrientation.LANDSCAPE)

        gameDetailsLoaded()
    }


    @Test
    fun onBackActioned() {
        val onBack: () -> Unit = mockk()

        every { onBack.invoke() } just runs
        every { viewModel.uiState } returns MutableStateFlow(
            GameViewModel.GameScreenData.Data(
                gameDetails = gameDetails,
                dealDetails = persistentListOf(store to gameDeal)
            )
        )

        composeTestRule.setContent {
            GameDealsTheme {
                GameScreen(
                    onBack = onBack,
                    goToWeb = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(TopAppNavBarTag)
            .performClick()

        verify(exactly = 1) { viewModel.uiState }
        verify(exactly = 1) { onBack.invoke() }
    }
}
