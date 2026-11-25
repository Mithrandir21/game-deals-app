package pm.bam.gamedeals.common.ui.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme

/**
 * Device UI coverage for the shared toolbar's notification bell. The shell takes hoisted state + lambdas,
 * so it's driven directly (no ViewModel/Koin). The bell is matched by its accessibility content description,
 * which merges the badge tally into one phrase — asserting on it also guards the TalkBack announcement.
 */
class AppShellScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var opened = 0

    private fun setContent(
        showNotifications: Boolean,
        unreadCount: Int = 0,
        selectedTab: TopLevelDestination = TopLevelDestination.HOME,
        activeSearchQuery: String? = null,
    ) {
        composeTestRule.setContent {
            GameDealsTheme {
                GameDealsAppShell(
                    selectedTab = selectedTab,
                    showTopBar = true,
                    showBottomBar = true,
                    onSelectTab = {},
                    activeSearchQuery = activeSearchQuery,
                    onSearchSubmit = {},
                    onSearchClosed = {},
                    showNotifications = showNotifications,
                    notificationUnreadCount = unreadCount,
                    onOpenNotifications = { opened++ },
                    content = { Text("Content") },
                )
            }
        }
    }

    @Test
    fun bellIsAbsentWhenNotificationsAreHidden() {
        setContent(showNotifications = false, unreadCount = 3)

        composeTestRule.onNodeWithContentDescription("Notifications", substring = true).assertDoesNotExist()
    }

    @Test
    fun bellIsShownUnbadgedWhenThereIsNothingUnread() {
        setContent(showNotifications = true, unreadCount = 0)

        composeTestRule.onNodeWithContentDescription("Notifications").assertIsDisplayed()
        // No badge, so no tally rendered anywhere on the bar.
        composeTestRule.onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun bellCarriesTheUnreadTallyInItsBadgeAndDescription() {
        setContent(showNotifications = true, unreadCount = 3)

        composeTestRule.onNodeWithContentDescription("Notifications, 3 unread games").assertIsDisplayed()
    }

    @Test
    fun tappingTheBellOpensNotifications() {
        setContent(showNotifications = true, unreadCount = 3)

        composeTestRule.onNodeWithContentDescription("Notifications, 3 unread games").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun bellIsHiddenWhileTheSearchFieldIsExpanded() {
        // An active query on the Deals tab expands the inline search field, which takes over the bar.
        setContent(
            showNotifications = true,
            unreadCount = 3,
            selectedTab = TopLevelDestination.DEALS,
            activeSearchQuery = "hades",
        )

        composeTestRule.onNodeWithContentDescription("Notifications", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Close search").assertIsDisplayed()
    }

    @Test
    fun aTabScreensMessageSitsAboveTheBottomBar() {
        // Tab content runs underneath the bottom bar (so the bar can slide away on scroll). A tab's
        // snackbar was drawn at the very bottom, hidden behind the bar: Deals' "Couldn't load deals"
        // with its Retry never showed.
        composeTestRule.setContent {
            GameDealsTheme {
                GameDealsAppShell(
                    selectedTab = TopLevelDestination.DEALS,
                    showTopBar = true,
                    showBottomBar = true,
                    onSelectTab = {},
                    activeSearchQuery = null,
                    onSearchSubmit = {},
                    onSearchClosed = {},
                    content = { padding ->
                        // The shape every tab screen uses: an inner Scaffold that only hosts the snackbar.
                        val hostState = remember { SnackbarHostState() }
                        LaunchedEffect(hostState) { hostState.showSnackbar(MESSAGE, actionLabel = "Retry") }
                        Scaffold(
                            modifier = Modifier.padding(padding),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            snackbarHost = { TabSnackbarHost(hostState) },
                        ) { }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText(MESSAGE).assertIsDisplayed()
        val messageBottom = composeTestRule.onNodeWithText(MESSAGE).getBoundsInRoot().bottom
        val barItemTop = composeTestRule.onNode(hasText("Home") and isSelectable()).getBoundsInRoot().top
        assertTrue("message bottom $messageBottom is below the bar's top $barItemTop", messageBottom <= barItemTop)
    }

    @Test
    fun accountTabNoLongerCarriesItsOwnBadge() {
        // The unread tally lives on the bell only; the Account tab icon must stay clean.
        setContent(showNotifications = true, unreadCount = 3)

        composeTestRule.onNodeWithContentDescription("unread notifications", substring = true).assertDoesNotExist()
    }

    private companion object {
        const val MESSAGE = "Couldn't load deals"
    }
}
