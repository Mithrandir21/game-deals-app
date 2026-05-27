package pm.bam.gamedeals.feature.store.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.store.ui.StoreScreen

fun NavGraphBuilder.storeScreen(
    navController: NavController,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
) {
    composable<Destination.Store> {
        StoreScreen(
            onBack = { navController.popBackStack() },
            goToWeb = goToWeb,
            goToGameDetails = goToGameDetails,
            goToGameDetailsByTitle = goToGameDetailsByTitle,
        )
    }
}
