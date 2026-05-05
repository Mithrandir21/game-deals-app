package pm.bam.gamedeals.logging.di

import org.koin.dsl.module
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.implementations.IosConsoleLoggingListener
import pm.bam.gamedeals.logging.implementations.SentryLoggingListener

val loggingIosModule = module {
    single<Logger> {
        LoggerImpl(
            mutableSetOf(
                IosConsoleLoggingListener(),
                SentryLoggingListener(),
            )
        )
    }
}
