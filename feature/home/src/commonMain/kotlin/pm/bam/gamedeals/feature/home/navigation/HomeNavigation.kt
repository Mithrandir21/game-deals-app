package pm.bam.gamedeals.feature.home.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.dropUnlessResumed
import pm.bam.gamedeals.feature.home.ui.HomeScreen
import pm.bam.gamedeals.feature.home.ui.WinterSaleGuideScreen

fun NavGraphBuilder.homeScreen(
    goToGame: (gameId: String) -> Unit,
    goToGameByTitle: (title: String) -> Unit,
    goToWaitlist: () -> Unit,
    goToCollection: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToBundles: () -> Unit,
    goToBundle: (bundleId: Int) -> Unit,
    goToWinterSale: () -> Unit,
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
            onViewWinterSale = dropUnlessResumed(block = goToWinterSale),
        )
    }
}

fun NavGraphBuilder.winterSaleGuideScreen(
    onBack: () -> Unit,
    goToGame: (String) -> Unit,
    goToOffer: (String) -> Unit,
) {
    composable<Destination.WinterSaleGuide> {
        WinterSaleGuideScreen(
            onBack = dropUnlessResumed(block = onBack),
            onGame = goToGame.dropUnlessResumed(),
            onOffer = goToOffer.dropUnlessResumed(),
        )
    }
}
