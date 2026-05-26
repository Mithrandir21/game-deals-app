@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_about_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_read_more
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_show_less

/**
 * IGDB-sourced summary shown above the deal list on the game-details screen. Wrapped in a Card
 * with an "About" header for visual separation from the price block and deal rows. Long summaries
 * are clamped to [COLLAPSED_LINES] lines with a Read more / Show less toggle; short summaries
 * (no overflow) suppress the toggle.
 */
@Composable
internal fun IgdbSummarySection(
    summary: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = GameDealsCustomTheme.spacing.large,
                end = GameDealsCustomTheme.spacing.large,
                bottom = GameDealsCustomTheme.spacing.large,
            ),
    ) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            Text(
                text = stringResource(Res.string.game_screen_about_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> if (!expanded) hasOverflow = layout.hasVisualOverflow },
            )
            if (hasOverflow || expanded) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) Res.string.game_screen_summary_show_less
                            else Res.string.game_screen_summary_read_more
                        )
                    )
                }
            }
        }
    }
}

private const val COLLAPSED_LINES = 5

@Preview
@Composable
private fun IgdbSummarySection_ShortSummary_Preview() {
    GameDealsTheme {
        IgdbSummarySection(summary = "A short blurb that fits within five lines easily.")
    }
}

@Preview
@Composable
private fun IgdbSummarySection_LongSummary_Preview() {
    GameDealsTheme {
        IgdbSummarySection(
            summary = "The Master Chief returns in Halo Infinite – the next chapter of the legendary franchise. " +
                "When all hope is lost and humanity's fate hangs in the balance, the Master Chief is ready to confront " +
                "the most ruthless foe he's ever faced. Step inside the armor of humanity's greatest hero to experience " +
                "an epic adventure and explore the massive scale of the Halo ring. Across multiple expansive locations, " +
                "players will engage in iconic combat against a deadly new enemy faction, the Banished. Pilot powerful " +
                "vehicles, wield a sandbox of legendary weapons, and traverse the largest Halo world ever designed."
        )
    }
}
