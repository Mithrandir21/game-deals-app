package pm.bam.gamedeals.feature.giveaways.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.giveaways.ui.GiveawaysScreen

fun NavGraphBuilder.giveawaysScreen(
    navController: NavController,
    goToWeb: (url: String, gameTitle: String) -> Unit
) {
    composable<Destination.Giveaways> {
        GiveawaysScreen(
            onBack = { navController.popBackStack() },
            goToWeb = goToWeb
        )
    }
}
