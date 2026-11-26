package pm.bam.gamedeals.feature.debug.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleTestMode
import pm.bam.gamedeals.feature.debug.generated.resources.Res
import pm.bam.gamedeals.feature.debug.generated.resources.debug_winter_sale_test_row
import pm.bam.gamedeals.feature.debug.generated.resources.debug_winter_sale_test_row_desc

/**
 * Winter Sale test document switch — a [DebugScreen] section.
 *
 * With it on, the Home card, the guide and the reminder read `/events/winter-sale-2026-test.json` instead of the
 * real document, so the whole flow can be tested on a phone while the real document stays off for everyone else.
 * [WinterSaleTestMode] ignores the stored value in release builds, so this is safe even beyond the debug-only entry.
 * The change applies at the next event check, e.g. after reopening the app.
 */
@Composable
internal fun WinterSaleTestSection() {
    val testMode: WinterSaleTestMode = koinInject()
    val scope = rememberCoroutineScope()
    val on by testMode.observe().collectAsState(initial = false)

    ListItem(
        modifier = Modifier.toggleable(value = on, role = Role.Switch) { scope.launch { testMode.set(it) } },
        headlineContent = { Text(stringResource(Res.string.debug_winter_sale_test_row)) },
        supportingContent = { Text(stringResource(Res.string.debug_winter_sale_test_row_desc)) },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    )
}
