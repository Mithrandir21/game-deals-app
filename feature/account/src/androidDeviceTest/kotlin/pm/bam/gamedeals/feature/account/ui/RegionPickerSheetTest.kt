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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.REST_OF_WORLD
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_region_picker_title

/**
 * Device UI coverage for [RegionPickerSheet] — the price-region bottom sheet. Renders the sheet directly
 * with hoisted state + mockk callbacks (same pattern as GamePeekSheetTest) and asserts the rows with their
 * currencies, the selected-radio state (by region id, so "Rest of world" isn't confused with the US whose
 * prices it shares), the given order, and the exact-region dispatch on tap.
 */
class RegionPickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val onSelect = mockk<(Country) -> Unit>(relaxed = true)
    private val onDismiss = mockk<() -> Unit>(relaxed = true)

    private lateinit var title: String

    private fun setContent(selectedId: String? = UK.id) {
        composeTestRule.setContent {
            title = stringResource(Res.string.account_region_picker_title)
            GameDealsTheme {
                RegionPickerSheet(
                    countries = persistentListOf(CANADA, UK, US, REST_OF_WORLD),
                    selectedId = selectedId,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun rendersTitleAndRegionsWithTheirCurrencies() {
        setContent()

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(UK.name).assertIsDisplayed()
        composeTestRule.onNodeWithText("GBP").assertIsDisplayed()
        composeTestRule.onNodeWithText(REST_OF_WORLD.name).assertIsDisplayed()
        composeTestRule.onNodeWithText("US prices").assertIsDisplayed()
    }

    @Test
    fun selectedRegionRowIsSelectedOthersAreNot() {
        setContent()

        composeTestRule.onNode(hasText(UK.name) and isRadio).assertIsSelected()
        composeTestRule.onNode(hasText(CANADA.name) and isRadio).assertIsNotSelected()
    }

    @Test
    fun theUsAndRestOfWorldAreSelectedApart() {
        setContent(selectedId = US.id)

        composeTestRule.onNode(hasText(US.name) and isRadio).assertIsSelected()
        composeTestRule.onNode(hasText(REST_OF_WORLD.name) and isRadio).assertIsNotSelected()
    }

    @Test
    fun rowsKeepTheGivenOrder() {
        setContent()

        composeTestRule.onAllNodes(isRadio).onFirst().assert(hasText(CANADA.name))
    }

    @Test
    fun tappingRegionDispatchesExactRegion() {
        setContent()

        composeTestRule.onNode(hasText(REST_OF_WORLD.name) and isRadio).performClick()

        verify(exactly = 1) { onSelect(REST_OF_WORLD) }
    }

    @Test
    fun scrollingTheListUpAndDownKeepsTheSheetOpen() {
        setContent()

        // The regions fit on most phones, so the list can't scroll; these drags used to pull the whole sheet
        // down and close it on release.
        val row = composeTestRule.onNode(hasText(CANADA.name) and isRadio)
        row.performTouchInput { swipeUp(startY = centerY, endY = centerY - 400f, durationMillis = 600) }
        row.performTouchInput { swipeDown(startY = centerY, endY = centerY + 400f, durationMillis = 600) }
        composeTestRule.waitForIdle()

        verify(exactly = 0) { onDismiss() }
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    private companion object {
        val isRadio: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val US = Country("US", "United States", "USD")
        val UK = Country("GB", "United Kingdom", "GBP")
        val CANADA = Country("CA", "Canada", "CAD")
    }
}
