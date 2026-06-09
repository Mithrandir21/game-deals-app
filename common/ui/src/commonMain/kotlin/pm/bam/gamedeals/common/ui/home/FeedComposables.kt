package pm.bam.gamedeals.common.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme

/**
 * Reusable composables for the curated Home feed (epic #219, Phase 5.3). The deal hero tile and
 * ranked-game row moved to `:common:ui/components` as `DealHeroTile` / `DealListRow` during the
 * ITAD-style UI Improvements work (board project #3); this file now hosts the account [StatCard].
 */

/** A compact metric card (e.g. "Waitlisted: 12") for the account stats strip. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GameDealsCustomTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
