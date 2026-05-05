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

/**
 * iOS counterpart of the Android `WebView` `AndroidView` host. Uses Compose
 * Multiplatform's `UIKitView` to bridge a `WKWebView` into the composition,
 * with a `WKNavigationDelegate` that surfaces main-frame load events through
 * [onLoadingChange]. Sub-frame errors do NOT clear loading — matches the
 * Android side's `request?.isForMainFrame == true` check.
 *
 * `loadRequest` runs in `update` (not `factory`) so the WKWebView is in the
 * UIKit hierarchy with a non-zero frame before the navigation kicks off —
 * loading into a freshly-allocated zero-frame WKWebView paints nothing on
 * iOS even after the frame is later assigned. `lastLoadedUrl` mirrors the
 * Android-side guard so recompositions don't redundantly reload.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformWebView(
    url: String,
    onLoadingChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    // Stable callback reference — the WKWebViewDelegate holds it across recompositions.
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
                // Keep the view opaque (default) and force a white background so
                // there's no dark flash before the page paints. The previous
                // `setOpaque(false)` workaround inverted this — it made the
                // WKWebView's layer transparent, so the dark Compose host bled
                // through and the page never appeared to render.
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

/**
 * Bridges WKWebView's main-frame navigation lifecycle to the Compose
 * `loading` state. WKWebView only delivers main-frame navigation events
 * to the delegate (sub-frame loads use a separate decision-handler API
 * we don't override), so the Android `isForMainFrame` filter is implicit
 * in iOS — every callback here is main-frame.
 */
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
