package pm.bam.gamedeals.feature.onboarding.platform

import java.util.Locale

actual fun deviceRegionDetector(): RegionDetector = RegionDetector {
    Locale.getDefault().country.takeIf { it.isNotBlank() }
}
