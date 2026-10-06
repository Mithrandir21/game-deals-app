package pm.bam.gamedeals.feature.account.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.dropUnlessResumed
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
            onOpenWaitlist = dropUnlessResumed { navController.navigate(Destination.WaitlistList) },
            onOpenCollection = dropUnlessResumed { navController.navigate(Destination.CollectionList) },
            onOpenNotifications = dropUnlessResumed { navController.navigate(Destination.Notifications) },
            onOpenIgnored = dropUnlessResumed { navController.navigate(Destination.IgnoredGames) },
            onOpenMyNotes = dropUnlessResumed { navController.navigate(Destination.MyNotes) },
            onOpenFollowedSeries = dropUnlessResumed { navController.navigate(Destination.FollowedSeriesList) },
            onOpenLinkedAccounts = dropUnlessResumed { navController.navigate(Destination.LinkedAccounts) },
            onOpenWebsite = goToWeb.dropUnlessResumed(),
            onReplayOnboarding = dropUnlessResumed(block = onReplayOnboarding),
            // The row itself is debug-gated; the route is registered by :feature:debug in every build type.
            onOpenDebug = dropUnlessResumed { navController.navigate(Destination.Debug) },
        )
    }

    composable<Destination.WaitlistList> {
        // A tap opens the shared game-centric peek sheet (same as Home/Deals); the sheet routes to the full
        // game page via goToGame and to store deals via goToWeb.
        WaitlistListScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            goToGame = goToGame.dropUnlessResumed(),
            goToWeb = goToWeb.dropUnlessResumed(),
        )
    }
    composable<Destination.CollectionList> {
        CollectionListScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            goToGame = goToGame.dropUnlessResumed(),
            goToWeb = goToWeb.dropUnlessResumed(),
        )
    }
    composable<Destination.FollowedSeriesList> {
        // Followed-series tiles carry IGDB ids, so they open the game page by IGDB id (a Steam-id detour
        // would silently drop console/indie titles), mirroring the game page's series row.
        FollowedSeriesScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onGameClick = { igdbGameId: Long -> navController.navigate(Destination.GameDetailsByIgdbId(igdbGameId)) }.dropUnlessResumed(),
        )
    }

    composable<Destination.Notifications> {
        NotificationsScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onOpenDay = { date: String -> navController.navigate(Destination.NotificationDay(date)) }.dropUnlessResumed(),
        )
    }

    composable<Destination.NotificationDay> {
        NotificationDayScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onGameClick = goToGame.dropUnlessResumed(),
            goToWeb = goToWeb.dropUnlessResumed(),
        )
    }

    composable<Destination.IgnoredGames> {
        IgnoredScreen(onBack = dropUnlessResumed { navController.popBackStack() }, onGameClick = goToGame.dropUnlessResumed())
    }

    composable<Destination.MyNotes> {
        MyNotesScreen(onBack = dropUnlessResumed { navController.popBackStack() }, onGameClick = goToGame.dropUnlessResumed())
    }

    // Placeholder routes — not yet implemented.
    composable<Destination.LinkedAccounts> {
        ComingSoonScreen(title = stringResource(Res.string.account_row_linked), onBack = dropUnlessResumed { navController.popBackStack() })
    }
}
