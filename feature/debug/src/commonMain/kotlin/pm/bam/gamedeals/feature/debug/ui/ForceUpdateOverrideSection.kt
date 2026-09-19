package pm.bam.gamedeals.feature.debug.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.domain.repositories.appupdate.AppUpdateDebugOverride
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.feature.debug.generated.resources.Res
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_blocking
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_clear
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_apply
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_min_version
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_reset_dismissal
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_reset_dismissal_done
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_row
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_reset_dismissal
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_reset_dismissal_done
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_row_desc
import pm.bam.gamedeals.feature.debug.generated.resources.debug_update_title

/**
 * Minimum-version gate override — a [DebugScreen] section.
 *
 * It exists because builds without a remote flag provider bind `NoOpFeatureFlags` — so `force_update` always
 * resolves to its `false` default and the update dialog could otherwise only ever be seen by a release build
 * talking to a live provider. Setting a minimum version here writes the same JSON a remote flag payload would
 * carry, which `:feature:appupdate` consumes in preference to the (absent) remote one.
 *
 * Reachability is gated by the Account hub's debug entry point (`AppInfo.isDebug`); consumption of whatever
 * this writes is independently gated in `AppUpdateViewModel`, so a release build ignores the stored value even
 * if one somehow exists.
 */
@Composable
internal fun ForceUpdateOverrideSection() {
    val appInfo: AppInfo = koinInject()
    val override: AppUpdateDebugOverride = koinInject()
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { current = override.observe().first() }

    ListItem(
        modifier = Modifier.clickable(role = Role.Button) { showDialog = true },
        headlineContent = { Text(stringResource(Res.string.debug_update_row)) },
        supportingContent = {
            Text(current ?: stringResource(Res.string.debug_update_row_desc))
        },
    )

    if (showDialog) {
        ForceUpdateOverrideDialog(
            currentVersion = appInfo.versionName,
            onApply = { minVersion, blocking ->
                scope.launch {
                    val json = """{"minimum_version":"$minVersion","blocking":$blocking}"""
                    override.set(json)
                    current = json
                }
                showDialog = false
            },
            onClear = {
                scope.launch {
                    override.set(null)
                    current = null
                }
                showDialog = false
            },
            // The nudge silences itself for 24h once dismissed, which makes it impossible to look at twice
            // while developing. Forgetting the timestamp makes it eligible again straight away. Deliberately
            // separate from Apply: re-showing the prompt and changing the config are different intentions.
            onResetDismissal = { scope.launch { settings.clearUpdatePromptDismissedAt() } },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ForceUpdateOverrideDialog(
    currentVersion: String,
    onApply: (minVersion: String, blocking: Boolean) -> Unit,
    onClear: () -> Unit,
    onResetDismissal: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Seeded above the running version so the gate fires immediately on apply — the common case is "show me
    // the dialog", not "work out a version that is higher than mine".
    var minVersion by remember { mutableStateOf("99.0.0") }
    var blocking by remember { mutableStateOf(false) }
    // The reset is fire-and-forget with no visible consequence inside this dialog, so acknowledge it in the
    // label — otherwise there's no way to tell a tap registered.
    var resetConfirmed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.debug_update_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Installed: $currentVersion")
                OutlinedTextField(
                    value = minVersion,
                    onValueChange = { minVersion = it },
                    label = { Text(stringResource(Res.string.debug_update_min_version)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.debug_update_blocking))
                    Switch(checked = blocking, onCheckedChange = { blocking = it })
                }
                HorizontalDivider()
                // Lives in the content rather than a button slot: AlertDialog offers only confirm + dismiss,
                // and those are already Apply and Clear.
                TextButton(
                    onClick = { onResetDismissal(); resetConfirmed = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (resetConfirmed) {
                            stringResource(Res.string.debug_update_reset_dismissal_done)
                        } else {
                            stringResource(Res.string.debug_update_reset_dismissal)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(minVersion.trim(), blocking) },
                enabled = minVersion.isNotBlank(),
            ) { Text(stringResource(Res.string.debug_update_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text(stringResource(Res.string.debug_update_clear)) }
        },
    )
}
