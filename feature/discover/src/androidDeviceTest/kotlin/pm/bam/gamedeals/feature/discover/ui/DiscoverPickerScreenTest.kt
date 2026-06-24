package pm.bam.gamedeals.feature.discover.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.feature.discover.generated.resources.Res
import pm.bam.gamedeals.feature.discover.generated.resources.discover_dimension_genre
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_clear
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_error
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_retry
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_show_results
import pm.bam.gamedeals.feature.discover.ui.DiscoverPickerViewModel.PickerState

/**
 * Device UI test for [DiscoverPickerScreen] — drives the tag picker through a mocked
 * [DiscoverPickerViewModel] (same pattern as the Store/Deals screen tests) and asserts both render
 * state and that chip / clear / show-results / retry interactions dispatch to the ViewModel.
 */
class DiscoverPickerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: DiscoverPickerViewModel = mockk(relaxed = true)
    private val onShowResults = mockk<(IgdbTagFilter) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private val genreTag = IgdbTag(IgdbTagDimension.Genre, 12L, GENRE_TAG_NAME, "shooter")
    private val genreKey = TagKey(IgdbTagDimension.Genre, 12L)

    private fun setContent(state: PickerState) {
        every { viewModel.uiState } returns MutableStateFlow(state)
        every { viewModel.currentFilter() } returns IgdbTagFilter()
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                DiscoverPickerScreen(onBack = {}, onShowResults = onShowResults, viewModel = viewModel)
            }
        }
    }

    private fun ready(selected: Set<TagKey> = emptySet()) = PickerState.Ready(
        groups = persistentListOf(TagGroup(IgdbTagDimension.Genre, persistentListOf(genreTag))),
        selected = if (selected.isEmpty()) persistentSetOf() else persistentSetOf(*selected.toTypedArray()),
    )

    @Test
    fun readyStateRendersDimensionHeaderAndTagChip() {
        setContent(ready())

        composeTestRule.onNodeWithText(labels.genreHeader).assertIsDisplayed()
        composeTestRule.onNodeWithText(GENRE_TAG_NAME).assertIsDisplayed()
    }

    @Test
    fun tappingTagChipDispatchesToggleTag() {
        setContent(ready())

        composeTestRule.onNodeWithText(GENRE_TAG_NAME).performClick()

        verify(exactly = 1) { viewModel.toggleTag(genreKey) }
    }

    @Test
    fun showResultsWithSelectionDispatchesFilter() {
        setContent(ready(selected = setOf(genreKey)))

        composeTestRule.onNodeWithText(labels.showResults, substring = true).performClick()

        verify(exactly = 1) { onShowResults(any()) }
    }

    @Test
    fun clearActionVisibleWithSelectionDispatchesClear() {
        setContent(ready(selected = setOf(genreKey)))

        composeTestRule.onNodeWithText(labels.clear).performClick()

        verify(exactly = 1) { viewModel.clear() }
    }

    @Test
    fun errorStateShowsRetryAndDispatches() {
        setContent(PickerState.Error)

        composeTestRule.onNodeWithText(labels.error).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.retry).performClick()

        verify(exactly = 1) { viewModel.retry() }
    }

    private data class Labels(
        val genreHeader: String,
        val showResults: String,
        val clear: String,
        val error: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                genreHeader = stringResource(Res.string.discover_dimension_genre),
                showResults = stringResource(Res.string.discover_picker_show_results),
                clear = stringResource(Res.string.discover_picker_clear),
                error = stringResource(Res.string.discover_picker_error),
                retry = stringResource(Res.string.discover_picker_retry),
            )
        }
    }

    private companion object {
        const val GENRE_TAG_NAME = "Shooter"
    }
}
