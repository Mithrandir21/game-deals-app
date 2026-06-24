package pm.bam.gamedeals.feature.onboarding.platform

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

actual fun deviceRegionDetector(): RegionDetector = RegionDetector {
    NSLocale.currentLocale.countryCode
}
