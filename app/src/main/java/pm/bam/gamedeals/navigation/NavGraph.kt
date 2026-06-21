package pm.bam.gamedeals.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.navigation.SearchController
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.shell.GameDealsAppShell
import pm.bam.gamedeals.common.ui.shell.TopLevelDestination
import pm.bam.gamedeals.feature.account.navigation.accountScreen
import pm.bam.gamedeals.feature.account.ui.SignInPromptHost
import pm.bam.gamedeals.feature.account.ui.rememberAccountTabUnreadCount
import pm.bam.gamedeals.feature.bundles.navigation.bundleDetailScreen
import pm.bam.gamedeals.feature.bundles.navigation.bundlesScreen
import pm.bam.gamedeals.feature.deals.navigation.dealsScreen
import pm.bam.gamedeals.feature.discover.navigation.discoverResultsScreen
import pm.bam.gamedeals.feature.discover.navigation.discoverScreen
import pm.bam.gamedeals.feature.game.navigation.gamePageScreen
import pm.bam.gamedeals.feature.giveaways.navigation.giveawayDetailScreen
import pm.bam.gamedeals.feature.giveaways.navigation.giveawaysScreen
import pm.bam.gamedeals.feature.home.navigation.homeScreen
import pm.bam.gamedeals.feature.onboarding.navigation.onboardingScreen
import pm.bam.gamedeals.feature.store.navigation.storeScreen
import pm.bam.gamedeals.feature.webview.navigation.webViewScreen
import pm.bam.gamedeals.notifications.NotificationRoute
import pm.bam.gamedeals.notifications.NotificationRouteBus

@Composable
internal fun NavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Destination.Home,
    navActions: NavigationActions = remember(navController) { NavigationActions(navController) }
) {
    // External / website links open in an in-app Custom Tab (Android) / SFSafariViewController (iOS)
    // rather than the standalone system browser, so the user stays in-context.
    val platformActions = LocalPlatformActions.current

    // Route taps on background (OS-tray) notifications into the nav graph: the bundled waitlist summary opens
    // the Notifications list, the bundled followed-franchise summary opens the Followed-series screen. The bus
    // is consume-once, so this won't re-navigate.
    LaunchedEffect(Unit) {
        NotificationRouteBus.routes.collect { route ->
            when (route) {
                NotificationRoute.Notifications -> navActions.navigateToNotifications()
                NotificationRoute.FollowedSeries -> navActions.navigateToFollowedSeries()
            }
        }
    }

    // Drive the shell chrome from the current route: bottom nav + the shared top bar on every
    // top-level tab; no chrome on detail routes (they own their Scaffold/TopAppBar).
    val currentDestination by navController.currentBackStackEntryAsState()
    val selectedTab = TopLevelDestination.entries.firstOrNull { tab ->
        currentDestination?.destination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
    }
    val isTab = selectedTab != null

    // The shared toolbar's search field reflects the active query (null = browse mode), kept in sync via
    // SearchController so a search started anywhere (toolbar submit or a deep-link) is shown there.
    val activeSearchQuery by SearchController.activeQuery.collectAsState()

    GameDealsAppShell(
        modifier = modifier,
        selectedTab = selectedTab,
        showTopBar = isTab,
        showBottomBar = isTab,
        onSelectTab = { navActions.navigateTopLevel(it.destination) },
        activeSearchQuery = activeSearchQuery,
        onSearchSubmit = { query -> navActions.searchDeals(query) },
        onSearchClosed = { navActions.clearSearch() },
        onBrowseStores = null,
        accountUnreadCount = rememberAccountTabUnreadCount(),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            onboardingScreen(
                onFinish = { navActions.finishOnboarding() },
            )

            homeScreen(
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
                goToGameByTitle = { title -> navActions.navigateToGameDetailsByTitle(title) },
                goToWaitlist = { navActions.navigateToWaitlist() },
                goToCollection = { navActions.navigateToCollection() },
                goToWeb = { url, _ -> platformActions.openInApp(url) },
                goToBundles = { navActions.navigateToBundles() },
                goToBundle = { bundleId -> navActions.navigateToBundleDetail(bundleId) },
            )

            dealsScreen(
                goToWeb = { url, _ -> platformActions.openInApp(url) },
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
                goToDiscover = { navActions.navigateToDiscover() },
            )

            discoverScreen(
                navController = navController,
                goToResults = { filter -> navActions.navigateToDiscoverResults(filter) },
            )

            discoverResultsScreen(
                navController = navController,
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
                goToWeb = { url -> platformActions.openInApp(url) },
            )
            accountScreen(
                navController = navController,
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
                goToWeb = { url -> platformActions.openInApp(url) },
                onReplayOnboarding = { navActions.navigateToOnboarding() },
            )

            storeScreen(
                navController = navController,
                goToWeb = { url, _ -> platformActions.openInApp(url) },
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
            )

            gamePageScreen(
                navController = navController,
                goToWeb = { url, _ -> platformActions.openInApp(url) },
                goToBundle = { bundleId -> navActions.navigateToBundleDetail(bundleId) },
                goToSearchByTitle = { title -> navActions.searchDeals(title) },
            )

            webViewScreen(
                onBack = { navController.popBackStack() }
            )

            giveawaysScreen(
                goToWeb = { url, _ -> platformActions.openInApp(url) },
                goToGiveawayDetail = { giveawayId -> navActions.navigateToGiveawayDetail(giveawayId) },
            )

            giveawayDetailScreen(
                navController = navController,
                goToWeb = { url, _ -> platformActions.openInApp(url) },
            )

            bundlesScreen(
                navController = navController,
                goToBundle = { bundleId -> navActions.navigateToBundleDetail(bundleId) },
            )

            bundleDetailScreen(
                navController = navController,
                goToWeb = { url, _ -> platformActions.openInApp(url) },
                goToGame = { gameId -> navActions.navigateToGame(gameId) },
            )
        }

        // One shell-level sign-in sheet for every gated action (waitlist/collection/ignore/note), driven by
        // SignInPromptController — replaces the per-screen "go to the Account tab" snackbars.
        SignInPromptHost()
    }
}
