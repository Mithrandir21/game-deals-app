package pm.bam.gamedeals.feature.debug.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.repositories.featureflags.FeatureFlagOverrides
import pm.bam.gamedeals.feature.debug.generated.resources.Res
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_desc
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_fetch
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_fetched
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_file
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_now_off
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_now_on
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_off
import pm.bam.gamedeals.feature.debug.generated.resources.debug_switches_on
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags

/**
 * Remote feature switches — a [DebugScreen] section. Each switch can be forced on or off on this phone, or left to
 * follow the switch file; "Fetch now" reads the file at once instead of waiting out the 15-minute gap. Forced values
 * are ignored in release builds ([FeatureFlagOverrides]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeatureSwitchesSection() {
    val featureFlags: FeatureFlags = koinInject()
    val overrides: FeatureFlagOverrides = koinInject()
    val scope = rememberCoroutineScope()
    val forced by overrides.observeOverrides().collectAsState(initial = emptyMap())
    var fetched by remember { mutableStateOf(false) }
    val padding = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large)

    Text(
        text = stringResource(Res.string.debug_switches_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = padding,
    )
    OutlinedButton(
        onClick = { scope.launch { overrides.fetchNow(); fetched = true } },
        modifier = padding,
    ) {
        Text(stringResource(if (fetched) Res.string.debug_switches_fetched else Res.string.debug_switches_fetch))
    }
    FeatureFlag.entries.forEach { flag ->
        val on by featureFlags.observe(flag).collectAsState(initial = featureFlags.isEnabled(flag))
        Column(modifier = padding.padding(vertical = GameDealsCustomTheme.spacing.small)) {
            Text(flag.key, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(if (on) Res.string.debug_switches_now_on else Res.string.debug_switches_now_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val choices = listOf(null to Res.string.debug_switches_file, true to Res.string.debug_switches_on, false to Res.string.debug_switches_off)
            SingleChoiceSegmentedButtonRow {
                choices.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = forced[flag] == value,
                        onClick = { scope.launch { overrides.setOverride(flag, value) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
        }
    }
}
