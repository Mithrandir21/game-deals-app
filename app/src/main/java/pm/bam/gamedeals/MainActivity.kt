package pm.bam.gamedeals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import org.koin.android.ext.android.inject
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.rememberPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.navigation.NavGraph
import pm.bam.gamedeals.notifications.NotificationRouteBus
import pm.bam.gamedeals.notifications.toNotificationRoute

class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent) // cold-start tap
        setContent {
            GameDealsTheme {
                CompositionLocalProvider(LocalPlatformActions provides rememberPlatformActions()) {
                    // First launch shows the onboarding carousel; thereafter Home. `null` while the (fast)
                    // Storage read is in flight — render nothing for that frame rather than flashing Home
                    // and bouncing into onboarding.
                    val startDestination by produceState<Any?>(initialValue = null) {
                        value = if (settingsRepository.getOnboardingCompleted()) Destination.Home else Destination.Onboarding
                    }
                    startDestination?.let { NavGraph(startDestination = it) }
                }
            }
        }
    }

    // singleTask: a tap while the app is already running re-delivers the launch intent here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Routes a tapped background notification (Phase B) into the Compose nav via [NotificationRouteBus]. */
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.toNotificationRoute()?.let { NotificationRouteBus.deliver(it) }
    }
}
