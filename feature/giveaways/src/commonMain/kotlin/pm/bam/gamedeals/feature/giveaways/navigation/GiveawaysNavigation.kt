package pm.bam.gamedeals.feature.giveaways.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.giveaways.ui.GiveawaysScreen

fun NavGraphBuilder.giveawaysScreen(
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    composable<Destination.Giveaways> {
        GiveawaysScreen(
            goToWeb = goToWeb,
        )
    }
}
