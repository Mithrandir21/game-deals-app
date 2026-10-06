package pm.bam.gamedeals.feature.debug.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.debug.ui.DebugScreen

/**
 * Registers the developer-tools screen. The route is always registered so the graph has the same shape in
 * every build type; only the Account hub's entry point is debug-gated.
 */
fun NavGraphBuilder.debugScreen(navController: NavController) {
    composable<Destination.Debug> {
        DebugScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
}
