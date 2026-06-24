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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.feature.bundles.generated.resources.Res
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_empty
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_loading_indicator
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_sort_expiring
import pm.bam.gamedeals.feature.bundles.ui.BundlesViewModel.BundlesScreenData

/**
 * Device UI test for [BundlesScreen] via a mocked [BundlesViewModel]. Covers each list state plus the
 * row-tap and sort-chip interactions.
 */
class BundlesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: BundlesViewModel = mockk(relaxed = true)
    private val onBundleClick = mockk<(Int) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private val bundle = Bundle(
        id = BUNDLE_ID,
        title = BUNDLE_TITLE,
        storeName = "Humble Store",
        url = "https://example.com/b",
        expiryEpochMs = null,
        gameCount = 1,
        priceDenominated = "$14.99",
        games = persistentListOf(Bundle.BundleGame("a", "Game A")),
    )

    private fun setContent(state: BundlesScreenData) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                BundlesScreen(onBack = {}, onBundleClick = onBundleClick, viewModel = viewModel)
            }
        }
    }

    @Test
    fun dataStateRendersBundleAndClicksThrough() {
        setContent(BundlesScreenData.Data(persistentListOf(bundle), sort = BundleSort.Newest))

        composeTestRule.onNodeWithText(BUNDLE_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(BUNDLE_TITLE).performClick()

        verify(exactly = 1) { onBundleClick(BUNDLE_ID) }
    }

    @Test
    fun tappingSortChipDispatchesSetSort() {
        setContent(BundlesScreenData.Data(persistentListOf(bundle), sort = BundleSort.Newest))

        composeTestRule.onNodeWithText(labels.sortExpiring).performClick()

        verify(exactly = 1) { viewModel.setSort(BundleSort.ExpiringSoon) }
    }

    @Test
    fun emptyStateShowsEmptyMessage() {
        setContent(BundlesScreenData.Data(persistentListOf(), sort = BundleSort.Newest))

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun loadingStateShowsSpinner() {
        setContent(BundlesScreenData.Loading)

        composeTestRule.onNodeWithContentDescription(labels.loading).assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetryAndDispatches() {
        setContent(BundlesScreenData.Error)

        composeTestRule.onNodeWithText(labels.errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { viewModel.load() }
    }

    private data class Labels(
        val sortExpiring: String,
        val empty: String,
        val loading: String,
        val errorMessage: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                sortExpiring = stringResource(Res.string.bundles_sort_expiring),
                empty = stringResource(Res.string.bundles_screen_empty),
                loading = stringResource(Res.string.bundles_screen_loading_indicator),
                errorMessage = stringResource(Res.string.bundles_screen_data_loading_error_msg),
                retry = stringResource(Res.string.bundles_screen_data_loading_error_retry),
            )
        }
    }

    private companion object {
        const val BUNDLE_ID = 1
        const val BUNDLE_TITLE = "Indie Bundle"
    }
}
