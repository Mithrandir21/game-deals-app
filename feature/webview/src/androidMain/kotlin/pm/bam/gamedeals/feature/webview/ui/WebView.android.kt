@file:Suppress("MatchingDeclarationName")

package pm.bam.gamedeals.feature.webview.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun PlatformWebView(
    url: String,
    onLoadingChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    // Latest callback so the captured `webViewClient` keeps emitting against the
    // current `loading` setter even if the parent recomposes with a new lambda.
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)

    // Track the last URL we loaded so `update` only reloads when the `url` argument actually changes.
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    val webViewClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                currentOnLoadingChange(true)
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                currentOnLoadingChange(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                currentOnLoadingChange(false)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) currentOnLoadingChange(false)
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) currentOnLoadingChange(false)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                this.webViewClient = webViewClient
                loadUrl(url)
                lastLoadedUrl = url
            }
        },
        update = { webView ->
            if (lastLoadedUrl != url) {
                webView.loadUrl(url)
                lastLoadedUrl = url
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.loadUrl("about:blank")
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
    )
}
