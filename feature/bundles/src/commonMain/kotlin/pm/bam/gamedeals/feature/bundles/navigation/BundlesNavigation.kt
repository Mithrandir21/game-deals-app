package pm.bam.gamedeals.feature.bundles.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.bundles.ui.BundleDetailScreen
import pm.bam.gamedeals.feature.bundles.ui.BundlesScreen

fun NavGraphBuilder.bundlesScreen(
    navController: NavController,
    goToBundle: (bundleId: Int) -> Unit,
) {
    composable<Destination.Bundles> {
        BundlesScreen(
            onBack = { navController.popBackStack() },
            onBundleClick = goToBundle,
        )
    }
}

fun NavGraphBuilder.bundleDetailScreen(
    navController: NavController,
    goToWeb: (url: String, title: String) -> Unit,
) {
    composable<Destination.BundleDetail> {
        BundleDetailScreen(
            onBack = { navController.popBackStack() },
            goToWeb = goToWeb,
        )
    }
}
