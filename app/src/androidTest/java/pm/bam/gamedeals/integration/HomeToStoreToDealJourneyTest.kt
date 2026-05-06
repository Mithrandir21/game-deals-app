package pm.bam.gamedeals.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
        // 1. Home loads — wait for store banner for store 1 (Steam) to appear.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("HomeScreenStoreBannerTag1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Tap "View All" for store 1.
        composeRule.onNodeWithTag("HomeScreenViewAllButtonTag1", useUnmergedTree = true)
            .performClick()

        // 3. Store screen — assert top app bar with store name.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("StoreTopBar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Steam").assertIsDisplayed()

        // 4. Wait for paged deals to render, then tap the first one (dealID = abc123 = Portal 2).
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("DealRowabc123", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("DealRowabc123", useUnmergedTree = true).performClick()

        // 5. Deal bottom sheet — assert game name from deal_abc123.json appears in the sheet header.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("StoreDataGameName", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("StoreDataGameName", useUnmergedTree = true)
            .assertTextContains("Portal 2")
    }
}
