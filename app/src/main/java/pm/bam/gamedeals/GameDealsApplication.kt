package pm.bam.gamedeals

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GameDealsApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        if (isDebuggable()) {
            // Surface accidental disk / network I/O on the main thread during development.
            // Logged (not crashed) so dev builds remain usable while regressions are visible
            // in Logcat — production code paths are untouched. Detection is gated on the
            // FLAG_DEBUGGABLE manifest bit (set automatically for the `debug` build type)
            // rather than BuildConfig.DEBUG to avoid having to opt the app module into
            // buildFeatures.buildConfig generation.
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
}
