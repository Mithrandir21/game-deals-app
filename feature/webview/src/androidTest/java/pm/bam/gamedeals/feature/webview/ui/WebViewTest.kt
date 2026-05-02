package pm.bam.gamedeals.feature.webview.ui

import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.feature.webview.R

class WebViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun webView_displaysTitleAndHandlesBack() {
        val gameTitle = "Test Game"
        var backClicked = false

        composeTestRule.setContent {
            WebView(
                gameTitle = gameTitle,
                url = "https://example.com",
                onBack = { backClicked = true }
            )
        }

        // Verify title is displayed
        composeTestRule.onNodeWithText(gameTitle).assertIsDisplayed()

        // Verify back button works
        val backDescription = context.getString(R.string.webview_screen_navigation_back_button)
        composeTestRule.onNodeWithContentDescription(backDescription).performClick()
        assert(backClicked)
    }

    @Test
    fun webView_handlesOpenInBrowser() {
        val url = "https://example.com"
        val uriHandler = mockk<UriHandler>(relaxed = true)

        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalUriHandler provides uriHandler
            ) {
                WebView(
                    gameTitle = "Test Game",
                    url = url,
                    onBack = {}
                )
            }
        }

        // Click "Open in Browser" button
        val openInBrowserDescription = context.getString(R.string.webview_screen_navigation_open_in_browser)
        composeTestRule.onNodeWithContentDescription(openInBrowserDescription).performClick()

        // Verify URI handler was called
        verify { uriHandler.openUri(url) }
    }
}
