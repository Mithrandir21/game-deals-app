package pm.bam.gamedeals.feature.account.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.account.ui.AccountScreen

fun NavGraphBuilder.accountScreen(
    goToGame: (gameId: String) -> Unit,
) {
    composable<Destination.Account> {
        AccountScreen(onGameClick = goToGame)
    }
}
