package pm.bam.gamedeals.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.MainActivity

/**
 * End-to-end journey: Home → Store → Deal bottom sheet. Runs against [TestGameDealsApplication]
 * (configured in `:app/build.gradle.kts` via `KoinTestRunner`) which loads the production
 * Koin graph plus `test*OverridesModule`s — replacing the CheapShark + GamerPower HttpClients
 * with Ktor MockEngine clients backed by JSON fixtures, and the Room DB with an in-memory one.
 */
class HomeToStoreToDealJourneyTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun home_to_store_to_deal_happy_path() {
        // 1. Home loads — wait for the Steam store banner. The AsyncImage exposes a
        //    content description of "Steam Store banner" via home_screen_store_banner.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("Steam Store banner")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Tap "View All Steam deals" — the View All button text from
        //    home_screen_all_store_deals_label formatted with the store name.
        composeRule.onNodeWithText("View All Steam deals").performClick()

        // 3. Store screen — wait for the store name in the top app bar.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Steam").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Steam").assertIsDisplayed()

        // 4. Wait for paged deals to render, then tap the first one (dealID = abc123 = Portal 2).
        //    StoreScreen's DealRow exposes a content description like "Deal: Portal 2, $1.99".
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("Deal: Portal 2", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Deal: Portal 2", substring = true).performClick()

        // 5. Deal bottom sheet — wait for the "Go to Deal" button (unique to DealBottomSheet's
        //    DealDetailsData state) and assert the formatted header shows Portal 2's Steam price.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Go to Deal").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Go to Deal").assertIsDisplayed()
        composeRule.onNodeWithText("Steam - $1.99").assertIsDisplayed()
    }
}
