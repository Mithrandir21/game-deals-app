package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.device.DeviceInteraction.Companion.setScreenOrientation
import androidx.test.espresso.device.EspressoDevice.Companion.onDevice
import androidx.test.espresso.device.action.ScreenOrientation
import androidx.test.espresso.device.rules.ScreenOrientationRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.FavouriteGame
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_store_deals_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_indicator
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_banner
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus
import pm.bam.gamedeals.testing.SkipOnCi

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenOrientationRule: ScreenOrientationRule = ScreenOrientationRule(ScreenOrientation.PORTRAIT)

    private val viewModel: HomeViewModel = mockk {
        every { events } returns MutableSharedFlow<HomeViewModel.HomeUiEvent>().asSharedFlow()
    }

    private val storeId = 1
    private val storeTitle = "StoreTitle"

    private val storeBanner = "Banner"
    private val storeLogo = "Logo"

    private val dealId = "DealId"
    private val dealTitle = "Title"
    private val normalPrice = "NormalPrice"
    private val dealPrice = "Price"
    private val dealThumb = "DealThumbnail"


    private val mockImages: Store.StoreImages = mockk {
        every { banner } returns storeBanner
        every { logo } returns storeLogo
    }
    private val mockStore: Store = mockk {
        every { storeID } returns storeId
        every { storeName } returns storeTitle
        every { images } returns mockImages
    }
    private val mockDeal: Deal = mockk {
        every { dealID } returns dealId
        every { gameID } returns 1
        every { title } returns dealTitle
        every { normalPriceDenominated } returns normalPrice
        every { salePriceDenominated } returns dealPrice
        every { thumb } returns dealThumb
    }
    private val mockStoreData = HomeScreenListData.StoreData(mockStore)
    private val mockDealData = HomeScreenListData.DealData(mockDeal)
    private val mockViewAllData = HomeScreenListData.ViewAllData(mockStore)

    private lateinit var screenSemantics: ScreenSemantics

    private var bannerCd: String = ""
    private var viewAllText: String = ""

    @Before
    fun setup() {
        every { viewModel.dealDetails } returns MutableStateFlow(null)
        every { viewModel.favouriteIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.favourites } returns MutableStateFlow(persistentListOf<FavouriteGame>())
    }

    private fun setupCompose(
        onSearch: () -> Unit = {},
        goToGame: (Int) -> Unit = { _ -> },
        onViewStoreDeals: (Store) -> Unit = { _ -> },
        onViewGiveaways: () -> Unit = {},
        onViewFavourites: () -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            bannerCd = ScreenSemantics.bannerCd(storeTitle)
            viewAllText = ScreenSemantics.viewAllText(storeTitle)
            HomeScreen(
                onSearch = onSearch,
                goToGame = goToGame,
                onViewStoreDeals = onViewStoreDeals,
                onViewGiveaways = onViewGiveaways,
                onViewFavourites = onViewFavourites,
                goToWeb = goToWeb,
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun loadingState() {
        val mockData = HomeScreenData(state = HomeScreenStatus.LOADING)

        every { viewModel.uiState } returns MutableStateFlow(mockData)

        setupCompose()

        composeTestRule.onNodeWithContentDescription(bannerCd).assertDoesNotExist()
        composeTestRule.onNodeWithText(dealTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(viewAllText).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertIsDisplayed()
    }

    @Test
    fun errorState() {
        val mockData = HomeScreenData(state = HomeScreenStatus.ERROR)

        every { viewModel.uiState } returns MutableStateFlow(mockData)

        setupCompose()

        composeTestRule.onNodeWithContentDescription(bannerCd).assertDoesNotExist()
        composeTestRule.onNodeWithText(dealTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(viewAllText).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertDoesNotExist()

        composeTestRule.onNodeWithText(screenSemantics.errorMsg).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.retry).assertIsDisplayed()
    }

    @Test
    fun storeDataLoaded() {
        val mockData = HomeScreenData(state = HomeScreenStatus.SUCCESS, items = persistentListOf(mockStoreData, mockDealData, mockViewAllData))

        every { viewModel.uiState } returns MutableStateFlow(mockData)

        setupCompose()

        composeTestRule.onNodeWithContentDescription(bannerCd).assertIsDisplayed()
        composeTestRule.onNodeWithText(dealTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(viewAllText).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertDoesNotExist()
    }

    @Test
    @SkipOnCi
    fun storeDataLoadedWide() {
        onDevice().setScreenOrientation(ScreenOrientation.LANDSCAPE)

        storeDataLoaded()
    }

    @Test
    fun giveawayRow_shows_worth_type_and_platforms() {
        val rich = aGiveaway(
            id = 99,
            title = "Rich Giveaway",
            worthDenominated = "$59.99",
            type = GiveawayType.GAME,
            platforms = persistentListOf(GiveawayPlatform.PC, GiveawayPlatform.STEAM),
        )
        every { viewModel.uiState } returns MutableStateFlow(
            HomeScreenData(state = HomeScreenStatus.SUCCESS, giveaways = persistentListOf(rich))
        )

        setupCompose()

        composeTestRule.onNodeWithText("Rich Giveaway").assertIsDisplayed()
        composeTestRule.onNodeWithText("FREE $59.99 - Game · PC, Steam").assertIsDisplayed()
    }

    @Test
    fun giveawayRow_with_null_worth_shows_FREE_only() {
        val freebie = aGiveaway(
            id = 100,
            title = "Free Beta",
            worthDenominated = null,
            type = GiveawayType.BETA,
            platforms = persistentListOf(GiveawayPlatform.PC),
        )
        every { viewModel.uiState } returns MutableStateFlow(
            HomeScreenData(state = HomeScreenStatus.SUCCESS, giveaways = persistentListOf(freebie))
        )

        setupCompose()

        composeTestRule.onNodeWithText("Free Beta").assertIsDisplayed()
        composeTestRule.onNodeWithText("FREE - Early Access · PC").assertIsDisplayed()
    }

    private fun aGiveaway(
        id: Int,
        title: String,
        worthDenominated: String?,
        type: GiveawayType,
        platforms: ImmutableList<GiveawayPlatform>,
    ) = Giveaway(
        id = id,
        title = title,
        worthDenominated = worthDenominated,
        worth = null,
        thumbnail = "thumb.png",
        image = "image.png",
        description = "",
        instructions = "",
        openGiveawayUrl = "https://example.com/open",
        publishedDate = LocalDateTime(1970, 1, 1, 0, 0),
        type = type,
        platforms = platforms,
        endDate = null,
        users = 0,
        status = "Active",
        gamerpowerUrl = "https://example.com",
        openGiveaway = "https://example.com/giveaway",
    )

    private data class ScreenSemantics(
        val loading: String,
        val errorMsg: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                loading = stringResource(Res.string.home_screen_loading_indicator),
                errorMsg = stringResource(Res.string.home_screen_data_loading_error_msg),
                retry = stringResource(Res.string.home_screen_data_loading_error_retry),
            )

            @Composable
            fun bannerCd(storeName: String): String =
                stringResource(Res.string.home_screen_store_banner, storeName)

            @Composable
            fun viewAllText(storeName: String): String =
                stringResource(Res.string.home_screen_all_store_deals_label, storeName)
        }
    }
}
