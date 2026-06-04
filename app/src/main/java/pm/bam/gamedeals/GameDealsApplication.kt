package pm.bam.gamedeals

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.di.domainAndroidModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.account.di.accountModule
import pm.bam.gamedeals.feature.bundles.di.bundlesModule
import pm.bam.gamedeals.feature.favourites.di.favouritesModule
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.search.di.searchModule
import pm.bam.gamedeals.feature.settings.di.settingsModule
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
     * Application-scoped fire-and-forget work, intended to be reused by future
     * cold-start initializers (e.g. moving `Sentry.init` off the Main thread).
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
                // ITAD is the live DealsSource (epic #205, Phase 2b); :remote:cheapshark was removed in Phase 4.
                itadNetworkModule,
                itadRemoteModule,
                itadAndroidModule,
                module { single { ItadCredentials(BuildConfig.ITAD_API_KEY, BuildConfig.ITAD_OAUTH_CLIENT_ID) } },
                appModule,
                homeModule,
                gameModule,
                giveawaysModule,
                favouritesModule,
                searchModule,
                storeModule,
                settingsModule,
                bundlesModule,
                accountModule,
            )
        }
        warmDomainDatabase()
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
     * Initialise the Sentry SDK off the Main thread.
     *
     * The empty-DSN early-return is kept at the top so debug/CI builds stay
     * zero-cost — we don't even wake [applicationScope] in that case.
     *
     * When a DSN is configured, the synchronous `Sentry.init` bootstrap is
     * dispatched onto [applicationScope] (IO dispatcher) so it doesn't add
     * cold-start cost to `Application.onCreate`. Fire-and-forget: any failure
     * inside `Sentry.init` should not crash the app.
     */
    private fun initSentry() {
        if (SENTRY_DSN.isEmpty()) return
        applicationScope.launch {
            Sentry.init { options ->
                options.dsn = SENTRY_DSN
                options.debug = isDebuggable()
            }
        }
    }

    private companion object {
        const val SENTRY_DSN = ""
    }
}

private fun currentRemoteBuildType(): RemoteBuildType = when (BuildConfig.BUILD_TYPE) {
    "release" -> RemoteBuildType.RELEASE
    else -> RemoteBuildType.DEBUG
}
