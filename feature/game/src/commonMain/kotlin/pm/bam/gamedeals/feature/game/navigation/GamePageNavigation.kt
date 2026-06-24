package pm.bam.gamedeals.feature.game.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.game.ui.GamePageScreen

/**
 * The single Game Page route (epic #291, Phase 8) — replaces the old `gameScreen` + `gameDetailsScreen`.
 *
 * All four historical destinations render the same [GamePageScreen]; `GamePageViewModel` reads whichever
 * identity key the destination carries (`gameId` / `steamAppId`+`title` / `igdbGameId` / `title`) from its
 * `SavedStateHandle` and resolves the rest. Keeping the four destinations (rather than one nullable route)
 * preserves type-safe navigation and means callers don't change — they already target these routes.
 */
fun NavGraphBuilder.gamePageScreen(
    navController: NavController,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToBundle: (bundleId: Int) -> Unit,
    goToSearchByTitle: (title: String) -> Unit,
) {
    val content: @Composable () -> Unit = {
        GamePageScreen(
            onBack = { navController.popBackStack() },
            goToWeb = goToWeb,
            onSimilarGameClick = { igdbGameId -> navController.navigate(Destination.GameDetailsByIgdbId(igdbGameId)) },
            onSearchDealsByTitle = goToSearchByTitle,
            onBundleClick = goToBundle,
        )
    }
    composable<Destination.Game> { content() }
    composable<Destination.GameDetails> { content() }
    composable<Destination.GameDetailsByIgdbId> { content() }
    composable<Destination.GameDetailsByTitle> { content() }
}
