package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_opens_externally
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_indicator
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: HomeViewModel = mockk {
        every { events } returns MutableSharedFlow<HomeViewModel.HomeUiEvent>().asSharedFlow()
    }

    private lateinit var screenSemantics: ScreenSemantics

    @Before
    fun setup() {
        every { viewModel.dealDetails } returns MutableStateFlow(null)
        every { viewModel.waitlistIds } returns MutableStateFlow(persistentSetOf())
    }

    private fun setupCompose(
        goToGame: (String) -> Unit = { _ -> },
        onViewGiveaways: () -> Unit = {},
        onViewBundles: () -> Unit = {},
        onViewBundle: (Int) -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
        goToGameDetails: (Int, String) -> Unit = { _, _ -> },
        goToGameDetailsByTitle: (String) -> Unit = { _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            HomeScreen(
                goToGame = goToGame,
                onViewGiveaways = onViewGiveaways,
                onViewBundles = onViewBundles,
                onViewBundle = onViewBundle,
                goToWeb = goToWeb,
                goToGameDetails = goToGameDetails,
                goToGameDetailsByTitle = goToGameDetailsByTitle,
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun loadingState() {
        every { viewModel.uiState } returns MutableStateFlow(HomeScreenData(status = HomeScreenStatus.LOADING))

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.errorMsg).assertDoesNotExist()
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(HomeScreenData(status = HomeScreenStatus.ERROR))

        setupCompose()

        composeTestRule.onNodeWithContentDescription(screenSemantics.loading).assertDoesNotExist()
        composeTestRule.onNodeWithText(screenSemantics.errorMsg).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.retry).assertIsDisplayed()
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
            HomeScreenData(status = HomeScreenStatus.DATA, giveaways = persistentListOf(rich))
        )

        setupCompose()

        composeTestRule.onNodeWithText("Rich Giveaway").assertIsDisplayed()
        composeTestRule.onNodeWithText("FREE $59.99 - Game · PC, Steam").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(screenSemantics.opensExternally, substring = true).assertIsDisplayed()
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
            HomeScreenData(status = HomeScreenStatus.DATA, giveaways = persistentListOf(freebie))
        )

        setupCompose()

        composeTestRule.onNodeWithText("Free Beta").assertIsDisplayed()
        composeTestRule.onNodeWithText("FREE - Early Access · PC").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(screenSemantics.opensExternally, substring = true).assertIsDisplayed()
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
        val opensExternally: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                loading = stringResource(Res.string.home_screen_loading_indicator),
                errorMsg = stringResource(Res.string.home_screen_data_loading_error_msg),
                retry = stringResource(Res.string.home_screen_data_loading_error_retry),
                opensExternally = stringResource(Res.string.home_screen_giveaway_opens_externally),
            )
        }
    }
}
