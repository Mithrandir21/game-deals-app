package pm.bam.gamedeals.iosApp

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import io.github.samuolis.posthog.PostHog
import io.github.samuolis.posthog.PostHogContext
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.User
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController
import pm.bam.gamedeals.common.di.commonIosModule
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.imaging.appCoilLogger
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.navigation.NotificationRoute
import pm.bam.gamedeals.common.navigation.NotificationRouteBus
import pm.bam.gamedeals.common.navigation.SearchController
import pm.bam.gamedeals.common.ui.di.commonUiModule
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.rememberPlatformActions
import pm.bam.gamedeals.common.ui.shell.GameDealsAppShell
import pm.bam.gamedeals.common.ui.shell.TopLevelDestination
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.di.domainIosModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.scheduling.applyLibraryLifecycle
import pm.bam.gamedeals.domain.scheduling.applyNotificationLifecycle
import pm.bam.gamedeals.feature.account.di.accountModule
import pm.bam.gamedeals.feature.account.navigation.accountScreen
import pm.bam.gamedeals.feature.account.ui.SignInPromptHost
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
import pm.bam.gamedeals.feature.onboarding.di.onboardingModule
import pm.bam.gamedeals.feature.onboarding.navigation.onboardingScreen
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.feature.store.navigation.storeScreen
import pm.bam.gamedeals.feature.webview.navigation.webViewScreen
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsConfig
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.logging.analytics.configurePostHog
import pm.bam.gamedeals.logging.configureSentryOptions
import pm.bam.gamedeals.logging.di.loggingIosModule
import pm.bam.gamedeals.logging.error
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

/** Swift-facing: start Koin early (idempotent) so a background launch has a graph before the Compose UI exists. */
fun startKoinIfNeeded() = bootstrapKoin()

/**
 * Swift-facing: arm Sentry's crash/perf handlers. Call first in `AppDelegate.didFinishLaunching`, before Koin,
 * so a startup crash is still captured. Release/dist come from the bundle; the shared policy lives in
 * [configureSentryOptions].
 *
 * Gated to release builds with a provisioned DSN, mirroring Android's `initSentry()`: debug binaries early-return
 * (zero cost, and our own dev/test runs never pollute the production dashboard), as does a build with no DSN
 * (local/dev without `Secrets.xcconfig`).
 *
 * Not named `initSentry`: Kotlin/Native mangles any `init*` top-level function to `doInit*` in the ObjC/Swift API.
 */
@OptIn(ExperimentalNativeApi::class)
fun startSentry() {
    if (Platform.isDebugBinary) return
    val dsn = infoPlistString("SentryDsn")
    if (dsn.isEmpty()) return
    val shortVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"
    val build = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "0"
    Sentry.init { options ->
        configureSentryOptions(
            options = options,
            dsn = dsn,
            release = "pm.bam.gamedeals@$shortVersion+$build",
            dist = build,
            tracesSampleRate = SENTRY_TRACES_SAMPLE_RATE,
        )
    }
}

/**
 * Swift-facing: initialise PostHog for product analytics. Call from `AppDelegate.didFinishLaunching` (like
 * [startSentry]), but enabled in ALL variants for now — only an empty key early-returns, matching Android. Requires
 * posthog-ios linked via SPM — see docs/posthog-ios-handoff.md. The SDK emits nothing on its own (see
 * [configurePostHog]); every event flows through our Analytics wrapper, which stamps the environment.
 */
@OptIn(ExperimentalNativeApi::class)
fun startPostHog() {
    val apiKey = infoPlistString("PostHogApiKey")
    if (apiKey.isEmpty()) return
    PostHog.setup(
        config = configurePostHog(apiKey = apiKey, debug = Platform.isDebugBinary),
        context = PostHogContext(),
    )
}

/** Swift-facing: a tapped notification's `userInfo[route]` → the shared bus the nav host collects. */
fun deliverNotificationRoute(routeKey: String?) {
    NotificationRoute.fromKey(routeKey)?.let { NotificationRouteBus.deliver(it) }
}

// Transaction sampling for performance tracing — mirrors Android; dial to ~0.2 once production volume is known.
private const val SENTRY_TRACES_SAMPLE_RATE = 1.0

