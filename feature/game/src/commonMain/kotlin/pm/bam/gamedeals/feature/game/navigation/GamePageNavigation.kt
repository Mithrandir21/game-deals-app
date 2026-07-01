package pm.bam.gamedeals.feature.game.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
    // Embedded hosts (Deals tablet detail) pass this to say whether their list is currently hidden, so a
    // back-to-list arrow is warranted. Null → normal full-screen route: the arrow always shows.
    canReturnToList: (@Composable () -> Boolean)? = null,
    // Back at this graph's root (nothing left to pop) delegates here, letting an embedded host return to
    // its list. No-op for the normal route.
    onExit: () -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        val showBackButton = if (canReturnToList == null) {
            true
        } else {
            // Subscribe to the nested backstack so the arrow appears once we drill into a similar game.
            navController.currentBackStackEntryAsState().value
            navController.previousBackStackEntry != null || canReturnToList()
        }
        GamePageScreen(
            onBack = { if (!navController.popBackStack()) onExit() },
            goToWeb = goToWeb,
            onSimilarGameClick = { igdbGameId -> navController.navigate(Destination.GameDetailsByIgdbId(igdbGameId)) },
            onSearchDealsByTitle = goToSearchByTitle,
            onBundleClick = goToBundle,
            showBackButton = showBackButton,
        )
    }
    composable<Destination.Game> { content() }
    composable<Destination.GameDetails> { content() }
    composable<Destination.GameDetailsByIgdbId> { content() }
    composable<Destination.GameDetailsByTitle> { content() }
}
