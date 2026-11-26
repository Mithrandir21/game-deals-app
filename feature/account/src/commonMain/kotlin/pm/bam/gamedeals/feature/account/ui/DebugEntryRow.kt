package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_row_debug
import pm.bam.gamedeals.feature.account.generated.resources.account_row_debug_desc

/**
 * Account-hub entry point to the developer-tools screen (`:feature:debug`).
 *
 * **Renders nothing at all unless [AppInfo.isDebug]** — the guard is the outermost statement, so a release
 * build never shows the row regardless of anything else. The `Destination.Debug` route stays registered in
 * every build type; gating the entry point rather than the graph keeps the nav graph one shape.
 */
@Composable
internal fun DebugEntryRow(onClick: () -> Unit) {
    val appInfo: AppInfo = koinInject()
    if (!appInfo.isDebug) return

    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(stringResource(Res.string.account_row_debug)) },
        supportingContent = { Text(stringResource(Res.string.account_row_debug_desc)) },
    )
}
