package pm.bam.gamedeals.feature.giveaways.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.giveaways.ui.GiveawayDetailScreen
import pm.bam.gamedeals.feature.giveaways.ui.GiveawaysScreen

fun NavGraphBuilder.giveawaysScreen(
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGiveawayDetail: (giveawayId: Int) -> Unit,
) {
    composable<Destination.Giveaways> {
        GiveawaysScreen(
            goToWeb = goToWeb,
            goToGiveawayDetail = goToGiveawayDetail,
        )
    }
}

fun NavGraphBuilder.giveawayDetailScreen(
    navController: NavController,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    composable<Destination.GiveawayDetail> {
        GiveawayDetailScreen(
            onBack = { navController.popBackStack() },
            goToWeb = goToWeb,
        )
    }
}
