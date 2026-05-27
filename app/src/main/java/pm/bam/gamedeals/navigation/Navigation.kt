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

    fun navigateHome() {
        navController.navigate(Destination.Home) {
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

    fun navigateToGame(gameId: Int) {
        navController.navigate(Destination.Game(gameId)) {
            restoreState = gameId == 0
        }
    }

    fun navigateToGameDetails(steamAppId: Int) {
        navController.navigate(Destination.GameDetails(steamAppId))
    }

    fun navigateToGameDetailsByTitle(title: String) {
        navController.navigate(Destination.GameDetailsByTitle(title))
    }

    fun navigateToSearch() {
        navController.navigate(Destination.Search()) {
            restoreState = true
        }
    }

    fun navigateToWeb(url: String, gameTitle: String) {
        navController.navigate(Destination.WebView(url = url, gameTitle = gameTitle)) {
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

}
