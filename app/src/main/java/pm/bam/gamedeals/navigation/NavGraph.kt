package pm.bam.gamedeals.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.navigation.NotificationRoute
import pm.bam.gamedeals.common.navigation.NotificationRouteBus
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
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

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
    // Manual product analytics (autocapture is off). Declared here so the notification-tap effect below can
    // record opens; the per-destination screen() effect further down reuses it.
    val analytics: Analytics = koinInject()

    // Route taps on background (OS-tray) notifications into the nav graph: the bundled waitlist summary opens
    // the Notifications list, the bundled followed-franchise summary opens the Followed-series screen. The bus
    // is consume-once, so this won't re-navigate.
    LaunchedEffect(Unit) {
        NotificationRouteBus.routes.collect { route ->
            when (route) {
                NotificationRoute.Notifications -> {
                    analytics.capture(AnalyticsEvents.NOTIFICATION_OPENED, mapOf("route" to "notifications"))
                    navActions.navigateToNotifications()
                }
                NotificationRoute.FollowedSeries -> {
                    analytics.capture(AnalyticsEvents.NOTIFICATION_OPENED, mapOf("route" to "followed_series"))
                    navActions.navigateToFollowedSeries()
                }
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

    // Screen-view analytics: one screen() per destination change. We send the route *template* leaf (e.g. "Game"),
    // never filled args, so no ids leak via the route. Manual because SDK autocapture can't see Compose destinations.
    val currentScreenName = currentDestination?.destination?.route?.let(::screenNameOf)
    LaunchedEffect(currentScreenName) {
        currentScreenName?.let { analytics.screen(it) }
    }

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
        onSearchSubmit = { query ->
            analytics.capture(AnalyticsEvents.SEARCH_PERFORMED, mapOf("query" to query))
            navActions.searchDeals(query)
        },
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
                gameDetailPane = { gameId, canReturnToList, onReturnToList ->
                    GameDetailPane(
                        gameId = gameId,
                        canReturnToList = canReturnToList,
                        onReturnToList = onReturnToList,
                        goToWeb = { url, _ -> platformActions.openInApp(url) },
                        goToBundle = { bundleId -> navActions.navigateToBundleDetail(bundleId) },
                        goToSearchByTitle = { title ->
                            analytics.capture(AnalyticsEvents.SEARCH_PERFORMED, mapOf("query" to title))
                            navActions.searchDeals(title)
                        },
                    )
                },
            )

            discoverScreen(
                navController = navController,
                goToResults = { filter ->
                    analytics.capture(
                        AnalyticsEvents.DISCOVER_SEARCH_PERFORMED,
                        mapOf(
                            "genre_ids" to filter.genreIds.toList(),
                            "theme_ids" to filter.themeIds.toList(),
                            "game_mode_ids" to filter.gameModeIds.toList(),
                            "perspective_ids" to filter.perspectiveIds.toList(),
                            "keyword_ids" to filter.keywordIds.toList(),
                            "tag_count" to (filter.genreIds.size + filter.themeIds.size + filter.gameModeIds.size +
                                filter.perspectiveIds.size + filter.keywordIds.size),
                        ),
                    )
                    navActions.navigateToDiscoverResults(filter)
                },
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
                goToSearchByTitle = { title ->
                    analytics.capture(AnalyticsEvents.SEARCH_PERFORMED, mapOf("query" to title))
                    navActions.searchDeals(title)
                },
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

/**
 * Derives a stable, PII-free screen name from a Nav route. Type-safe routes serialize to a fully-qualified
 * class route like `pm.bam.gamedeals.common.navigation.Destination.Game/{gameId}`; we keep just the leaf
 * ("Game") and drop any argument template, so no filled ids or query text are ever sent to analytics.
 */
private fun screenNameOf(route: String): String =
    route.substringBefore('/').substringBefore('?').substringAfterLast('.')

/**
 * Hosts the full game page in the Deals tablet detail pane via a nested [NavHost] on the [gamePageScreen]
 * route — reusing the page's SavedStateHandle identity + per-game ViewModel scoping without a
 * feature:deals → feature:game dependency. Re-points to the newly selected game as deals are tapped.
 */
@Composable
private fun GameDetailPane(
    gameId: String,
    canReturnToList: Boolean,
    onReturnToList: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToBundle: (bundleId: Int) -> Unit,
    goToSearchByTitle: (title: String) -> Unit,
) {
    val nestedNavController = rememberNavController()
    var currentGameId by remember { mutableStateOf(gameId) }
    // The nested graph is built once, so read these live (not captured) inside the reactive callbacks.
    val canReturnToListNow by rememberUpdatedState(canReturnToList)
    val onReturnToListNow by rememberUpdatedState(onReturnToList)

    NavHost(
        navController = nestedNavController,
        startDestination = Destination.Game(gameId),
    ) {
        gamePageScreen(
            navController = nestedNavController,
            goToWeb = goToWeb,
            goToBundle = goToBundle,
            goToSearchByTitle = goToSearchByTitle,
            // Single-pane portrait hides the list, so warrant a back-to-list arrow there.
            canReturnToList = { canReturnToListNow },
            onExit = { onReturnToListNow() },
        )
    }

    // The start destination seeds the first selection; re-point only when the user picks a different deal.
    LaunchedEffect(gameId) {
        if (gameId != currentGameId) {
            currentGameId = gameId
            nestedNavController.navigate(Destination.Game(gameId)) {
                popUpTo(nestedNavController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
