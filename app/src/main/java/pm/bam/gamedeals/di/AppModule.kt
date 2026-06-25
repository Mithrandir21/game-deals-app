package pm.bam.gamedeals.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.HttpException
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.Logger as CoilLogger
import okio.Path.Companion.toPath
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.notifications.AndroidNotificationPresenter

val appModule = module {
    single<Clock> { Clock { System.currentTimeMillis() } }

    // Background notification presentation — bound here because the tap PendingIntent targets MainActivity.
    single<NotificationPresenter> { AndroidNotificationPresenter(androidContext(), get()) }

    single<CoilLogger> {
        val logger: Logger = get()
        object : CoilLogger {
            override var minLevel: CoilLogger.Level = CoilLogger.Level.Debug
            override fun log(tag: String, level: CoilLogger.Level, message: String?, throwable: Throwable?) {
                logger.log(coilLogLevel(level, throwable), "CoilLogging", throwable = throwable) { message ?: "Coil Log Message" }
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

/**
 * Maps a Coil log event to an app [LogLevel], downgrading transient image-load failures so they
 * don't reach Sentry as issues. Only genuine decode/bitmap bugs stay at [LogLevel.ERROR]:
 * - cancellations (request abandoned, e.g. navigated away) -> DEBUG (breadcrumb only)
 * - HTTP (404 etc.) and IO/connectivity failures -> WARN (breadcrumb only)
 * - non-error levels pass through unchanged.
 */
internal fun coilLogLevel(level: CoilLogger.Level, throwable: Throwable?): LogLevel =
    if (level != CoilLogger.Level.Error) level.toAppLogLevel()
    else when (throwable) {
        is CancellationException -> LogLevel.DEBUG
        is HttpException, is IOException -> LogLevel.WARN
        null -> LogLevel.WARN
        else -> LogLevel.ERROR
    }
