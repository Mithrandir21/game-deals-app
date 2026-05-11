package pm.bam.gamedeals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.rememberPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.navigation.NavGraph

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameDealsTheme {
                CompositionLocalProvider(LocalPlatformActions provides rememberPlatformActions()) {
                    NavGraph()
                }
            }
        }
    }
}
