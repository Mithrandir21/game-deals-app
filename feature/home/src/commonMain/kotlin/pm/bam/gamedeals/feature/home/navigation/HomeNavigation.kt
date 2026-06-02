package pm.bam.gamedeals.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.home.ui.HomeScreen

fun NavGraphBuilder.homeScreen(
    goToSearch: () -> Unit,
    goToGame: (gameId: String) -> Unit,
    goToStore: (storeId: Int) -> Unit,
    goToGiveaway: () -> Unit,
    goToFavourites: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
) {
    composable<Destination.Home> {
        HomeScreen(
            onSearch = goToSearch,
            goToGame = goToGame,
            onViewStoreDeals = { store: Store -> goToStore(store.storeID) },
            onViewGiveaways = goToGiveaway,
            onViewFavourites = goToFavourites,
            goToWeb = { url: String, gameTitle: String -> goToWeb(url, gameTitle) },
            goToGameDetails = goToGameDetails,
            goToGameDetailsByTitle = goToGameDetailsByTitle,
        )
    }
}
