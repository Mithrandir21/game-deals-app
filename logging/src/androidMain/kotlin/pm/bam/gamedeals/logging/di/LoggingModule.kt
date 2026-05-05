package pm.bam.gamedeals.logging.di

import org.koin.dsl.module
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.implementations.SentryLoggingListener
import pm.bam.gamedeals.logging.implementations.SimpleLoggingListener

val loggingAndroidModule = module {
    single<Logger> {
        LoggerImpl(mutableSetOf(SimpleLoggingListener(), SentryLoggingListener()))
    }
}
