package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_navigation_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_toolbar_title_loading
import pm.bam.gamedeals.testing.SkipOnCi

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
        every { steamAppID } returns null
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

    private lateinit var screenSemantics: ScreenSemantics

    @Before
    fun setup() {
        every { viewModel.reloadGameDetails() } just runs
        every { viewModel.isFavourite } returns MutableStateFlow(false)
        every { viewModel.events } returns MutableSharedFlow<GameViewModel.GameUiEvent>().asSharedFlow()
    }

    private fun setupCompose(
        onBack: () -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
        goToGameDetails: (Int, String) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            GameDealsTheme {
                GameScreen(
                    onBack = onBack,
                    goToWeb = goToWeb,
                    goToGameDetails = goToGameDetails,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun initialLoading() {
        every { viewModel.uiState } returns MutableStateFlow(GameViewModel.GameScreenData.Loading)

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.loadingTitle)
            .assertIsDisplayed()

        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(GameViewModel.GameScreenData.Error)

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.errorMsg)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(screenSemantics.retry)
            .assertIsDisplayed()

        verify(exactly = 0) { viewModel.reloadGameDetails() }
        verify(exactly = 1) { viewModel.uiState }

        composeTestRule.onNodeWithText(screenSemantics.retry)
            .assertIsDisplayed()
            .performClick()

        verify(exactly = 1) { viewModel.reloadGameDetails() }
    }


    @Test
    fun gameDetailsLoaded() {
        every { viewModel.uiState } returns MutableStateFlow(
            GameViewModel.GameScreenData.Data(
                gameDetails = gameDetails,
                dealDetails = persistentListOf(StoreDealPair(store = store, deal = gameDeal))
            )
        )

        setupCompose()

        composeTestRule.onAllNodesWithText(gameTitle)
            .assertCountEquals(2)

        composeTestRule.onNodeWithText(mockStoreName)
            .assertIsDisplayed()

        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    @SkipOnCi
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
                dealDetails = persistentListOf(StoreDealPair(store = store, deal = gameDeal))
            )
        )

        setupCompose(onBack = onBack)

        composeTestRule.onNodeWithContentDescription(screenSemantics.back)
            .performClick()

        verify(exactly = 1) { viewModel.uiState }
        verify(exactly = 1) { onBack.invoke() }
    }

    private data class ScreenSemantics(
        val loadingTitle: String,
        val errorMsg: String,
        val retry: String,
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                loadingTitle = stringResource(Res.string.game_screen_toolbar_title_loading),
                errorMsg = stringResource(Res.string.game_screen_data_loading_error_msg),
                retry = stringResource(Res.string.game_screen_data_loading_error_retry),
                back = stringResource(Res.string.game_screen_navigation_back_button),
            )
        }
    }
}
