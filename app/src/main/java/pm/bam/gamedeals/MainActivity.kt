package pm.bam.gamedeals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.navigation.NavGraph

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameDealsTheme {
                NavGraph()
            }
        }
    }
}
