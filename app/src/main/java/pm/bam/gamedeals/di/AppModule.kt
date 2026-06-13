package pm.bam.gamedeals.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.Logger as CoilLogger
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.notifications.AndroidNotificationPresenter

val appModule = module {
    single<Clock> { Clock { System.currentTimeMillis() } }

    // Background notification presentation (Phase B) — bound here because the tap PendingIntent targets MainActivity.
    single<NotificationPresenter> { AndroidNotificationPresenter(androidContext()) }

    single<CoilLogger> {
        val logger: Logger = get()
        object : CoilLogger {
            override var minLevel: CoilLogger.Level = CoilLogger.Level.Debug
            override fun log(tag: String, level: CoilLogger.Level, message: String?, throwable: Throwable?) {
                logger.log(level.toAppLogLevel(), "CoilLogging", throwable = throwable) { message ?: "Coil Log Message" }
            }
        }
    }

    single<ImageLoader> {
        val context = androidContext()
        ImageLoader.Builder(context)
            .crossfade(true)
            .logger(get())
            .components { add(KtorNetworkFetcherFactory()) }
            // Keep decoded bitmaps in memory so fast scrolling re-shows thumbnails without re-decoding/re-uploading.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            // Persist downloaded images so revisiting Home doesn't re-fetch them over the network.
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

private fun CoilLogger.Level.toAppLogLevel(): LogLevel = when (this) {
    CoilLogger.Level.Verbose -> LogLevel.VERBOSE
    CoilLogger.Level.Debug -> LogLevel.DEBUG
    CoilLogger.Level.Info -> LogLevel.INFO
    CoilLogger.Level.Warn -> LogLevel.WARN
    CoilLogger.Level.Error -> LogLevel.ERROR
}
