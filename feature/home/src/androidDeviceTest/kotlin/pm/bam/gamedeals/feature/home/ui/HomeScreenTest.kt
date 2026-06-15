package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_retry
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
        every { viewModel.gamePeek } returns MutableStateFlow(null)
        every { viewModel.waitlistIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.ignoredIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.stores } returns MutableStateFlow(persistentMapOf())
    }

    private fun setupCompose(
        goToGame: (String) -> Unit = { _ -> },
        goToGameByTitle: (String) -> Unit = { _ -> },
        onViewWaitlist: () -> Unit = {},
        onViewCollection: () -> Unit = {},
        onViewBundles: () -> Unit = {},
        onViewBundle: (Int) -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            HomeScreen(
                goToGame = goToGame,
                goToGameByTitle = goToGameByTitle,
                onViewWaitlist = onViewWaitlist,
                onViewCollection = onViewCollection,
                onViewBundles = onViewBundles,
                onViewBundle = onViewBundle,
                goToWeb = goToWeb,
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
        }
    }
}
