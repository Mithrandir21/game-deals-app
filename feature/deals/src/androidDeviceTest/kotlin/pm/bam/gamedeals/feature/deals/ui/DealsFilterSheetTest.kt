package pm.bam.gamedeals.feature.deals.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.feature.deals.generated.resources.Res
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_button
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_discount
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_sort
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_sheet_title
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel.DealsScreenData
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel.SearchResultsState

/**
 * Locks the accessibility fix that marks the filter sheet's title and each section title as headings,
 * so TalkBack's heading navigation can jump between filter groups instead of skipping over them.
 */
class DealsFilterSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: DealsViewModel = mockk(relaxed = true)

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private data class Labels(val filterButton: String, val sheetTitle: String, val sort: String, val discount: String)

    private lateinit var labels: Labels

    private fun setup() {
        every { viewModel.uiState } returns MutableStateFlow(DealsScreenData(status = DealsScreenData.Status.DATA))
        every { viewModel.waitlistIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.collectionIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.ignoredIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.stores } returns MutableStateFlow(persistentListOf())
        every { viewModel.selectedShops } returns MutableStateFlow(persistentSetOf())
        every { viewModel.filter } returns MutableStateFlow(DealsFilter())
        every { viewModel.searchQuery } returns MutableStateFlow("")
        every { viewModel.searchResults } returns MutableStateFlow(SearchResultsState.Idle)
        every { viewModel.gamePeek } returns MutableStateFlow(null)
        every { viewModel.discoverEnabled } returns MutableStateFlow(false)
        every { viewModel.events } returns MutableSharedFlow()

        composeTestRule.setContent {
            labels = Labels(
                filterButton = stringResource(Res.string.deals_filter_button),
                sheetTitle = stringResource(Res.string.deals_filter_sheet_title),
                sort = stringResource(Res.string.deals_filter_section_sort),
                discount = stringResource(Res.string.deals_filter_section_discount),
            )
            GameDealsTheme {
                DealsScreen(goToWeb = { _, _ -> }, goToGame = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun filterSheetTitleAndSectionTitlesAreHeadings() {
        setup()

        composeTestRule.onNodeWithText(labels.filterButton).performClick()

        composeTestRule.onNodeWithText(labels.sheetTitle).assert(isHeading)
        composeTestRule.onNodeWithText(labels.sort).assert(isHeading)
        composeTestRule.onNodeWithText(labels.discount).assert(isHeading)
    }
}
