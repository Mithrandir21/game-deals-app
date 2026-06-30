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
import pm.bam.gamedeals.logging.implementations.IosConsoleLoggingListener
import pm.bam.gamedeals.logging.implementations.SentryLoggingListener

val loggingIosModule = module {
    single<Logger> {
        LoggerImpl(mutableSetOf(IosConsoleLoggingListener(), SentryLoggingListener()))
    }
    // Bound from the app-provided AnalyticsConfig: no key -> NoOp, else the PostHog-backed impl. PostHog.setup(...)
    // is called from MainViewController once posthog-ios is linked via SPM (Mac-gated — see docs/posthog-ios-handoff.md).
    single<Analytics> {
        val config = get<AnalyticsConfig>()
        if (config.apiKey.isEmpty()) NoOpAnalytics else PostHogAnalytics(config.baseProperties())
    }
    // Flags share PostHog's setup but are a separate seam from Analytics. Same key gate: no key -> NoOp (every
    // flag resolves to its catalogue default). refresh() is kicked from MainViewController after setup.
    single<FeatureFlags> {
        val config = get<AnalyticsConfig>()
        if (config.apiKey.isEmpty()) NoOpFeatureFlags else PostHogFeatureFlags()
    }
}
