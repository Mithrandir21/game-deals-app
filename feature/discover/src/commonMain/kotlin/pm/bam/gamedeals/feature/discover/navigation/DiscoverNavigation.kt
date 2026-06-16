package pm.bam.gamedeals.feature.discover.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.feature.discover.ui.DiscoverPickerScreen
import pm.bam.gamedeals.feature.discover.ui.DiscoverResultsScreen

/**
 * Tag-discovery picker (epic #307). Reached from the Deals tab's "Discover by tag" affordance; on
 * "Show results" it hands the selected [IgdbTagFilter] to [goToResults]. Owns its back arrow.
 */
fun NavGraphBuilder.discoverScreen(
    navController: NavHostController,
    goToResults: (IgdbTagFilter) -> Unit,
) {
    composable<Destination.Discover> {
        DiscoverPickerScreen(
            onBack = { navController.popBackStack() },
            onShowResults = goToResults,
        )
    }
}

/**
 * Tag-discovery results (epic #307). A priced result opens the in-app Game Page; a Steam-only result
 * links out to the store; an unpriced result is inert.
 */
fun NavGraphBuilder.discoverResultsScreen(
    navController: NavHostController,
    goToGame: (gameId: String) -> Unit,
    goToWeb: (url: String) -> Unit,
) {
    composable<Destination.DiscoverResults> {
        DiscoverResultsScreen(
            onBack = { navController.popBackStack() },
            goToGame = goToGame,
            goToWeb = goToWeb,
        )
    }
}
