package pm.bam.gamedeals.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.settings.ui.SettingsScreen

fun NavGraphBuilder.settingsScreen(
    navController: NavController,
) {
    composable<Destination.Settings> {
        SettingsScreen(onBack = { navController.popBackStack() })
    }
}
