package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.Region
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_region_picker_search_hint
import pm.bam.gamedeals.feature.account.generated.resources.account_region_picker_title

/**
 * Device UI coverage for [RegionPickerSheet] — the country-selection bottom sheet. Renders the sheet
 * directly with hoisted state + mockk callbacks (same pattern as GamePeekSheetTest) and asserts the
 * selected-radio state, the continent grouping/ordering, search filtering, and the exact-Country
 * dispatch on tap.
 */
class RegionPickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val onSelect = mockk<(Country) -> Unit>(relaxed = true)
    private val onDismiss = mockk<() -> Unit>(relaxed = true)

    private lateinit var title: String
    private lateinit var searchHint: String

    private fun setContent() {
        composeTestRule.setContent {
            title = stringResource(Res.string.account_region_picker_title)
            searchHint = stringResource(Res.string.account_region_picker_search_hint)
            GameDealsTheme {
                RegionPickerSheet(
                    countries = persistentListOf(US, UK, CANADA),
                    selectedCode = UK.code,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun rendersTitleAndCountryNames() {
        setContent()

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(UK.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(CANADA.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(US.name).assertIsDisplayed()
    }

    @Test
    fun selectedCountryRowIsSelectedOthersAreNot() {
        setContent()

        composeTestRule.onNode(hasText(UK.name) and isRadio).assertIsSelected()
        composeTestRule.onNode(hasText(CANADA.name) and isRadio).assertIsNotSelected()
    }

    @Test
    fun countriesAreGroupedByRegionHeaders() {
        setContent()

        // Continent headers render, and rows follow their region grouping in [Region] order (Americas
        // before Europe), name-sorted within — so the first radio row is Canada, not the selected UK.
        composeTestRule.onNodeWithText(Region.AMERICAS.displayName).assertIsDisplayed()
        composeTestRule.onNodeWithText(Region.EUROPE.displayName).assertIsDisplayed()
        composeTestRule.onAllNodes(isRadio).onFirst().assert(hasText(CANADA.name))
    }

    @Test
    fun searchFiltersToMatchingCountries() {
        setContent()

        composeTestRule.onNodeWithText(searchHint).performTextInput("can")

        composeTestRule.onNodeWithText(CANADA.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(US.name).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(UK.name).assertIsNotDisplayed()
    }

    @Test
    fun tappingCountryDispatchesExactCountry() {
        setContent()

        composeTestRule.onNode(hasText(CANADA.name) and isRadio).performClick()

        verify(exactly = 1) { onSelect(CANADA) }
    }

    private companion object {
        val isRadio: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val US = Country("US", "United States", Region.AMERICAS)
        val UK = Country("GB", "United Kingdom", Region.EUROPE)
        val CANADA = Country("CA", "Canada", Region.AMERICAS)
    }
}
