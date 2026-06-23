package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.feature.bundles.generated.resources.Res
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_get_bundle
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_navigation_back_button
import pm.bam.gamedeals.feature.bundles.ui.BundleDetailViewModel.BundleDetailScreenData

/**
 * Device UI test for [BundleDetailScreen] via a mocked [BundleDetailViewModel]. Asserts the bundle
 * renders, the "Get bundle" CTA and a game-row tap dispatch, and back / error-retry wiring.
 */
class BundleDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: BundleDetailViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val goToWeb = mockk<(String, String) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private val bundle = Bundle(
        id = 1,
        title = BUNDLE_TITLE,
        storeName = "Humble Store",
        url = BUNDLE_URL,
        expiryEpochMs = null,
        gameCount = 1,
        priceDenominated = "$14.99",
        games = persistentListOf(Bundle.BundleGame(GAME_ID, GAME_TITLE)),
    )

    private fun setContent(state: BundleDetailScreenData) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.gamePeek } returns MutableStateFlow<GamePeekSheetData?>(null)
        every { viewModel.waitlistIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.collectionIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.ignoredIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.events } returns MutableSharedFlow()
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                BundleDetailScreen(onBack = onBack, goToWeb = goToWeb, onGameClick = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun dataStateRendersTitleAndGetBundleCta() {
        setContent(BundleDetailScreenData.Data(bundle = bundle))

        composeTestRule.onNodeWithText(BUNDLE_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.getBundle).performClick()

        verify(exactly = 1) { goToWeb(BUNDLE_URL, BUNDLE_TITLE) }
    }

    @Test
    fun tappingGameRowPeeksTheGame() {
        setContent(BundleDetailScreenData.Data(bundle = bundle))

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { viewModel.peekGame(GAME_ID, GAME_TITLE, any()) }
    }

    @Test
    fun backIconDispatchesOnBack() {
        setContent(BundleDetailScreenData.Data(bundle = bundle))

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    @Test
    fun errorStateShowsRetryAndDispatches() {
        setContent(BundleDetailScreenData.Error)

        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { viewModel.load() }
    }

    private data class Labels(
        val getBundle: String,
        val back: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                getBundle = stringResource(Res.string.bundle_detail_get_bundle),
                back = stringResource(Res.string.bundles_screen_navigation_back_button),
                retry = stringResource(Res.string.bundles_screen_data_loading_error_retry),
            )
        }
    }

    private companion object {
        const val BUNDLE_TITLE = "Indie Bundle"
        const val BUNDLE_URL = "https://example.com/b"
        const val GAME_ID = "hk"
        const val GAME_TITLE = "Hollow Knight"
    }
}
