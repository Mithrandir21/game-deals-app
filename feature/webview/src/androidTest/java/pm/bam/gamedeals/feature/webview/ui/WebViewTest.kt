package pm.bam.gamedeals.feature.webview.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.feature.webview.R

class WebViewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

        val mainFrameRequest = mockk<WebResourceRequest>(relaxed = true) {
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

        val mainFrameRequest = mockk<WebResourceRequest>(relaxed = true) {
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

        val subFrameRequest = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns false
        }
        val error = mockk<WebResourceError>(relaxed = true)
        val errorResponse = mockk<WebResourceResponse>(relaxed = true)

        invokeOnWebViewClient { client, view ->
            // Pin `loading = true` before exercising the sub-frame error callbacks. The
            // real WebView is loading https://example.com on the emulator and may have
            // already fired onPageFinished (which clears loading) by the time we reach
            // here — that would mask the actual assertion we want to make: sub-frame
            // failures themselves do NOT clear loading.
            client.onPageStarted(view, "https://example.com", null)
            client.onReceivedError(view, subFrameRequest, error)
            client.onReceivedHttpError(view, subFrameRequest, errorResponse)
        }

        composeTestRule.waitForIdle()

        // Sub-frame failures must not kill the spinner.
        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()
    }

    /**
     * Locates the [WebView] inside the activity's view tree and invokes the supplied
     * action against its [WebViewClient] on the main thread.
     *
     * Walks the view hierarchy directly rather than going through Espresso's `onView`,
     * which races the AndroidView attachment + window-focus gates and fails with
     * `RootViewWithoutFocusException` against `createComposeRule()` on the Pixel 3 API 35
     * emulator. `composeTestRule.waitForIdle()` settles the Compose tree first; the WebView
     * is then guaranteed to be attached as a child of the AndroidView's host view.
     */
    private fun invokeOnWebViewClient(action: (WebViewClient, WebView) -> Unit) {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val webView = activity.window.decorView.findFirstWebView()
            ?: error("WebView not found in the composed activity")
        composeTestRule.runOnUiThread {
            action(webView.webViewClient, webView)
        }
        composeTestRule.waitForIdle()
    }

    private fun View.findFirstWebView(): WebView? {
        if (this is WebView) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).findFirstWebView()?.let { return it }
            }
        }
        return null
    }
}
