package pm.bam.gamedeals.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.home.ui.HomeScreen

fun NavGraphBuilder.homeScreen(
    goToGame: (gameId: String) -> Unit,
    goToStore: (storeId: Int) -> Unit,
    goToGiveaway: () -> Unit,
    goToFavourites: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
    goToBundles: () -> Unit,
    goToBundle: (bundleId: Int) -> Unit,
) {
    // Search + Settings now live on the app-shell top bar / overflow (epic #219, Phase 1), so they are
    // no longer Home's responsibility.
    composable<Destination.Home> {
        HomeScreen(
            goToGame = goToGame,
            onViewStoreDeals = { store: Store -> goToStore(store.storeID) },
            onViewGiveaways = goToGiveaway,
            onViewFavourites = goToFavourites,
            onViewBundles = goToBundles,
            onViewBundle = goToBundle,
            goToWeb = { url: String, gameTitle: String -> goToWeb(url, gameTitle) },
            goToGameDetails = goToGameDetails,
            goToGameDetailsByTitle = goToGameDetailsByTitle,
        )
    }
}
