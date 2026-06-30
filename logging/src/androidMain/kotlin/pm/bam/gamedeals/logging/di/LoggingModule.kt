package pm.bam.gamedeals.logging.di

import org.koin.dsl.module
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsConfig
import pm.bam.gamedeals.logging.analytics.NoOpAnalytics
import pm.bam.gamedeals.logging.analytics.PostHogAnalytics
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.logging.featureflags.NoOpFeatureFlags
import pm.bam.gamedeals.logging.featureflags.PostHogFeatureFlags
import pm.bam.gamedeals.logging.implementations.SentryLoggingListener
import pm.bam.gamedeals.logging.implementations.SimpleLoggingListener

val loggingAndroidModule = module {
    single<Logger> {
        LoggerImpl(mutableSetOf(SimpleLoggingListener(), SentryLoggingListener()))
    }
    // Bound from the app-provided AnalyticsConfig: no key -> NoOp (so callers never null-check), else the
    // PostHog-backed impl. PostHog.setup(...) itself is called by the platform entry point (GameDealsApplication).
    single<Analytics> {
        val config = get<AnalyticsConfig>()
        if (config.apiKey.isEmpty()) NoOpAnalytics else PostHogAnalytics(config.baseProperties())
    }
    // Flags share PostHog's setup but are a separate seam from Analytics. Same key gate: no key -> NoOp (every
    // flag resolves to its catalogue default). refresh() is kicked from the platform entry point after setup.
    single<FeatureFlags> {
        val config = get<AnalyticsConfig>()
        if (config.apiKey.isEmpty()) NoOpFeatureFlags else PostHogFeatureFlags()
    }
}
