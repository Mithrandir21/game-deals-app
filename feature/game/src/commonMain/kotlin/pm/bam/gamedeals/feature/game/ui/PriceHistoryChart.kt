@file:OptIn(ExperimentalTime::class)

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.PriceHistory.PricePoint
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_a11y
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_msrp
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_range_1y
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_range_3m
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_price_history_range_all

/**
 * Price-history chart (epic #205, Phase 3 — #208; enriched per the chart-improvements work).
 *
 * Renders nothing when there are fewer than two points (a line needs at least one segment). When
 * there are, it plots a **time-proportional** step line (price holds until the next change), with:
 *  - a **3M / 1Y / All** range toggle ([PriceHistoryRange]),
 *  - a **press-and-drag tooltip** showing the date, price, discount, and shop at a point,
 *  - an **MSRP** reference line and highlighted **all-time-low** / **current** points,
 *  - real **date axis ticks**, and a spoken **content description** for TalkBack.
 *
 * The heavy lifting (windowing, anchoring, formatting) lives in [PriceHistoryChartData] so it can be
 * unit-tested without a Compose/Vico runtime.
 */
@Composable
internal fun PriceHistoryChart(
    priceHistory: PriceHistory,
    modifier: Modifier = Modifier,
) {
    val allPoints = priceHistory.points
    if (allPoints.size < 2) return

    var range by rememberSaveable { mutableStateOf(PriceHistoryRange.ALL) }
    val nowEpochMs = remember { Clock.System.now().toEpochMilliseconds() }
    val windowed = remember(allPoints, range, nowEpochMs) {
        windowedPriceHistory(allPoints, range, nowEpochMs)
    }

    Column(modifier) {
        Text(
            text = stringResource(Res.string.game_screen_price_history_label),
            style = MaterialTheme.typography.titleSmall,
        )
        PriceHistoryRangeSelector(
            selected = range,
            onSelected = { range = it },
            modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.small),
        )
        if (windowed.size < 2) {
            Text(
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.medium),
                text = stringResource(Res.string.game_screen_price_history_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            PriceHistoryGraph(
                points = windowed,
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.small),
            )
        }
    }
}

@Composable
private fun PriceHistoryRangeSelector(
    selected: PriceHistoryRange,
    onSelected: (PriceHistoryRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        PriceHistoryRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelected(range) },
                label = { Text(stringResource(range.labelRes())) },
            )
        }
    }
}

