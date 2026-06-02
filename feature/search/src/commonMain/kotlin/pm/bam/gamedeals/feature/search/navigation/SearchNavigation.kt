package pm.bam.gamedeals.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.search.ui.SearchScreen

fun NavGraphBuilder.searchScreen(
    goToGame: (gameId: String) -> Unit
) {
    composable<Destination.Search> {
        SearchScreen(
            onSearchedGame = { gameId: String -> goToGame(gameId) }
        )
    }
}
