package pm.bam.gamedeals.iosApp

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController
import pm.bam.gamedeals.common.di.commonIosModule
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.navigation.SearchRequestBus
import pm.bam.gamedeals.common.ui.di.commonUiModule
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.rememberPlatformActions
import pm.bam.gamedeals.common.ui.shell.GameDealsAppShell
import pm.bam.gamedeals.common.ui.shell.TopLevelDestination
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.di.domainIosModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.account.di.accountModule
import pm.bam.gamedeals.feature.account.navigation.accountScreen
import pm.bam.gamedeals.feature.account.ui.rememberAccountTabUnreadCount
import pm.bam.gamedeals.feature.bundles.di.bundlesModule
import pm.bam.gamedeals.feature.bundles.navigation.bundleDetailScreen
import pm.bam.gamedeals.feature.bundles.navigation.bundlesScreen
import pm.bam.gamedeals.feature.deals.di.dealsModule
import pm.bam.gamedeals.feature.deals.navigation.dealsScreen
import pm.bam.gamedeals.feature.discover.di.discoverModule
import pm.bam.gamedeals.feature.discover.navigation.discoverResultsScreen
import pm.bam.gamedeals.feature.discover.navigation.discoverScreen
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.game.navigation.gamePageScreen
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.giveaways.navigation.giveawayDetailScreen
import pm.bam.gamedeals.feature.giveaways.navigation.giveawaysScreen
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.home.navigation.homeScreen
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.feature.store.navigation.storeScreen
import pm.bam.gamedeals.feature.webview.navigation.webViewScreen
import pm.bam.gamedeals.logging.di.loggingIosModule
import pm.bam.gamedeals.remote.di.remoteModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerNetworkModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerRemoteModule
import pm.bam.gamedeals.remote.igdb.auth.IgdbCredentials
import pm.bam.gamedeals.remote.igdb.di.igdbNetworkModule
import pm.bam.gamedeals.remote.igdb.di.igdbRemoteModule
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.di.itadIosModule
import pm.bam.gamedeals.remote.itad.di.itadNetworkModule
import pm.bam.gamedeals.remote.itad.di.itadRemoteModule
import pm.bam.gamedeals.remote.logic.RemoteBuildType

@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    bootstrapKoin()
    return ComposeUIViewController { App() }
}

private var koinStarted = false

