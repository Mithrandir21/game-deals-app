package pm.bam.gamedeals.feature.deals.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.deals.ui.DealsScreen

/**
 * The Deals tab (epic #219, Phase 4): a full, sorted, all-stores deals browser hosted under the app
 * shell (the shell owns the top bar + bottom nav, so this screen adds no chrome of its own). Row taps
 * open the shared game-centric `GamePeekSheet`; the heart writes to the ITAD waitlist.
 */
fun NavGraphBuilder.dealsScreen(
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
    goToDiscover: () -> Unit = {},
    // Injected by :app: on tablets the list-detail layout renders this (the full game page) in the
    // detail pane. Null on iOS/where unset → the wide detail pane falls back to the peek content.
    gameDetailPane: (@Composable (gameId: String, canReturnToList: Boolean, onReturnToList: () -> Unit) -> Unit)? = null,
) {
    composable<Destination.Deals> {
        DealsScreen(
            goToWeb = goToWeb,
            goToGame = goToGame,
            goToDiscover = goToDiscover,
            gameDetailPane = gameDetailPane,
        )
    }
}
