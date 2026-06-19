package pm.bam.gamedeals.feature.webview.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.fullscreenSemiTransparentBackground
import pm.bam.gamedeals.feature.webview.generated.resources.Res
import pm.bam.gamedeals.feature.webview.generated.resources.browser
import pm.bam.gamedeals.feature.webview.generated.resources.webview_screen_navigation_back_button
import pm.bam.gamedeals.feature.webview.generated.resources.webview_screen_navigation_open_in_browser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebView(
    gameTitle: String,
    url: String,
    onBack: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current

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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.webview_screen_navigation_back_button)
                            )
                        }
                    },
                    actions = {
                        if (loading) {
                            CircularProgressIndicator()
                        }
                        IconButton(onClick = { uriHandler.openUri(url) }) {
                            Icon(
                                painter = painterResource(Res.drawable.browser),
                                contentDescription = stringResource(Res.string.webview_screen_navigation_open_in_browser)
                            )
                        }
                    }
                )
            }
        ) { contentPadding: PaddingValues ->
            PlatformWebView(
                url = url,
                onLoadingChange = { loading = it },
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
        }
    }
}

/**
 * Hosts the platform's native web-rendering surface. Android wraps
 * `android.webkit.WebView` in `AndroidView`; iOS wraps `WKWebView` in
 * `UIKitView`. The actuals own load-state lifecycle and surface progress
 * back through [onLoadingChange] — `true` while a navigation is in flight,
 * `false` once it finishes (success) or fails on the main frame.
 */
@Composable
internal expect fun PlatformWebView(
    url: String,
    onLoadingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