private fun bootstrapKoin() {
    if (koinStarted) return
    koinStarted = true
    val iosAppModule = module {
        single<Clock> { Clock { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }
        single<ImageLoader> {
            ImageLoader.Builder(PlatformContext.INSTANCE)
                .crossfade(true)
                .components { add(KtorNetworkFetcherFactory()) }
                .build()
        }
        // IGDB creds come from Info.plist keys IGDBClientId / IGDBClientSecret
        single { IgdbCredentials(infoPlistString("IGDBClientId"), infoPlistString("IGDBClientSecret")) }
        // ITAD key comes from Info.plist key ITADApiKey (Secrets.xcconfig: ITAD_API_KEY). Epic #205.
        // OAuth client id from ITADOAuthClientId (Secrets.xcconfig: ITAD_OAUTH_CLIENT_ID). Epic #219, Phase 2.
        single { ItadCredentials(infoPlistString("ITADApiKey"), infoPlistString("ITADOAuthClientId")) }
    }

    startKoin {
        modules(
            loggingIosModule,
            commonModule,
            commonIosModule,
            commonUiModule,
            domainModule,
            domainIosModule,
            remoteModule(currentRemoteBuildType()),
            gamerpowerNetworkModule,
            gamerpowerRemoteModule,
            igdbNetworkModule,
            igdbRemoteModule,
            // ITAD is the live DealsSource (epic #205, Phase 2b); :remote:cheapshark was removed in Phase 4.
            itadNetworkModule,
            itadRemoteModule,
            itadIosModule,
            homeModule,
            gameModule,
            giveawaysModule,
            storeModule,
            bundlesModule,
            accountModule,
            dealsModule,
            discoverModule,
            iosAppModule,
        )
    }

    SingletonImageLoader.setSafe { _ ->
        org.koin.mp.KoinPlatform.getKoin().get()
    }
}

@Composable
private fun App() {
    GameDealsTheme {
        CompositionLocalProvider(LocalPlatformActions provides rememberPlatformActions()) {
            AppNavHost()
        }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val uriHandler = LocalUriHandler.current

    // Top-level (bottom-nav tab) navigation: pop to start saving state, single-top, restore — mirrors
    // the Android `NavigationActions.navigateTopLevel` (epic #219, Phase 1).
    fun navigateTopLevel(destination: Destination) {
        navController.navigate(destination) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val currentDestination by navController.currentBackStackEntryAsState()
    val selectedTab = TopLevelDestination.entries.firstOrNull { tab ->
        currentDestination?.destination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
    }
    val isTab = selectedTab != null

    GameDealsAppShell(
        selectedTab = selectedTab,
        showTopBar = isTab && selectedTab != TopLevelDestination.GIVEAWAYS,
        showBottomBar = isTab,
        onSelectTab = { navigateTopLevel(it.destination) },
        onSearch = {
            navigateTopLevel(Destination.Deals)
            SearchRequestBus.request()
        },
        onBrowseStores = null,
        accountUnreadCount = rememberAccountTabUnreadCount(),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home,
            modifier = Modifier.padding(padding),
        ) {
        homeScreen(
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
            goToGameByTitle = { title -> navController.navigate(Destination.GameDetailsByTitle(title)) },
            goToWaitlist = { navController.navigate(Destination.WaitlistList) },
            goToCollection = { navController.navigate(Destination.CollectionList) },
            goToWeb = { url, _ -> uriHandler.openUri(url) },
            goToBundles = { navController.navigate(Destination.Bundles) },
            goToBundle = { bundleId -> navController.navigate(Destination.BundleDetail(bundleId)) },
        )
        dealsScreen(
            goToWeb = { url, _ -> uriHandler.openUri(url) },
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
            goToDiscover = { navController.navigate(Destination.Discover) },
        )
        discoverScreen(
            navController = navController,
            goToResults = { filter ->
                navController.navigate(
                    Destination.DiscoverResults(
                        genreIds = filter.genreIds.joinToString(","),
                        themeIds = filter.themeIds.joinToString(","),
                        gameModeIds = filter.gameModeIds.joinToString(","),
                        perspectiveIds = filter.perspectiveIds.joinToString(","),
                        keywordIds = filter.keywordIds.joinToString(","),
                    )
                )
            },
        )
        discoverResultsScreen(
            navController = navController,
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
            goToWeb = { url -> uriHandler.openUri(url) },
        )
        accountScreen(
            navController = navController,
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
            goToWeb = { url -> uriHandler.openUri(url) },
        )
        gamePageScreen(
            navController = navController,
            goToWeb = { url, _ -> uriHandler.openUri(url) },
            goToBundle = { bundleId -> navController.navigate(Destination.BundleDetail(bundleId)) },
            goToSearchByTitle = { title ->
                navigateTopLevel(Destination.Deals)
                SearchRequestBus.request(title)
            },
        )
        giveawaysScreen(
            navController = navController,
            goToWeb = { url, _ -> uriHandler.openUri(url) },
            goToGiveawayDetail = { giveawayId -> navController.navigate(Destination.GiveawayDetail(giveawayId)) },
        )
        giveawayDetailScreen(
            navController = navController,
            goToWeb = { url, _ -> uriHandler.openUri(url) },
        )
        bundlesScreen(
            navController = navController,
            goToBundle = { bundleId -> navController.navigate(Destination.BundleDetail(bundleId)) },
        )
        bundleDetailScreen(
            navController = navController,
            goToWeb = { url, _ -> uriHandler.openUri(url) },
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
        )
        storeScreen(
            navController = navController,
            goToWeb = { url, _ -> uriHandler.openUri(url) },
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
        )
        webViewScreen(
            onBack = { navController.popBackStack() },
        )
        }
    }
}

@OptIn(ExperimentalNativeApi::class)
private fun currentRemoteBuildType(): RemoteBuildType =
    if (Platform.isDebugBinary) RemoteBuildType.DEBUG else RemoteBuildType.RELEASE

private fun infoPlistString(key: String): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String).orEmpty()
