package pm.bam.gamedeals.logging.di

import org.koin.dsl.module
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.NoOpAnalytics
import pm.bam.gamedeals.logging.implementations.IosConsoleLoggingListener
import pm.bam.gamedeals.logging.implementations.SentryLoggingListener

val loggingIosModule = module {
    single<Logger> {
        LoggerImpl(mutableSetOf(IosConsoleLoggingListener(), SentryLoggingListener()))
    }
    // No analytics provider is integrated, so every event is dropped. FeatureFlags is bound in :domain's module
    // (the switch-file reader), which needs storage and the network.
    single<Analytics> { NoOpAnalytics }
}
