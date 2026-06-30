package pm.bam.gamedeals.feature.account.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_row_linked
import pm.bam.gamedeals.feature.account.ui.AccountScreen
import pm.bam.gamedeals.feature.account.ui.CollectionListScreen
import pm.bam.gamedeals.feature.account.ui.ComingSoonScreen
import pm.bam.gamedeals.feature.account.ui.FollowedSeriesScreen
import pm.bam.gamedeals.feature.account.ui.IgnoredScreen
import pm.bam.gamedeals.feature.account.ui.MyNotesScreen
import pm.bam.gamedeals.feature.account.ui.NotificationDayScreen
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
    onReplayOnboarding: () -> Unit,
) {
    composable<Destination.Account> {
        AccountScreen(
            onOpenWaitlist = { navController.navigate(Destination.WaitlistList) },
            onOpenCollection = { navController.navigate(Destination.CollectionList) },
            onOpenNotifications = { navController.navigate(Destination.Notifications) },
            onOpenIgnored = { navController.navigate(Destination.IgnoredGames) },
            onOpenMyNotes = { navController.navigate(Destination.MyNotes) },
            onOpenFollowedSeries = { navController.navigate(Destination.FollowedSeriesList) },
            onOpenLinkedAccounts = { navController.navigate(Destination.LinkedAccounts) },
            onOpenWebsite = goToWeb,
            onReplayOnboarding = onReplayOnboarding,
        )
    }

    composable<Destination.WaitlistList> {
        // A tap opens the shared game-centric peek sheet (same as Home/Deals); the sheet routes to the full
        // game page via goToGame and to store deals via goToWeb.
        WaitlistListScreen(onBack = { navController.popBackStack() }, goToGame = goToGame, goToWeb = goToWeb)
    }
    composable<Destination.CollectionList> {
        CollectionListScreen(onBack = { navController.popBackStack() }, goToGame = goToGame, goToWeb = goToWeb)
    }
    composable<Destination.FollowedSeriesList> {
        // Followed-series tiles carry IGDB ids, so they open the game page by IGDB id (a Steam-id detour
        // would silently drop console/indie titles), mirroring the game page's series row.
        FollowedSeriesScreen(
            onBack = { navController.popBackStack() },
            onGameClick = { igdbGameId -> navController.navigate(Destination.GameDetailsByIgdbId(igdbGameId)) },
        )
    }

    composable<Destination.Notifications> {
        NotificationsScreen(
            onBack = { navController.popBackStack() },
            onOpenDay = { date -> navController.navigate(Destination.NotificationDay(date)) },
        )
    }

    composable<Destination.NotificationDay> {
        NotificationDayScreen(onBack = { navController.popBackStack() }, onGameClick = goToGame)
    }

    composable<Destination.IgnoredGames> {
        IgnoredScreen(onBack = { navController.popBackStack() }, onGameClick = goToGame)
    }

    composable<Destination.MyNotes> {
        MyNotesScreen(onBack = { navController.popBackStack() }, onGameClick = goToGame)
    }

    // Placeholder routes — not yet implemented.
    composable<Destination.LinkedAccounts> {
        ComingSoonScreen(title = stringResource(Res.string.account_row_linked), onBack = { navController.popBackStack() })
    }
}