private var koinStarted = false
private var notificationLifecycleStarted = false
private val notificationLifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private var libraryLifecycleStarted = false
private val libraryLifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val sentryUserScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val analyticsUserScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun bootstrapKoin() {
    if (koinStarted) return
    koinStarted = true
    val iosAppModule = module {
        single<Clock> { Clock { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }
        single<ImageLoader> {
            ImageLoader.Builder(PlatformContext.INSTANCE)
                .crossfade(true)
                .logger(appCoilLogger(get(), debug = Platform.isDebugBinary))
                .components { add(KtorNetworkFetcherFactory()) }
                .build()
        }
        // IGDB creds come from Info.plist keys IGDBClientId / IGDBClientSecret
        single { IgdbCredentials(infoPlistString("IGDBClientId"), infoPlistString("IGDBClientSecret")) }
        // ITAD key comes from Info.plist key ITADApiKey (Secrets.xcconfig: ITAD_API_KEY).
        // OAuth client id from ITADOAuthClientId (Secrets.xcconfig: ITAD_OAUTH_CLIENT_ID).
        single { ItadCredentials(infoPlistString("ITADApiKey"), infoPlistString("ITADOAuthClientId")) }
        // PostHog analytics config — key from Info.plist PostHogApiKey (Secrets.xcconfig: POSTHOG_API_KEY). An empty
        // key -> NoOp binding, so the shared Koin graph (WaitlistRepository et al.) resolves even before posthog-ios
        // is linked via SPM. Read by loggingIosModule's Analytics binding.
        single {
            AnalyticsConfig(
                apiKey = infoPlistString("PostHogApiKey"),
                environment = analyticsEnvironment(),
                appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0",
            )
        }
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
            // ITAD is the live DealsSource.
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
            onboardingModule,
            iosAppModule,
        )
    }

    SingletonImageLoader.setSafe { _ ->
        org.koin.mp.KoinPlatform.getKoin().get()
    }

    attachSentryUser()
    startAnalytics()
    startNotificationLifecycle()
    startLibraryLifecycle()
}

/**
 * Attaches the anonymised, stable install id as the Sentry user so issues group by "users affected" without any
 * PII — mirrors Android's `attachSentryUser`. The id comes from [SettingsRepository] (needs Koin), hence this runs
 * after `startKoin`; the crash handler is already armed from [startSentry]. No-op when Sentry is disabled (no DSN).
 */
private fun attachSentryUser() {
    if (!Sentry.isEnabled()) return
    sentryUserScope.launch {
        try {
            val installId = KoinPlatform.getKoin().get<SettingsRepository>().getInstallId()
            Sentry.setUser(User().apply { id = installId })
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            runCatching { error(KoinPlatform.getKoin().get<Logger>(), t) { "Failed to attach Sentry user." } }
        }
    }
}

/**
 * Applies the persisted analytics consent at launch — mirrors Android's `startAnalytics`. The SDK is set up
 * **opted out** ([configurePostHog]); this only opts in, identifies (anonymised install id, correlates with
 * Sentry, no PII) and emits the launch event when the user has consented (GDPR), otherwise it stays opted out
 * and sends nothing. Needs Koin (SettingsRepository), so runs after `startKoin`. No-op when analytics is
 * disabled (the binding is NoOp). Fire-and-forget; failures are logged, never crash.
 */
