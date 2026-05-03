package pm.bam.gamedeals

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import io.sentry.kotlin.multiplatform.Sentry
import javax.inject.Inject

@HiltAndroidApp
class GameDealsApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        initSentry()
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
