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
    composable<Destination.GameDetails> {
        GameDetailsScreen(
            onBack = { navController.popBackStack() },
            onSimilarGameClick = navigateToSimilar,
        )
    }
    composable<Destination.GameDetailsByIgdbId> {
        GameDetailsScreen(
            onBack = { navController.popBackStack() },
            onSimilarGameClick = navigateToSimilar,
        )
    }
    composable<Destination.GameDetailsByTitle> {
        GameDetailsScreen(
            onBack = { navController.popBackStack() },
            onSimilarGameClick = navigateToSimilar,
        )
    }
}