private fun startAnalytics() {
    analyticsUserScope.launch {
        try {
            val koin = KoinPlatform.getKoin()
            val settings = koin.get<SettingsRepository>()
            if (settings.getAnalyticsConsent()) {
                val analytics = koin.get<Analytics>()
                analytics.setConsent(true) // optIn the freshly-opted-out SDK
                analytics.identify(settings.getInstallId())
                analytics.capture(AnalyticsEvents.APP_OPENED)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            runCatching { error(KoinPlatform.getKoin().get<Logger>(), t) { "Failed to start analytics." } }
        }
    }
}

/** "debug"/"release" for the analytics environment tag — mirrors Android's isDebuggable() split. */
@OptIn(ExperimentalNativeApi::class)
private fun analyticsEnvironment(): String = if (Platform.isDebugBinary) "debug" else "release"

/**
 * Mirrors Android's `GameDealsApplication.runNotificationLifecycle`: reconcile the background poll with auth +
 * opt-in on every launch (incl. a background launch) and clear the surfaced-id set on logout. The auth StateFlow
 * emits the current state immediately, so this also re-arms a dropped BGAppRefresh chain after a reboot/force-quit.
 */
private fun startNotificationLifecycle() {
    if (notificationLifecycleStarted) return
    notificationLifecycleStarted = true
    val koin = KoinPlatform.getKoin()
    notificationLifecycleScope.launch {
        koin.get<AuthTokenStore>().observeAuthState().collect { state ->
            try {
                applyNotificationLifecycle(state, koin.get(), koin.get(), koin.get())
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                runCatching { error(koin.get<Logger>(), t) { "Notification lifecycle update failed." } }
            }
        }
    }
}

/**
 * Mirrors Android's `GameDealsApplication.runLibraryLifecycle`: reconcile the user's ITAD library (waitlist /
 * collection / ignored) into Room on login — regardless of which entry point signed the user in or which tab is
 * alive — and wipe it on logout. The auth StateFlow emits the current state immediately, so this also syncs on
 * launch; `distinctUntilChangedBy { it is LoggedIn }` collapses the two `LoggedIn` emissions a fresh login
 * produces into a single reconcile.
 */
private fun startLibraryLifecycle() {
    if (libraryLifecycleStarted) return
    libraryLifecycleStarted = true
    val koin = KoinPlatform.getKoin()
    libraryLifecycleScope.launch {
        koin.get<AuthTokenStore>().observeAuthState()
            .distinctUntilChangedBy { it is AuthState.LoggedIn }
            .collect { state ->
                try {
                    applyLibraryLifecycle(state, koin.get(), koin.get(), koin.get(), koin.get())
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    runCatching { error(koin.get<Logger>(), t) { "Library lifecycle update failed." } }
                }
            }
    }
}

@Composable
private fun App() {
    GameDealsTheme {
        CompositionLocalProvider(LocalPlatformActions provides rememberPlatformActions()) {
            // First launch shows the onboarding carousel; thereafter Home. `null` while the (fast) Storage read
            // is in flight — render nothing rather than flashing Home and bouncing into onboarding.
            val startDestination by produceState<Destination?>(initialValue = null) {
                val settings = KoinPlatform.getKoin().get<SettingsRepository>()
                value = if (settings.getOnboardingCompleted()) Destination.Home else Destination.Onboarding
            }
            startDestination?.let { AppNavHost(startDestination = it) }
        }
    }
}

@Composable
private fun AppNavHost(startDestination: Destination) {
    val navController = rememberNavController()
    val uriHandler = LocalUriHandler.current

    // Route taps on background (OS-tray) notifications into the nav graph — mirrors the Android NavGraph.
    // The bus is consume-once, so this won't re-navigate.
    LaunchedEffect(Unit) {
        NotificationRouteBus.routes.collect { route ->
            when (route) {
                NotificationRoute.Notifications -> navController.navigate(Destination.Notifications)
                NotificationRoute.FollowedSeries -> navController.navigate(Destination.FollowedSeriesList)
            }
        }
    }

    // Top-level (bottom-nav tab) navigation: pop to start saving state, single-top, restore — mirrors
    // the Android `NavigationActions.navigateTopLevel`.
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

    // The shared toolbar's search field reflects the active query (null = browse mode), kept in sync via
    // SearchController so a search started anywhere (toolbar submit or a deep-link) is shown there.
    val activeSearchQuery by SearchController.activeQuery.collectAsState()

    GameDealsAppShell(
        selectedTab = selectedTab,
        showTopBar = isTab,
        showBottomBar = isTab,
        onSelectTab = { navigateTopLevel(it.destination) },
        activeSearchQuery = activeSearchQuery,
        onSearchSubmit = { query ->
            navigateTopLevel(Destination.Deals)
            SearchController.search(query)
        },
        onSearchClosed = { SearchController.clear() },
        onBrowseStores = null,
        accountUnreadCount = rememberAccountTabUnreadCount(),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
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
            onReplayOnboarding = { navController.navigate(Destination.Onboarding) },
        )
        onboardingScreen(
            // Replay pops back to where it was launched from; first run (nothing behind it) opens Home and
            // drops Onboarding so a back press exits the app — mirrors Android's NavigationActions.finishOnboarding.
            onFinish = {
                if (!navController.popBackStack()) {
                    navController.navigate(Destination.Home) {
                        popUpTo(Destination.Onboarding) { inclusive = true }
                    }
                }
            },
        )
        gamePageScreen(
            navController = navController,
            goToWeb = { url, _ -> uriHandler.openUri(url) },
            goToBundle = { bundleId -> navController.navigate(Destination.BundleDetail(bundleId)) },
            goToSearchByTitle = { title ->
                navigateTopLevel(Destination.Deals)
                SearchController.search(title)
            },
        )
        giveawaysScreen(
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

        // One shell-level sign-in sheet for every gated action (waitlist/collection/ignore/note), driven by
        // SignInPromptController — mirrors the Android NavGraph.
        SignInPromptHost()
    }
}

@OptIn(ExperimentalNativeApi::class)
private fun currentRemoteBuildType(): RemoteBuildType =
    if (Platform.isDebugBinary) RemoteBuildType.DEBUG else RemoteBuildType.RELEASE

private fun infoPlistString(key: String): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String).orEmpty()
