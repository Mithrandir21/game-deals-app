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
import pm.bam.gamedeals.domain.repositories.yearindeals.YearInDealsSampleMode
import pm.bam.gamedeals.feature.debug.generated.resources.Res
import pm.bam.gamedeals.feature.debug.generated.resources.debug_year_in_deals_sample_row
import pm.bam.gamedeals.feature.debug.generated.resources.debug_year_in_deals_sample_desc

/**
 * "Year in deals" sample switch — a [DebugScreen] section. With it on, Home shows the year card's entry at any time
 * of year, signed in or not, and the card fills with sample games and numbers, so its look and sharing can be checked
 * on any test phone. [YearInDealsSampleMode] ignores the stored value in release builds.
 */
@Composable
internal fun YearInDealsSampleSection() {
    val testMode: YearInDealsSampleMode = koinInject()
    val scope = rememberCoroutineScope()
    val on by testMode.observe().collectAsState(initial = false)

    ListItem(
        modifier = Modifier.toggleable(value = on, role = Role.Switch) { scope.launch { testMode.set(it) } },
        headlineContent = { Text(stringResource(Res.string.debug_year_in_deals_sample_row)) },
        supportingContent = { Text(stringResource(Res.string.debug_year_in_deals_sample_desc)) },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    )
}
