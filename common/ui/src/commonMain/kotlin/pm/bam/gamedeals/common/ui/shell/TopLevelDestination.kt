package pm.bam.gamedeals.common.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_tab_account
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_tab_deals
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_tab_giveaways
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_tab_home

/**
 * The four bottom-navigation tabs (epic #219). Each maps to a top-level [Destination]; the hosting
 * NavHost (Android `NavGraph` / iOS `AppNavHost`) computes the selected tab from the current route and
 * dispatches taps via `NavigationActions.navigateTopLevel(...)` (wired in Phase 1.1, #224).
 */
enum class TopLevelDestination(
    val destination: Destination,
    val icon: ImageVector,
    val label: StringResource,
) {
    HOME(Destination.Home, Icons.Filled.Home, Res.string.app_shell_tab_home),
    DEALS(Destination.Deals, Icons.Filled.LocalOffer, Res.string.app_shell_tab_deals),
    GIVEAWAYS(Destination.Giveaways, Icons.Filled.CardGiftcard, Res.string.app_shell_tab_giveaways),
    ACCOUNT(Destination.Account, Icons.Filled.AccountCircle, Res.string.app_shell_tab_account),
}
