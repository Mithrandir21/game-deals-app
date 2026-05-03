package pm.bam.gamedeals.di

import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.Logger as CoilLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger

val appModule = module {
    single<Clock> { Clock { System.currentTimeMillis() } }

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
        ImageLoader.Builder(androidContext())
            .crossfade(true)
            .logger(get())
            .components { add(KtorNetworkFetcherFactory()) }
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
