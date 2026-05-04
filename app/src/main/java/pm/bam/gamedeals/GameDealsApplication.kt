package pm.bam.gamedeals

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.sentry.kotlin.multiplatform.Sentry
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pm.bam.gamedeals.common.di.commonAndroidModule
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.di.appModule
import pm.bam.gamedeals.domain.di.domainAndroidModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.search.di.searchModule
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.logging.di.loggingModule
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
                loggingModule,
                commonModule,
                commonAndroidModule,
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
