@file:Suppress("MatchingDeclarationName")

package pm.bam.gamedeals.feature.webview.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject
import pm.bam.gamedeals.logging.implementations.iosLog

// `loadRequest` runs in `update`, not `factory`: WKWebView paints nothing if the navigation
// kicks off before it has a non-zero frame.
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformWebView(
    url: String,
    onLoadingChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)

    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    val navigationDelegate = remember {
        WebViewNavigationDelegate { isLoading -> currentOnLoadingChange(isLoading) }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView().apply {
                this.navigationDelegate = navigationDelegate
                setBackgroundColor(UIColor.whiteColor)
                scrollView.setBackgroundColor(UIColor.whiteColor)
            }
        },
        update = { webView ->
            if (lastLoadedUrl != url) {
                val nsUrl = NSURL.URLWithString(url)
                if (nsUrl != null) {
                    webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
                } else {
                    iosLog("[WebView] NSURL.URLWithString returned nil for url=$url — skipping load")
                    currentOnLoadingChange(false)
                }
                lastLoadedUrl = url
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.navigationDelegate = null
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class WebViewNavigationDelegate(
    private val onLoadingChange: (Boolean) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        onLoadingChange(true)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onLoadingChange(false)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        onLoadingChange(false)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        onLoadingChange(false)
    }
}
