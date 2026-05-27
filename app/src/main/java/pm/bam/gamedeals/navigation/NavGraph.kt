package pm.bam.gamedeals.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.favourites.navigation.favouritesScreen
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

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        homeScreen(
            goToSearch = { navActions.navigateToSearch() },
            goToGame = { gameId -> navActions.navigateToGame(gameId) },
            goToStore = { storeId -> navActions.navigateToStore(storeId) },
            goToGiveaway = { navActions.navigateToGiveaways() },
            goToFavourites = { navActions.navigateToFavourites() },
            goToWeb = { url: String, gameTitle: String -> navActions.navigateToWeb(url, gameTitle) },
            goToGameDetails = { steamAppId, title -> navActions.navigateToGameDetails(steamAppId, title) },
            goToGameDetailsByTitle = { title -> navActions.navigateToGameDetailsByTitle(title) },
        )

        storeScreen(
            navController = navController,
            goToWeb = { url: String, gameTitle: String -> navActions.navigateToWeb(url, gameTitle) },
            goToGameDetails = { steamAppId, title -> navActions.navigateToGameDetails(steamAppId, title) },
            goToGameDetailsByTitle = { title -> navActions.navigateToGameDetailsByTitle(title) },
        )

        gameScreen(
            navController = navController,
            goToWeb = { url: String, gameTitle: String -> navActions.navigateToWeb(url, gameTitle) },
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
            goToWeb = { url: String, gameTitle: String -> navActions.navigateToWeb(url, gameTitle) }
        )

        favouritesScreen(
            navController = navController,
            goToGame = { gameId -> navActions.navigateToGame(gameId) }
        )
    }
}
