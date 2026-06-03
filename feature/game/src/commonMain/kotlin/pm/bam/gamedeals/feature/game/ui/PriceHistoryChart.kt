package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_range_label

/**
 * Price-history line chart (epic #205, Phase 3 — #208), drawn with Vico.
 *
 * Best-effort enrichment: renders nothing when there are fewer than two points (a line needs at least one
 * segment). The y-axis carries the price; the x-axis is unlabelled — the caption underneath states the
 * tracked time span, which is friendlier than per-tick timestamps for a compact card.
 */
@Composable
internal fun PriceHistoryChart(
    priceHistory: PriceHistory,
    modifier: Modifier = Modifier,
) {
    val points = priceHistory.points
    if (points.size < 2) return

    val prices = remember(points) { points.map { it.priceValue } }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(prices) {
        modelProducer.runTransaction {
            lineModel { series(prices) }
        }
    }

    Column(modifier) {
        Text(
            text = stringResource(Res.string.game_screen_price_history_label),
            style = MaterialTheme.typography.titleSmall,
        )
        CartesianChartHost(
            rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
            ),
            modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = GameDealsCustomTheme.spacing.small),
        )
        Text(
            modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.extraSmall),
            text = stringResource(
                Res.string.game_screen_price_history_range_label,
                formatMonthYear(points.first().timestampEpochMs),
                formatMonthYear(points.last().timestampEpochMs),
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatMonthYear(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date
    return "${MONTH_ABBREV[date.month.ordinal]} ${date.year}"
}

private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
