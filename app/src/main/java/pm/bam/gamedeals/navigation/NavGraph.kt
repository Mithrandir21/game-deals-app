package pm.bam.gamedeals.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.shell.GameDealsAppShell
import pm.bam.gamedeals.common.ui.shell.TopLevelDestination
import pm.bam.gamedeals.feature.account.navigation.accountScreen
import pm.bam.gamedeals.feature.bundles.navigation.bundleDetailScreen
import pm.bam.gamedeals.feature.bundles.navigation.bundlesScreen
import pm.bam.gamedeals.feature.deals.navigation.dealsScreen
import pm.bam.gamedeals.feature.game.navigation.gameDetailsScreen
import pm.bam.gamedeals.feature.game.navigation.gameScreen
import pm.bam.gamedeals.feature.giveaways.navigation.giveawaysScreen
import pm.bam.gamedeals.feature.home.navigation.homeScreen
import pm.bam.gamedeals.feature.search.navigation.searchScreen
import pm.bam.gamedeals.feature.store.navigation.storeScreen
import pm.bam.gamedeals.feature.webview.navigation.webViewScreen

@Composable
internal fun NavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Destination.Home,
    navActions: NavigationActions = remember(navController) { NavigationActions(navController) }
) {
    val uriHandler = LocalUriHandler.current

    // Drive the shell chrome from the current route (epic #219, Phase 1): bottom nav on the top-level
    // tabs, top bar on all tabs except Giveaways (which keeps its own top bar — interim), no chrome on
    // detail routes (they own their Scaffold/TopAppBar).
    val currentDestination by navController.currentBackStackEntryAsState()
    val selectedTab = TopLevelDestination.entries.firstOrNull { tab ->
        currentDestination?.destination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
    }
    val isTab = selectedTab != null

    GameDealsAppShell(
        modifier = modifier,
        selectedTab = selectedTab,
        showTopBar = isTab && selectedTab != TopLevelDestination.GIVEAWAYS,
        showBottomBar = isTab,
        onSelectTab = { navActions.navigateTopLevel(it.destination) },
        onSearch = { navActions.navigateToSearch() },
        onBrowseStores = null,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            homeScreen(
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
                goToGiveaway = { navActions.navigateTopLevel(Destination.Giveaways) },
                goToWeb = { url, _ -> uriHandler.openUri(url) },
                goToGameDetails = { steamAppId, title -> navActions.navigateToGameDetails(steamAppId, title) },
                goToGameDetailsByTitle = { title -> navActions.navigateToGameDetailsByTitle(title) },
                goToBundles = { navActions.navigateToBundles() },
                goToBundle = { bundleId -> navActions.navigateToBundleDetail(bundleId) },
            )

            dealsScreen(
                goToWeb = { url, _ -> uriHandler.openUri(url) },
                goToGameDetails = { steamAppId, title -> navActions.navigateToGameDetails(steamAppId, title) },
                goToGameDetailsByTitle = { title -> navActions.navigateToGameDetailsByTitle(title) },
            )
            accountScreen(
                navController = navController,
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
                goToWeb = { url -> uriHandler.openUri(url) },
            )

            storeScreen(
                navController = navController,
                goToWeb = { url, _ -> uriHandler.openUri(url) },
                goToGameDetails = { steamAppId, title -> navActions.navigateToGameDetails(steamAppId, title) },
                goToGameDetailsByTitle = { title -> navActions.navigateToGameDetailsByTitle(title) },
            )

            gameScreen(
                navController = navController,
                goToWeb = { url, _ -> uriHandler.openUri(url) },
                goToGameDetails = { steamAppId, title -> navActions.navigateToGameDetails(steamAppId, title) }
            )

            gameDetailsScreen(navController = navController)

            searchScreen(
                goToGame = { gameId -> navActions.navigateToGame(gameId) }
            )

            webViewScreen(
                onBack = { navController.popBackStack() }
            )

            giveawaysScreen(
                navController = navController,
                goToWeb = { url, _ -> uriHandler.openUri(url) }
            )

            bundlesScreen(
                navController = navController,
                goToBundle = { bundleId -> navActions.navigateToBundleDetail(bundleId) },
            )

            bundleDetailScreen(
                navController = navController,
                goToWeb = { url, _ -> uriHandler.openUri(url) },
            )
        }
    }
}
