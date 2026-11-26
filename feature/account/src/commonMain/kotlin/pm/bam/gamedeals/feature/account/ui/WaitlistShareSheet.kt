package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_no_deal
import pm.bam.gamedeals.feature.account.generated.resources.waitlist_share_body
import pm.bam.gamedeals.feature.account.generated.resources.waitlist_share_button
import pm.bam.gamedeals.feature.account.generated.resources.waitlist_share_count
import pm.bam.gamedeals.feature.account.generated.resources.waitlist_share_title

/**
 * Picks up to [ShareLinks.MAX_WISHLIST_GAMES] waitlist games to share as one wishlist link. Rows keep the waitlist's
 * current sort; once the limit is reached the unpicked rows are disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WaitlistShareSheet(
    rows: ImmutableList<WaitlistRowUi>,
    picked: ImmutableList<String>,
    onToggle: (gameId: String) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val max = ShareLinks.MAX_WISHLIST_GAMES
    val full = picked.size >= max
    val noDeal = stringResource(Res.string.account_waitlist_no_deal)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.large)
                .padding(bottom = GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            Text(
                text = stringResource(Res.string.waitlist_share_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.waitlist_share_body, max),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.waitlist_share_count, picked.size, max),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.politeLiveRegion(),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(rows, key = { it.gameId }) { row ->
                    val isPicked = row.gameId in picked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isPicked,
                                enabled = isPicked || !full,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(row.gameId) },
                            )
                            .padding(vertical = GameDealsCustomTheme.spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                    ) {
                        // The row is the toggle; the checkbox only shows its state.
                        Checkbox(checked = isPicked, onCheckedChange = null, enabled = isPicked || !full)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = row.salePrice ?: noDeal,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Button(onClick = onShare, enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.waitlist_share_button))
            }
        }
    }
}
