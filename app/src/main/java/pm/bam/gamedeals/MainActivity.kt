package pm.bam.gamedeals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.rememberPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.navigation.NavGraph
import pm.bam.gamedeals.notifications.NotificationRouteBus
import pm.bam.gamedeals.notifications.toNotificationRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent) // cold-start tap
        setContent {
            GameDealsTheme {
                CompositionLocalProvider(LocalPlatformActions provides rememberPlatformActions()) {
                    NavGraph()
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