@Composable
private fun PriceHistoryGraph(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
) {
    val xs = remember(points) { points.map { it.timestampEpochMs.toEpochDay().toDouble() } }
    val ys = remember(points) { points.map { it.priceValue } }
    val pointByDay = remember(points) {
        points.associateBy { it.timestampEpochMs.toEpochDay().toDouble() }
    }
    val lowX = remember(points) { lowestPoint(points)?.timestampEpochMs?.toEpochDay()?.toDouble() }
    val currentX = remember(points) { points.last().timestampEpochMs.toEpochDay().toDouble() }
    val msrp = remember(points) { latestRegular(points) }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(xs, ys) {
        modelProducer.runTransaction { lineModel { series(xs, ys) } }
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val lowColor = MaterialTheme.colorScheme.tertiary

    val pointProvider = run {
        val lowDot = LineCartesianLayer.Point(rememberShapeComponent(fill = Fill(lowColor), shape = CircleShape), HIGHLIGHT_POINT_SIZE)
        val currentDot = LineCartesianLayer.Point(rememberShapeComponent(fill = Fill(lineColor), shape = CircleShape), HIGHLIGHT_POINT_SIZE)
        remember(lowX, currentX, lowDot, currentDot) {
            HighlightPointProvider(lowX, currentX, lowDot, currentDot)
        }
    }

    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
        pointProvider = pointProvider,
        interpolator = StepInterpolator,
    )
    val layer = rememberLineCartesianLayer(lineProvider = LineCartesianLayer.LineProvider.series(line))

    val markerLabel = rememberTextComponent(
        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface),
        lineCount = 2,
        padding = Insets(horizontal = GameDealsCustomTheme.spacing.small, vertical = GameDealsCustomTheme.spacing.extraSmall),
        background = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(MARKER_CORNER_RADIUS),
        ),
    )
    val markerFormatter = remember(pointByDay) { PriceTooltipFormatter(pointByDay) }
    val guideline = rememberLineComponent(fill = Fill(lineColor.copy(alpha = 0.4f)), thickness = 1.dp)
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = markerFormatter,
        guideline = guideline,
    )

    val decorations: List<Decoration> = if (msrp != null) {
        val msrpLine = rememberLineComponent(fill = Fill(MaterialTheme.colorScheme.outline), thickness = 1.dp)
        val msrpLabelComponent = rememberTextComponent(
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline),
        )
        val msrpText = stringResource(Res.string.game_screen_price_history_msrp)
        listOf(
            remember(msrp, msrpLine, msrpLabelComponent, msrpText) {
                HorizontalLine(
                    y = { msrp },
                    line = msrpLine,
                    labelComponent = msrpLabelComponent,
                    label = { msrpText },
                )
            }
        )
    } else {
        emptyList()
    }

    val spacingDays = remember(xs) {
        ((xs.last() - xs.first()) / X_AXIS_TARGET_LABELS).toInt().coerceAtLeast(1)
    }
    val bottomAxis = HorizontalAxis.rememberBottom(
        valueFormatter = remember {
            CartesianValueFormatter { _, value, _ -> formatMonthYear(value.toLong().epochDayToMillis()) }
        },
        itemPlacer = remember(spacingDays) { HorizontalAxis.ItemPlacer.aligned(spacing = { spacingDays }) },
        guideline = null,
    )

    val chart = rememberCartesianChart(
        layer,
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = bottomAxis,
        marker = marker,
        decorations = decorations,
        getXStep = { _, _, _ -> 1.0 },
    )

    val contentDescription = stringResource(
        Res.string.game_screen_price_history_a11y,
        points.first().priceDenominated,
        formatMonthYear(points.first().timestampEpochMs),
        points.last().priceDenominated,
        (lowestPoint(points) ?: points.last()).priceDenominated,
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

/** Step-after interpolation: the price holds at the prior level until the next change, then jumps. */
private object StepInterpolator : LineCartesianLayer.Interpolator {
    override fun interpolate(
        context: CartesianDrawingContext,
        path: Path,
        points: List<Offset>,
        visibleIndexRange: IntRange,
    ) {
        for (index in visibleIndexRange) {
            val point = points[index]
            if (index == visibleIndexRange.first) {
                path.moveTo(point.x, point.y)
            } else {
                val previous = points[index - 1]
                path.lineTo(point.x, previous.y) // hold the prior price up to this point's x
                path.lineTo(point.x, point.y) // then step to the new price
            }
        }
    }
}

/** Draws a dot only at the all-time-low ([lowX]) and current ([currentX]) points; nothing elsewhere. */
private class HighlightPointProvider(
    private val lowX: Double?,
    private val currentX: Double?,
    private val lowPoint: LineCartesianLayer.Point,
    private val currentPoint: LineCartesianLayer.Point,
) : LineCartesianLayer.PointProvider {
    override fun getPoint(
        entry: com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineCartesianLayer.Point? =
        when (entry.x) {
            currentX -> currentPoint // current takes precedence when it is also the low
            lowX -> lowPoint
            else -> null
        }

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = currentPoint
}

/** Builds the scrub tooltip: "12 Mar 2024" / "$14.99 · -75% · Steam", looked up by day x-value. */
private class PriceTooltipFormatter(
    private val pointByDay: Map<Double, PricePoint>,
) : DefaultCartesianMarker.ValueFormatter {
    override fun format(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ): CharSequence {
        val point = targets.firstOrNull()?.let { pointByDay[it.x] } ?: return ""
        val cut = if (point.cutPercent > 0) " · -${point.cutPercent}%" else ""
        val shop = point.shopName?.let { " · $it" }.orEmpty()
        return "${formatFullDate(point.timestampEpochMs)}\n${point.priceDenominated}$cut$shop"
    }
}

private fun PriceHistoryRange.labelRes() = when (this) {
    PriceHistoryRange.THREE_MONTHS -> Res.string.game_screen_price_history_range_3m
    PriceHistoryRange.ONE_YEAR -> Res.string.game_screen_price_history_range_1y
    PriceHistoryRange.ALL -> Res.string.game_screen_price_history_range_all
}

private val CHART_HEIGHT = 200.dp
private val HIGHLIGHT_POINT_SIZE = 8.dp
private val MARKER_CORNER_RADIUS = 4.dp
private const val X_AXIS_TARGET_LABELS = 4.0
