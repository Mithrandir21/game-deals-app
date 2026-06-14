package pm.bam.gamedeals.feature.deals.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.deals.ui.DealsScreen

/**
 * The Deals tab (epic #219, Phase 4): a full, sorted, all-stores deals browser hosted under the app
 * shell (the shell owns the top bar + bottom nav, so this screen adds no chrome of its own). Row taps
 * open the shared `DealBottomSheet`; the heart writes to the ITAD waitlist.
 */
fun NavGraphBuilder.dealsScreen(
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
) {
    composable<Destination.Deals> {
        DealsScreen(
            goToWeb = goToWeb,
            goToGame = goToGame,
        )
    }
}
