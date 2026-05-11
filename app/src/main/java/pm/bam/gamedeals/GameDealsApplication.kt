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
import pm.bam.gamedeals.common.di.commonAndroidModule
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.ui.di.commonUiModule
import pm.bam.gamedeals.di.appModule
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.di.domainAndroidModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.search.di.searchModule
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.di.loggingAndroidModule
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.remote.cheapshark.di.cheapsharkNetworkModule
import pm.bam.gamedeals.remote.cheapshark.di.cheapsharkRemoteModule
import pm.bam.gamedeals.remote.di.remoteModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerNetworkModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerRemoteModule

class GameDealsApplication : Application(), SingletonImageLoader.Factory {

    private val imageLoader: ImageLoader by inject()

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
                remoteModule,
                cheapsharkNetworkModule,
                cheapsharkRemoteModule,
                gamerpowerNetworkModule,
                gamerpowerRemoteModule,
                appModule,
                homeModule,
                gameModule,
                giveawaysModule,
                searchModule,
                storeModule
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                get<DomainDatabase>()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                runCatching {
                    val logger: Logger = get()
                    error(logger, throwable = t) { "Failed to warm DomainDatabase" }
                }
            }
        }
    }

    private fun initSentry() {
        if (SENTRY_DSN.isEmpty()) return
        Sentry.init { options ->
            options.dsn = SENTRY_DSN
            options.debug = isDebuggable()
        }
    }

    private companion object {
        const val SENTRY_DSN = ""
    }
}
