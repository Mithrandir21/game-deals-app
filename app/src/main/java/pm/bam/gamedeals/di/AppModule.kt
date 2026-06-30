package pm.bam.gamedeals.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.BuildConfig
import pm.bam.gamedeals.common.imaging.appCoilLogger
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.notifications.AndroidNotificationPresenter

val appModule = module {
    single<Clock> { Clock { System.currentTimeMillis() } }

    // Background notification presentation — bound here because the tap PendingIntent targets MainActivity.
    single<NotificationPresenter> { AndroidNotificationPresenter(androidContext(), get()) }

    single<ImageLoader> {
        val context = androidContext()
        ImageLoader.Builder(context)
            .crossfade(true)
            .logger(appCoilLogger(get(), debug = BuildConfig.DEBUG))
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
