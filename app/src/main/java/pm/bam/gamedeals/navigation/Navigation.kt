package pm.bam.gamedeals.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import pm.bam.gamedeals.common.navigation.Destination


/**
 * Models the navigation actions in the app.
 *
 * All navigation calls use type-safe [Destination] instances rather than string routes,
 * so that argument types and names are checked at compile time.
 */
internal class NavigationActions(private val navController: NavHostController) {

    fun navigateHome() = navigateTopLevel(Destination.Home)

    /**
     * Navigate to a top-level (bottom-nav tab) [destination] with the standard tab back-stack
     * behaviour (epic #219): pop up to the graph's start destination saving state, single-top, and
     * restore the tab's previously saved state. Used by the [pm.bam.gamedeals.common.ui.shell]
     * `NavigationBar` so re-selecting a tab restores its scroll/stack rather than stacking copies.
     */
    fun navigateTopLevel(destination: Destination) {
        navController.navigate(destination) {
            // Pop up to the start destination of the graph to avoid building up a large stack of destinations on the back stack as users select items
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            // Avoid multiple copies of the same destination when re-selecting the same item
            launchSingleTop = true
            // Restore state when re-selecting a previously selected item
            restoreState = true
        }
    }

    fun navigateToStore(storeId: Int) {
        navController.navigate(Destination.Store(storeId)) {
            restoreState = storeId == 0
        }
    }

    fun navigateToGame(gameId: String) {
        navController.navigate(Destination.Game(gameId)) {
            restoreState = gameId.isEmpty()
        }
    }

    fun navigateToGameDetails(steamAppId: Int, title: String? = null) {
        navController.navigate(Destination.GameDetails(steamAppId, title))
    }

    fun navigateToGameDetailsByTitle(title: String) {
        navController.navigate(Destination.GameDetailsByTitle(title))
    }

    fun navigateToSearch() {
        navController.navigate(Destination.Search()) {
            restoreState = true
        }
    }

    fun navigateToGiveaways() {
        navController.navigate(Destination.Giveaways) {
            restoreState = true
        }
    }

    fun navigateToFavourites() {
        navController.navigate(Destination.Favourites) {
            restoreState = true
        }
    }

    fun navigateToSettings() {
        navController.navigate(Destination.Settings) {
            restoreState = true
        }
    }

    fun navigateToBundles() {
        navController.navigate(Destination.Bundles) {
            restoreState = true
        }
    }

    fun navigateToBundleDetail(bundleId: Int) {
        navController.navigate(Destination.BundleDetail(bundleId))
    }

}
