package pm.bam.gamedeals.di

import android.util.Log
import coil.ImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.toLogLevel

val appModule = module {
    single<Clock> { Clock { System.currentTimeMillis() } }

    single<coil.util.Logger> {
        val logger: Logger = get()
        object : coil.util.Logger {
            override var level: Int = Log.DEBUG
            override fun log(tag: String, priority: Int, message: String?, throwable: Throwable?) {
                logger.log(priority.toLogLevel(), "CoilLogging", throwable = throwable) { message ?: "Coil Log Message" }
            }
        }
    }

    single<ImageLoader> {
        ImageLoader.Builder(androidContext())
            .crossfade(true)
            .logger(get())
            .build()
    }
}
