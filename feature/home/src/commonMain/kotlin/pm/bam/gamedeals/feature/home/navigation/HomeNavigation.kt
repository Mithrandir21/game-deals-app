package pm.bam.gamedeals.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.home.ui.HomeScreen

fun NavGraphBuilder.homeScreen(
    goToGame: (gameId: String) -> Unit,
    goToGiveaway: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
    goToBundles: () -> Unit,
    goToBundle: (bundleId: Int) -> Unit,
) {
    // Search + Settings live on the app-shell top bar / overflow (epic #219, Phase 1). The per-store
    // strips moved to the Deals tab in Phase 4, so Home no longer navigates to the Store screen.
    composable<Destination.Home> {
        HomeScreen(
            goToGame = goToGame,
            onViewGiveaways = goToGiveaway,
            onViewBundles = goToBundles,
            onViewBundle = goToBundle,
            goToWeb = { url: String, gameTitle: String -> goToWeb(url, gameTitle) },
            goToGameDetails = goToGameDetails,
            goToGameDetailsByTitle = goToGameDetailsByTitle,
        )
    }
}
