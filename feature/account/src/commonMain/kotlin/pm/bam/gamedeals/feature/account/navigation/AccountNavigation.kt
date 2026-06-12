package pm.bam.gamedeals.feature.account.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_row_ignored
import pm.bam.gamedeals.feature.account.generated.resources.account_row_linked
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notes
import pm.bam.gamedeals.feature.account.ui.AccountScreen
import pm.bam.gamedeals.feature.account.ui.CollectionListScreen
import pm.bam.gamedeals.feature.account.ui.ComingSoonScreen
import pm.bam.gamedeals.feature.account.ui.NotificationsScreen
import pm.bam.gamedeals.feature.account.ui.WaitlistListScreen

/**
 * Registers the Account hub (#272, P1.2) and its sub-screens. The hub routes to the library lists and
 * the (placeholder) discovery/connection screens via [navController]; region is an in-hub bottom sheet
 * (#276) and the website-only settings open externally via [goToWeb]. Library rows reach game detail
 * via [goToGame].
 */
fun NavGraphBuilder.accountScreen(
    navController: NavController,
    goToGame: (gameId: String) -> Unit,
    goToWeb: (url: String) -> Unit,
) {
    composable<Destination.Account> {
        AccountScreen(
            onOpenWaitlist = { navController.navigate(Destination.WaitlistList) },
            onOpenCollection = { navController.navigate(Destination.CollectionList) },
            onOpenNotifications = { navController.navigate(Destination.Notifications) },
            onOpenIgnored = { navController.navigate(Destination.IgnoredGames) },
            onOpenMyNotes = { navController.navigate(Destination.MyNotes) },
            onOpenLinkedAccounts = { navController.navigate(Destination.LinkedAccounts) },
            onOpenWebsite = goToWeb,
        )
    }

    composable<Destination.WaitlistList> {
        WaitlistListScreen(onBack = { navController.popBackStack() }, onGameClick = goToGame)
    }
    composable<Destination.CollectionList> {
        CollectionListScreen(onBack = { navController.popBackStack() }, onGameClick = goToGame)
    }

    composable<Destination.Notifications> {
        NotificationsScreen(onBack = { navController.popBackStack() })
    }

    // Placeholder routes — filled in by later phases (P3–P5).
    composable<Destination.IgnoredGames> {
        ComingSoonScreen(title = stringResource(Res.string.account_row_ignored), onBack = { navController.popBackStack() })
    }
    composable<Destination.MyNotes> {
        ComingSoonScreen(title = stringResource(Res.string.account_row_notes), onBack = { navController.popBackStack() })
    }
    composable<Destination.LinkedAccounts> {
        ComingSoonScreen(title = stringResource(Res.string.account_row_linked), onBack = { navController.popBackStack() })
    }
}
