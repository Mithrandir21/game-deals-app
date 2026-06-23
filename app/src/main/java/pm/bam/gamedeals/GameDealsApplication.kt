package pm.bam.gamedeals

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pm.bam.gamedeals.common.di.commonAndroidModule
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.ui.di.commonUiModule
import pm.bam.gamedeals.di.appModule
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.di.domainAndroidModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.cache.CacheMaintenance
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.scheduling.applyLibraryLifecycle
import pm.bam.gamedeals.domain.scheduling.applyNotificationLifecycle
import pm.bam.gamedeals.feature.account.di.accountModule
import pm.bam.gamedeals.feature.bundles.di.bundlesModule
import pm.bam.gamedeals.feature.deals.di.dealsModule
import pm.bam.gamedeals.feature.discover.di.discoverModule
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.onboarding.di.onboardingModule
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.di.loggingAndroidModule
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.remote.di.remoteModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerNetworkModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerRemoteModule
import pm.bam.gamedeals.remote.igdb.auth.IgdbCredentials
import pm.bam.gamedeals.remote.igdb.di.igdbNetworkModule
import pm.bam.gamedeals.remote.igdb.di.igdbRemoteModule
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.di.itadAndroidModule
import pm.bam.gamedeals.remote.itad.di.itadNetworkModule
import pm.bam.gamedeals.remote.itad.di.itadRemoteModule
import pm.bam.gamedeals.remote.logic.RemoteBuildType

class GameDealsApplication : Application(), SingletonImageLoader.Factory {

    private val imageLoader: ImageLoader by inject()
    private val logger: Logger by inject()

