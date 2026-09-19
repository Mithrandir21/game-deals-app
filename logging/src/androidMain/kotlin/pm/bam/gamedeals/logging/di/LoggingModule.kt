package pm.bam.gamedeals.logging.di

import org.koin.dsl.module
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.NoOpAnalytics
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.logging.featureflags.NoOpFeatureFlags
import pm.bam.gamedeals.logging.implementations.SentryLoggingListener
import pm.bam.gamedeals.logging.implementations.SimpleLoggingListener

val loggingAndroidModule = module {
    single<Logger> {
        LoggerImpl(mutableSetOf(SimpleLoggingListener(), SentryLoggingListener()))
    }
    // No analytics or remote flag provider is integrated, so both seams bind their NoOp impls: every event is
    // dropped and every flag resolves to its catalogue default. Swap a real provider in here to re-enable.
    single<Analytics> { NoOpAnalytics }
    single<FeatureFlags> { NoOpFeatureFlags }
}
