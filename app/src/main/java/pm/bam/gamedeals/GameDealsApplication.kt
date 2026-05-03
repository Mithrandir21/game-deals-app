package pm.bam.gamedeals

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.sentry.kotlin.multiplatform.Sentry
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.di.appModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.logging.di.loggingModule

class GameDealsApplication : Application(), ImageLoaderFactory {

    private val imageLoader: ImageLoader by inject()

    override fun onCreate() {
        super.onCreate()
        initSentry()
        startKoin {
            androidContext(this@GameDealsApplication)
            modules(loggingModule, commonModule, domainModule, appModule)
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

    override fun newImageLoader(): ImageLoader = imageLoader

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