    /**
     * Application-scoped fire-and-forget work: DB warm-up, cache maintenance, the
     * auth-driven lifecycles, and attaching the Sentry user once Koin is up.
     *
     * Backed by a [SupervisorJob] so one failed child doesn't tear down siblings,
     * and pinned to [Dispatchers.IO] since the typical use case is I/O-bound
     * warm-up. Not cancelled manually — the [Application] lives for the entire
     * process lifetime, so the scope dies with the process.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initSentry()
        startKoin {
            androidContext(this@GameDealsApplication)
            modules(
                loggingAndroidModule,
                commonModule,
                commonAndroidModule,
                commonUiModule,
                domainModule,
                domainAndroidModule,
                remoteModule(currentRemoteBuildType()),
                gamerpowerNetworkModule,
                gamerpowerRemoteModule,
                igdbNetworkModule,
                igdbRemoteModule,
                module { single { IgdbCredentials(BuildConfig.IGDB_CLIENT_ID, BuildConfig.IGDB_CLIENT_SECRET) } },
                // ITAD is the live DealsSource.
                itadNetworkModule,
                itadRemoteModule,
                itadAndroidModule,
                module { single { ItadCredentials(BuildConfig.ITAD_API_KEY, BuildConfig.ITAD_OAUTH_CLIENT_ID) } },
                appModule,
                homeModule,
                gameModule,
                giveawaysModule,
                storeModule,
                bundlesModule,
                accountModule,
                dealsModule,
                discoverModule,
                onboardingModule,
            )
        }
        attachSentryUser()
        warmDomainDatabase()
        runCacheMaintenance()
        runNotificationLifecycle()
        runLibraryLifecycle()
        if (isDebuggable()) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Resolve [DomainDatabase] from Koin on a background thread. Without this, the
     * first DAO request — which happens during `HomeScreen`'s composition via
     * `koinViewModel()` — drives the singleton `RoomDatabase.Builder.build()` call
     * (and any pending destructive migrations) on the Main thread during the first
     * frame.
     *
     * `:app` doesn't have `androidx.room` on its compile classpath, so we can't dot
     * into `db.openHelper.writableDatabase` here. Resolving the singleton is enough
     * to force `.build()` — the actual SQLite connection then opens lazily on the
     * first DAO query, which already runs on a coroutine dispatcher.
     *
     * Fire-and-forget on a [SupervisorJob]-backed [Dispatchers.IO] scope: failures
     * here must never crash the app, since the regular DAO call path will surface
     * real errors with their proper context. [CancellationException] is rethrown so
     * structured concurrency stays honest if the scope is ever cancelled.
     */
    private fun warmDomainDatabase() {
        applicationScope.launch {
            try {
                get<DomainDatabase>()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                try {
                    error(logger, throwable = t) { "Failed to warm DomainDatabase" }
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Run startup cache maintenance (Phase 8) off the Main thread: the `cacheSchemaVersion` guard and the
     * eviction sweep over the ITAD caches. Fire-and-forget on the [applicationScope] (mirrors
     * [warmDomainDatabase]) — maintenance is best-effort and must never crash the app or block cold start;
     * [CancellationException] is rethrown so structured concurrency stays honest.
     */
    private fun runCacheMaintenance() {
        applicationScope.launch {
            try {
                get<CacheMaintenance>().runStartupMaintenance()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                try {
                    error(logger, throwable = t) { "Cache maintenance failed" }
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Keeps the background notification poll in sync with auth + the user's opt-in (background-notifications
     * feature, Phase D). Observing [AuthTokenStore.observeAuthState] re-arms the [NotificationScheduler] on
     * login when enabled (also covers app start / reboot since the StateFlow emits the current state), and on
     * logout cancels the poll and clears the surfaced-id set so a different account re-alerts cleanly. The
     * user's opt-in preference itself is preserved. Fire-and-forget on [applicationScope]; per-emission
     * failures are logged but never tear down the collector.
     */
    private fun runNotificationLifecycle() {
        applicationScope.launch {
            get<AuthTokenStore>().observeAuthState().collect { state ->
                try {
                    applyNotificationLifecycle(state, get(), get(), get())
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    try {
                        error(logger, throwable = t) { "Notification lifecycle update failed" }
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    /**
     * Keeps the user's ITAD library (waitlist / collection / ignored) in sync with auth, app-wide. Owning this
     * here — rather than in `AccountViewModel` — means the Room-backed id sets are reconciled on login no matter
     * which entry point signed the user in (the Account tab, the global sign-in sheet, or onboarding) or which
     * tab is on screen, and are wiped on logout. Observing [AuthTokenStore.observeAuthState] also covers app
     * start (the StateFlow emits the current state). `distinctUntilChangedBy { it is LoggedIn }` collapses the
     * two `LoggedIn` emissions a fresh login produces (blank-then-real username) into a single reconcile.
     * Fire-and-forget on [applicationScope]; per-emission failures are logged but never tear down the collector.
     */
    private fun runLibraryLifecycle() {
        applicationScope.launch {
            get<AuthTokenStore>().observeAuthState()
                .distinctUntilChangedBy { it is AuthState.LoggedIn }
                .collect { state ->
                    try {
                        applyLibraryLifecycle(state, get(), get(), get(), get())
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        try {
                            error(logger, throwable = t) { "Library lifecycle update failed" }
                        } catch (_: Throwable) {
                        }
                    }
                }
        }
    }

    /**
     * Initialise the Sentry SDK as the very first thing in [onCreate], synchronously, so the crash and
     * ANR handlers are armed before any other startup work can throw. `sentry-android` init is only a few
     * milliseconds, so the cold-start cost is negligible and worth paying for startup-crash coverage.
     *
     * Gated to release builds with a configured DSN: debuggable builds early-return (zero cost, and our
     * own dev/test runs never pollute the production dashboard). The R8 mapping that de-obfuscates these
     * traces is uploaded by the Sentry Gradle plugin — see app/build.gradle.kts.
     */
    private fun initSentry() {
        if (isDebuggable() || BuildConfig.SENTRY_DSN.isEmpty()) return
        Sentry.init { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = "production"
            options.release = "pm.bam.gamedeals@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.dist = BuildConfig.VERSION_CODE.toString()
            // sentry-android-core's automatic activity/app-start transactions ride this. Start at 1.0 to
            // validate tracing end-to-end, then dial down (~0.2) once production volume is understood.
            options.tracesSampleRate = TRACES_SAMPLE_RATE
            options.sendDefaultPii = false
            options.debug = false
            // Defence-in-depth: strip query strings off HTTP breadcrumb URLs so an ITAD api key / OAuth
            // token can never reach Sentry via a `?key=…` query param. (Headers are already excluded by
            // sendDefaultPii = false.)
            options.beforeBreadcrumb = { breadcrumb -> scrubBreadcrumb(breadcrumb) }
        }
    }

    /** Strips the query string from HTTP breadcrumb URLs; passes every other breadcrumb through untouched. */
    private fun scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
        if (breadcrumb.category == "http") {
            (breadcrumb.getData()?.get("url") as? String)?.let { url ->
                breadcrumb.setData("url", url.substringBefore('?'))
            }
        }
        return breadcrumb
    }

    /**
     * Attaches an anonymised, stable install id as the Sentry user so issues can be grouped by
     * "users affected" without shipping any PII. The id comes from [SettingsRepository] (so it needs
     * Koin), hence this runs after [startKoin]; the crash handler is already armed from [initSentry], so
     * a crash in the gap is still captured, just without the id. No-op when Sentry is disabled (debug /
     * no DSN). Fire-and-forget on [applicationScope]; failures are logged but never crash the app.
     */
    private fun attachSentryUser() {
        if (!Sentry.isEnabled()) return
        applicationScope.launch {
            try {
                val installId = get<SettingsRepository>().getInstallId()
                Sentry.setUser(User().apply { id = installId })
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                try {
                    error(logger, throwable = t) { "Failed to attach Sentry user" }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private companion object {
        /**
         * Transaction sampling for performance tracing. Start at 1.0 to confirm transactions flow, then
         * lower (~0.2) once production volume is understood.
         */
        const val TRACES_SAMPLE_RATE = 1.0
    }
}

private fun currentRemoteBuildType(): RemoteBuildType = when (BuildConfig.BUILD_TYPE) {
    "release" -> RemoteBuildType.RELEASE
    else -> RemoteBuildType.DEBUG
}
