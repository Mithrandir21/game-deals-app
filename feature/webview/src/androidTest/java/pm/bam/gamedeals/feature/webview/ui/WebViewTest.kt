package pm.bam.gamedeals.feature.webview.ui

import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matcher
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

    @Test
    fun webView_clearsLoadingOnMainFrameReceivedError() {
        composeTestRule.setContent {
            WebView(
                gameTitle = "Test Game",
                url = "https://example.com",
                onBack = {}
            )
        }

        // The spinner is initially displayed because `loading` defaults to true.
        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()

        val mainFrameRequest = mockk<WebResourceRequest> {
            every { isForMainFrame } returns true
        }
        val error = mockk<WebResourceError>(relaxed = true)

        invokeOnWebViewClient { client, view ->
            client.onReceivedError(view, mainFrameRequest, error)
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertDoesNotExist()
    }

    @Test
    fun webView_clearsLoadingOnMainFrameReceivedHttpError() {
        composeTestRule.setContent {
            WebView(
                gameTitle = "Test Game",
                url = "https://example.com",
                onBack = {}
            )
        }

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()

        val mainFrameRequest = mockk<WebResourceRequest> {
            every { isForMainFrame } returns true
        }
        val errorResponse = mockk<WebResourceResponse>(relaxed = true)

        invokeOnWebViewClient { client, view ->
            client.onReceivedHttpError(view, mainFrameRequest, errorResponse)
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertDoesNotExist()
    }

    @Test
    fun webView_keepsLoadingOnSubFrameReceivedError() {
        composeTestRule.setContent {
            WebView(
                gameTitle = "Test Game",
                url = "https://example.com",
                onBack = {}
            )
        }

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()

        val subFrameRequest = mockk<WebResourceRequest> {
            every { isForMainFrame } returns false
        }
        val error = mockk<WebResourceError>(relaxed = true)
        val errorResponse = mockk<WebResourceResponse>(relaxed = true)

        invokeOnWebViewClient { client, view ->
            client.onReceivedError(view, subFrameRequest, error)
            client.onReceivedHttpError(view, subFrameRequest, errorResponse)
        }

        composeTestRule.waitForIdle()

        // Sub-frame failures must not kill the spinner.
        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()
    }

    /**
     * Locates the [WebView] inside the composed tree via Espresso and invokes the supplied
     * action against its [WebViewClient]. The action runs on the main thread, which is the
     * same thread that the real WebView would use to deliver these callbacks.
     */
    private fun invokeOnWebViewClient(action: (WebViewClient, WebView) -> Unit) {
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)

            override fun getDescription(): String = "Invoke a WebViewClient callback"

            override fun perform(uiController: UiController, view: View) {
                val webView = view as WebView
                action(webView.webViewClient, webView)
                uiController.loopMainThreadUntilIdle()
            }
        })
    }
}
