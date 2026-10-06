package pm.bam.gamedeals.feature.store.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.dropUnlessResumed
import pm.bam.gamedeals.feature.store.ui.StoreScreen

fun NavGraphBuilder.storeScreen(
    navController: NavController,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
) {
    composable<Destination.Store> {
        StoreScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            goToWeb = goToWeb.dropUnlessResumed(),
            goToGame = goToGame.dropUnlessResumed(),
        )
    }
}
