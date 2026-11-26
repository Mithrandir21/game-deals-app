package pm.bam.gamedeals.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.feature.onboarding.ui.OnboardingScreen

/**
 * First-run onboarding carousel (one-time, replayable from the Account hub). A non-top-level route, so the
 * app shell renders it without the top/bottom bars. [onFinish] leaves the flow — to Home on first run, or
 * back to wherever it was launched from on a replay (handled by the caller).
 */
fun NavGraphBuilder.onboardingScreen(onFinish: () -> Unit) {
    composable<Destination.Onboarding> {
        OnboardingScreen(onFinish = onFinish)
    }
}
