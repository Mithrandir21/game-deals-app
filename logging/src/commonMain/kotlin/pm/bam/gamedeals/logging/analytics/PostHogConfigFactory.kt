package pm.bam.gamedeals.logging.analytics

import io.github.samuolis.posthog.PostHogConfig

/**
 * Builds the shared [PostHogConfig] applied identically on Android and iOS, so policy lives in one place and
 * can't drift between platforms — the analogue of `configureSentryOptions`. Hardcodes the **EU** cloud.
 *
 * Every form of automatic capture is OFF on purpose: this is a Compose app where the SDK's View-hierarchy
 * autocapture can't see tap targets, and turning the SDK's own emitters off guarantees that every event is
 * one we emit through [PostHogAnalytics] — and therefore carries our environment / app-version base props.
 * Feature flags and session replay are out of scope (deferrable behind [Analytics] later).
 *
 * Starts **opted out** (`optOut = true`): EU users must explicitly consent before anything is sent. The SDK
 * is flipped on at runtime via [Analytics.setConsent] (→ `PostHog.optIn()/optOut()`) once consent is known —
 * see GameDealsApplication/MainViewController `startAnalytics()` and SettingsRepository.setAnalyticsConsent.
 *
 * @param apiKey the PostHog `phc_…` project key (callers must ensure it's non-empty before setup).
 * @param debug enables the SDK's verbose logging — we pass the build's debuggable flag.
 */
fun configurePostHog(apiKey: String, debug: Boolean): PostHogConfig = PostHogConfig(
    apiKey = apiKey,
    host = PostHogConfig.HOST_EU,
    debug = debug,
    captureApplicationLifecycleEvents = false,
    captureScreenViews = false,
    captureDeepLinks = false,
    autocapture = false,
    enableExceptionAutocapture = false,
    preloadFeatureFlags = false,
    optOut = true,
)
