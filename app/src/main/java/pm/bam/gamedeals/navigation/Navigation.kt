package pm.bam.gamedeals.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.navigation.SearchRequestBus
import pm.bam.gamedeals.domain.models.IgdbTagFilter


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

    /** Open the Account hub's Notifications sub-screen (e.g. from a tapped multi-game background alert, #288). */
    fun navigateToNotifications() {
        navController.navigate(Destination.Notifications)
    }

    /** Open the Waitlist list (from the Home account stat card). Registered under the shared NavHost. */
    fun navigateToWaitlist() {
        navController.navigate(Destination.WaitlistList)
    }

    /** Open the Collection list (from the Home account stat card). Registered under the shared NavHost. */
    fun navigateToCollection() {
        navController.navigate(Destination.CollectionList)
    }

    fun navigateToGameDetails(steamAppId: Int, title: String? = null) {
        navController.navigate(Destination.GameDetails(steamAppId, title))
    }

    fun navigateToGameDetailsByTitle(title: String) {
        navController.navigate(Destination.GameDetailsByTitle(title))
    }

    /**
     * Search lives on the Deals tab (Search was merged into Deals): switch to the tab with the standard
     * tab back-stack behaviour, then ask the Deals screen to reveal its search field — optionally
     * prefilled with [title] (e.g. the Game Page's "search by title" deep-link).
     */
    fun navigateToSearch(title: String? = null) {
        navigateTopLevel(Destination.Deals)
        SearchRequestBus.request(title)
    }

    fun navigateToGiveaways() {
        navController.navigate(Destination.Giveaways) {
            restoreState = true
        }
    }

    fun navigateToGiveawayDetail(giveawayId: Int) {
        navController.navigate(Destination.GiveawayDetail(giveawayId))
    }

    fun navigateToBundles() {
        navController.navigate(Destination.Bundles) {
            restoreState = true
        }
    }

    fun navigateToBundleDetail(bundleId: Int) {
        navController.navigate(Destination.BundleDetail(bundleId))
    }

    /** Open the tag-discovery picker (epic #307), from the Deals tab affordance. */
    fun navigateToDiscover() {
        navController.navigate(Destination.Discover)
    }

    /** Open the tag-discovery results for [filter] — encodes each dimension's ids as a comma-joined arg. */
    fun navigateToDiscoverResults(filter: IgdbTagFilter) {
        navController.navigate(
            Destination.DiscoverResults(
                genreIds = filter.genreIds.joinToString(","),
                themeIds = filter.themeIds.joinToString(","),
                gameModeIds = filter.gameModeIds.joinToString(","),
                perspectiveIds = filter.perspectiveIds.joinToString(","),
                keywordIds = filter.keywordIds.joinToString(","),
            )
        )
    }

}
