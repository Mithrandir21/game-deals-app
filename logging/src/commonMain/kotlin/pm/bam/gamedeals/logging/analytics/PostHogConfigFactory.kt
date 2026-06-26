package pm.bam.gamedeals.logging.analytics

import io.github.samuolis.posthog.PostHogConfig

/**
 * Builds the shared [PostHogConfig] applied identically on Android and iOS, so policy lives in one place and
 * can't drift between platforms — the analogue of `configureSentryOptions`. Hardcodes the **EU** cloud.
 *
 * Every form of automatic capture is OFF on purpose: this is a Compose app where the SDK's View-hierarchy
 * autocapture can't see tap targets, and turning the SDK's own emitters off guarantees that every event is
 * one we emit through [PostHogAnalytics] — and therefore carries our environment / app-version base props.
 * Session replay is out of scope.
 *
 * Feature flags ARE used (behind the separate `FeatureFlags` seam): `preloadFeatureFlags = true` fetches them
 * on setup, independent of event consent so a gated feature can roll out without forcing analytics opt-in.
 * `sendFeatureFlagEvent = false` is the consent-critical pin — reading a flag must NOT emit a
 * `$feature_flag_called` event, or a flag read would leak an event before the user has opted in.
 *
 * Starts **opted out** (`optOut = true`): EU users must explicitly consent before any product event is sent.
 * The SDK is flipped on at runtime via [Analytics.setConsent] (→ `PostHog.optIn()/optOut()`) once consent is
 * known — see GameDealsApplication/MainViewController `startAnalytics()` and SettingsRepository.setAnalyticsConsent.
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
    // Feature flags: preload on setup so a gated feature can be evaluated without waiting on consent; never let
    // a flag read emit its own event (would bypass the opt-out gate). See class KDoc.
    preloadFeatureFlags = true,
    sendFeatureFlagEvent = false,
    optOut = true,
)
