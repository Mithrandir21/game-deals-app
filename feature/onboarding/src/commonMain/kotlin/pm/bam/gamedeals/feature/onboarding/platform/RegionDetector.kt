package pm.bam.gamedeals.feature.onboarding.platform

/**
 * Detects the device's home region as an ISO 3166-1 alpha-2 country code (e.g. "GB"), or `null` when it
 * can't be determined. The onboarding region step uses it to pre-select the storefront region from the
 * device locale instead of silently defaulting to the US. Wrapped in a `fun interface` so the
 * `OnboardingViewModel` can be unit-tested with a fake.
 */
fun interface RegionDetector {
    fun detectCountryCode(): String?
}

/** Platform-backed device-region lookup: Android `Locale`, iOS `NSLocale`. Bound in `onboardingModule`. */
expect fun deviceRegionDetector(): RegionDetector
