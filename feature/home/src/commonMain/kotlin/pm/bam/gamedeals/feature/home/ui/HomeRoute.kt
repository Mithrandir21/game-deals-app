package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable

/**
 * Public entry point into the home feature. Wraps the internal [HomeScreen] so
 * platform consumers (Android nav graph, iOS root composable) can render it
 * without exposing the underlying internals.
 */
@Composable
fun HomeRoute(
    goToGame: (gameId: String) -> Unit,
    goToGameByTitle: (title: String) -> Unit,
    onViewWaitlist: () -> Unit,
    onViewCollection: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    HomeScreen(
        goToGame = goToGame,
        goToGameByTitle = goToGameByTitle,
        onViewWaitlist = onViewWaitlist,
        onViewCollection = onViewCollection,
        onViewBundles = onViewBundles,
        onViewBundle = onViewBundle,
        goToWeb = goToWeb,
    )
}
