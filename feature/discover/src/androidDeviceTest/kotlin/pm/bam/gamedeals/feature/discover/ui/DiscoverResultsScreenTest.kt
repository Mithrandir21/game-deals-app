package pm.bam.gamedeals.feature.discover.ui

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
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.feature.discover.generated.resources.Res
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_empty
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_error
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_retry
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_row_description
import pm.bam.gamedeals.feature.discover.ui.DiscoverResultsViewModel.ResultsScreenData

/**
 * Device UI test for [DiscoverResultsScreen] via a mocked [DiscoverResultsViewModel]. Uses an
 * unpriced ("tracked, no deal") result so the shared `DealListRow` content description equals the
 * caller's exactly (no badge/state suffixes), giving a stable click target.
 */
class DiscoverResultsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: DiscoverResultsViewModel = mockk(relaxed = true)

    private lateinit var labels: Labels

    private val result = TagDiscoveryResult(
        igdbId = 1L,
        gameId = GAME_ID,
        title = GAME_TITLE,
        coverImageUrl = null,
        price = null,
    )

    private fun setContent(state: ResultsScreenData) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.waitlistIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.collectionIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.ignoredIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { viewModel.storeIconsByName } returns MutableStateFlow<Map<String, String?>>(emptyMap())
        every { viewModel.gamePeek } returns MutableStateFlow<GamePeekSheetData?>(null)
        every { viewModel.events } returns MutableSharedFlow()
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                DiscoverResultsScreen(onBack = {}, goToGame = {}, goToWeb = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun dataStateRendersResultRowAndPeeksOnTap() {
        setContent(ResultsScreenData(status = ResultsScreenData.Status.DATA, results = persistentListOf(result)))

        composeTestRule.onNodeWithContentDescription(labels.rowDescription).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(labels.rowDescription).performClick()

        verify(exactly = 1) { viewModel.peekGame(GAME_ID, GAME_TITLE, null) }
    }

    @Test
    fun emptyStateShowsEmptyMessage() {
        setContent(ResultsScreenData(status = ResultsScreenData.Status.EMPTY))

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetryAndDispatches() {
        setContent(ResultsScreenData(status = ResultsScreenData.Status.ERROR))

        composeTestRule.onNodeWithText(labels.error).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { viewModel.retry() }
    }

    private data class Labels(
        val rowDescription: String,
        val empty: String,
        val error: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                rowDescription = stringResource(Res.string.discover_results_row_description, GAME_TITLE),
                empty = stringResource(Res.string.discover_results_empty),
                error = stringResource(Res.string.discover_results_error),
                retry = stringResource(Res.string.discover_results_retry),
            )
        }
    }

    private companion object {
        const val GAME_ID = "itad-1"
        const val GAME_TITLE = "Hades"
    }
}
