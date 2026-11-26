package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.ThemeMode
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_theme_dark
import pm.bam.gamedeals.feature.account.generated.resources.account_theme_light
import pm.bam.gamedeals.feature.account.generated.resources.account_theme_picker_title
import pm.bam.gamedeals.feature.account.generated.resources.account_theme_system

/** The user-facing label for a [ThemeMode] (Light / Dark / System default). */
@Composable
internal fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> Res.string.account_theme_light
        ThemeMode.DARK -> Res.string.account_theme_dark
        ThemeMode.SYSTEM -> Res.string.account_theme_system
    }
)

/**
 * Bottom-sheet theme picker (#193): a radio choice between Light / Dark / System. Selecting one persists
 * the preference and dismisses the sheet; the app root re-themes live (no restart). Mirrors [RegionPickerSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemePickerSheet(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(Res.string.account_theme_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
                .semantics { heading() },
        )
        // Fixed, three-item list — order is intentional (Light, Dark, System), no scrolling needed.
        ThemeMode.entries.forEach { mode ->
            ThemeRow(
                label = themeModeLabel(mode),
                selected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        // Selection/click owned by the row's `selectable`; the RadioButton is a non-interactive visual.
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
