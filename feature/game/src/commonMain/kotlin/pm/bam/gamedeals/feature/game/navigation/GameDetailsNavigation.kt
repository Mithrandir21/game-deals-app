package pm.bam.gamedeals.feature.game.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.game.ui.GameDetailsScreen

fun NavGraphBuilder.gameDetailsScreen(navController: NavController) {
    val navigateToSimilar: (Long) -> Unit = { igdbGameId ->
        navController.navigate(Destination.GameDetailsByIgdbId(igdbGameId))
    }
    val navigateToGame: (Int) -> Unit = { gameId ->
        navController.navigate(Destination.Game(gameId))
    }
    val navigateToSearch: (String) -> Unit = { title ->
        navController.navigate(Destination.Search(initialQuery = title))
    }
    composable<Destination.GameDetails> {
        GameDetailsScreen(
            onBack = { navController.popBackStack() },
            onSimilarGameClick = navigateToSimilar,
            onViewDealsClick = navigateToGame,
            onSearchDealsByTitle = navigateToSearch,
        )
    }
    composable<Destination.GameDetailsByIgdbId> {
        GameDetailsScreen(
            onBack = { navController.popBackStack() },
            onSimilarGameClick = navigateToSimilar,
            onViewDealsClick = navigateToGame,
            onSearchDealsByTitle = navigateToSearch,
        )
    }
}
