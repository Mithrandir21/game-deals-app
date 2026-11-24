package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.PlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.CollectedGames
import pm.bam.gamedeals.domain.models.YearGame
import pm.bam.gamedeals.domain.models.YearInDeals
import pm.bam.gamedeals.domain.repositories.yearindeals.YearInDealsRepository

/** Each state of the year screen, and sharing from it (matrix 2.4). */
class YearInDealsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** Each load waits for the test to answer it. */
    private class FakeYearRepository : YearInDealsRepository {
        val answers: MutableList<CompletableDeferred<YearInDeals>> = java.util.concurrent.CopyOnWriteArrayList()
        override fun isOffered() = true
        override fun reportYear() = 2026
        override suspend fun getYearInDeals(): YearInDeals = CompletableDeferred<YearInDeals>().also { answers += it }.await()
    }

    private class RecordingActions : PlatformActions {
        val images = mutableListOf<Pair<ByteArray, String>>()
        override fun share(text: String) = Unit
        override suspend fun shareImage(png: ByteArray, text: String) { images += png to text }
        override fun openInApp(url: String) = Unit
        override fun openAppNotificationSettings() = Unit
        override fun openStoreListing(storeId: String) = Unit
    }

    private val year = YearInDeals(
        year = 2026,
        collected = CollectedGames(count = 12, highlights = persistentListOf(YearGame("hades", "Hades"))),
        fullPriceValue = "$340",
        waitlistWins = null,
        longestWait = null,
    )

    private val repository = FakeYearRepository()
    private val actions = RecordingActions()

    private fun show() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPlatformActions provides actions) {
                GameDealsTheme { YearInDealsScreen(onBack = {}, viewModel = YearInDealsViewModel(repository)) }
            }
        }
        composeTestRule.waitUntil(5_000) { repository.answers.isNotEmpty() }
    }

    private fun answer(index: Int, result: Result<YearInDeals>) {
        composeTestRule.runOnIdle {
            result.fold({ repository.answers[index].complete(it) }, { repository.answers[index].completeExceptionally(it) })
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun a_failed_load_says_so_and_try_again_loads_the_year() {
        show()
        composeTestRule.onNodeWithText("Year in deals").assertIsDisplayed()
        answer(0, Result.failure(IllegalStateException("offline")))

        composeTestRule.onNodeWithText("Your year couldn't be loaded. Check your connection and try again.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").performClick()
        composeTestRule.waitUntil(5_000) { repository.answers.size == 2 }
        answer(1, Result.success(year))

        composeTestRule.onNodeWithText("12 games collected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun an_empty_year_says_how_to_fill_the_card() {
        show()
        answer(0, Result.success(YearInDeals(2026, null, null, null, null)))

        composeTestRule.onNodeWithText(
            "Not much to show for 2026 yet. Add games to your waitlist and collection on IsThereAnyDeal, and your card fills in.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Share my year").assertDoesNotExist()
    }

    @Test
    fun a_ready_year_shares_the_card_as_an_image_with_its_line_of_text() {
        show()
        answer(0, Result.success(year))

        composeTestRule.onNodeWithText("Share my year").performScrollTo().performClick()
        composeTestRule.waitUntil(5_000) { actions.images.isNotEmpty() }

        val (png, text) = actions.images.single()
        assertTrue("a real image", png.size > 100)
        assertEquals("My 2026 in game deals: 12 games collected. Made with Game Deals https://gamedeals.bam.pm/app", text)
    }
}
