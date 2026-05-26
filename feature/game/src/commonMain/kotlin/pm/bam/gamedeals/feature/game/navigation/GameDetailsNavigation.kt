package pm.bam.gamedeals.feature.game.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.game.ui.GameDetailsScreen

fun NavGraphBuilder.gameDetailsScreen(navController: NavController) {
    composable<Destination.GameDetails> {
        GameDetailsScreen(onBack = { navController.popBackStack() })
    }
}
