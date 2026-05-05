package pm.bam.gamedeals.feature.webview.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.webview.ui.WebView

fun NavGraphBuilder.webViewScreen(
    onBack: () -> Unit
) {
    composable<Destination.WebView> { entry ->
        val args = entry.toRoute<Destination.WebView>()
        WebView(
            url = args.url,
            gameTitle = args.gameTitle,
            onBack = onBack
        )
    }
}
