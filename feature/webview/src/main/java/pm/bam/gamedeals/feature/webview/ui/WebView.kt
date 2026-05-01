package pm.bam.gamedeals.feature.webview.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.theme.fullscreenSemiTransparentBackground
import pm.bam.gamedeals.feature.webview.R

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebView(
    gameTitle: String,
    url: String,
    onBack: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current

    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.fullscreenSemiTransparentBackground()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        title = { Text(text = gameTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { onBack() }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.webview_screen_navigation_back_button)
                                )
                            }
                        },
                        actions = {
                            if (loading) {
                                CircularProgressIndicator()
                            }
                            IconButton(onClick = { uriHandler.openUri(url) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.browser),
                                    contentDescription = stringResource(id = R.string.webview_screen_navigation_open_in_browser)
                                )
                            }
                        }
                    )
                }
            ) { contentPadding: PaddingValues ->
                // Track the last URL we loaded so `update` only reloads when the
                // `url` argument actually changes. Without this guard, every parent
                // recomposition (e.g. `loading` toggling) would call `loadUrl(url)`
                // again and snap the WebView back to the original URL, breaking
                // intra-WebView navigation. See issue #34.
                var lastLoadedUrl by remember { mutableStateOf<String?>(null) }
                AndroidView(
                    modifier = Modifier.padding(contentPadding),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            this.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    loading = true
                                    return super.shouldOverrideUrlLoading(view, request)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    loading = false
                                }
                            }
                            loadUrl(url)
                            lastLoadedUrl = url
                        }
                    },
                    update = { webView ->
                        if (lastLoadedUrl != url) {
                            webView.loadUrl(url)
                            lastLoadedUrl = url
                        }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun FiltersPreview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.fullscreenSemiTransparentBackground()) {
            WebView(gameTitle = "Game Title", url = "www.google.com", onBack = {})
        }
    }
}