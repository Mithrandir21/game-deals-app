package pm.bam.gamedeals.feature.bundles.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.dropUnlessResumed
import pm.bam.gamedeals.feature.bundles.ui.BundleDetailScreen
import pm.bam.gamedeals.feature.bundles.ui.BundlesScreen

fun NavGraphBuilder.bundlesScreen(
    navController: NavController,
    goToBundle: (bundleId: Int) -> Unit,
) {
    composable<Destination.Bundles> {
        BundlesScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onBundleClick = goToBundle.dropUnlessResumed(),
        )
    }
}

fun NavGraphBuilder.bundleDetailScreen(
    navController: NavController,
    goToWeb: (url: String, title: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
) {
    composable<Destination.BundleDetail> {
        BundleDetailScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            goToWeb = goToWeb.dropUnlessResumed(),
            onGameClick = goToGame.dropUnlessResumed(),
        )
    }
}
