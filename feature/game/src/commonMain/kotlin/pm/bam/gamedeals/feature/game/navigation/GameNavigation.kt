package pm.bam.gamedeals.feature.game.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.game.ui.GameScreen

fun NavGraphBuilder.gameScreen(
    navController: NavController,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
) {
    composable<Destination.Game> {
        GameScreen(
            onBack = { navController.popBackStack() },
            goToWeb = goToWeb,
            goToGameDetails = goToGameDetails,
        )
    }
}
