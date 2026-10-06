package pm.bam.gamedeals.feature.home.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.dropUnlessResumed
import pm.bam.gamedeals.feature.home.ui.HomeScreen

fun NavGraphBuilder.homeScreen(
    goToGame: (gameId: String) -> Unit,
    goToGameByTitle: (title: String) -> Unit,
    goToWaitlist: () -> Unit,
    goToCollection: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToBundles: () -> Unit,
    goToBundle: (bundleId: Int) -> Unit,
) {
    // Search + Settings live on the app-shell top bar / overflow. Giveaways moved off Home to their
    // own tab, so Home is now 100% in-app content.
    composable<Destination.Home> {
        HomeScreen(
            goToGame = goToGame.dropUnlessResumed(),
            goToGameByTitle = goToGameByTitle.dropUnlessResumed(),
            onViewWaitlist = dropUnlessResumed(block = goToWaitlist),
            onViewCollection = dropUnlessResumed(block = goToCollection),
            onViewBundles = dropUnlessResumed(block = goToBundles),
            onViewBundle = goToBundle.dropUnlessResumed(),
            goToWeb = { url: String, gameTitle: String -> goToWeb(url, gameTitle) }.dropUnlessResumed(),
        )
    }
}
