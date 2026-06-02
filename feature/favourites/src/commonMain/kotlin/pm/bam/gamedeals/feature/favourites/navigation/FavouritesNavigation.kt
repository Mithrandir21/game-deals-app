package pm.bam.gamedeals.feature.favourites.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.favourites.ui.FavouritesScreen

fun NavGraphBuilder.favouritesScreen(
    navController: NavController,
    goToGame: (String) -> Unit,
) {
    composable<Destination.Favourites> {
        FavouritesScreen(
            onBack = { navController.popBackStack() },
            goToGame = goToGame,
        )
    }
}
