package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_card_coming
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_card_countdown_day
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_card_starts_days
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_card_starts_hours
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_card_starts_soon
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_on
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_total
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_total_unknown
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_until

/**
 * The sale's blue card, used on Home ([compact]), at the top of the hub, and on the guide's own pages, so the sale
 * looks the same wherever it shows.
 *
 * During the sale the headline is the deal total ("64,000+ deals across 8 stores") with the countdown as a pill; with no
 * total, and before the sale, the countdown itself is the headline and the date becomes the pill. The event file's own
 * text is not shown.
 *
 * The card always has one pill, and [extraChips] (the guide's region) each go on their own row beneath it, so nothing
 * moves when a countdown or date gets longer. On the full card the countdown pill carries the day too ("Ends in 9 days ·
 * 14 Dec"); the compact card keeps the countdown alone.
 */
@Composable
internal fun WinterSaleCard(
    event: WinterSaleEventState.Available,
    totalDeals: Int?,
    storeCount: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    extraChips: @Composable () -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val palette = winterSalePalette()
    val onSale = event.phase == WinterSalePhase.OnSale
    val countdown = if (onSale) {
        countdownText(saleCountdown(event.event.endEpochMillis - event.observedServerNowEpochMillis))
    } else {
        startsText(saleCountdown(event.event.saleStartEpochMillis - event.observedServerNowEpochMillis))
    }
    val total = totalDeals?.takeIf { it > 0 }
    val headline = when {
        !onSale || storeCount <= 0 -> countdown
        total != null -> pluralStringResource(Res.plurals.winter_sale_hub_total, storeCount, roundedDealTotal(total), storeCount)
        else -> pluralStringResource(Res.plurals.winter_sale_hub_total_unknown, storeCount, storeCount)
    }
    val headlineIsCountdown = headline == countdown
    val dateChip = if (onSale) stringResource(Res.string.winter_sale_hub_until, winterSaleTime(event.event.endEpochMillis))
        else winterSaleTime(event.event.saleStartEpochMillis)
    val countdownChip = if (compact) countdown
        else stringResource(Res.string.winter_sale_card_countdown_day, countdown, winterSaleDay(event.event.endEpochMillis))

    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f).padding(if (compact) 16.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AcUnit, contentDescription = null, tint = palette.heroAccent, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
                    Text(
                        stringResource(if (onSale) Res.string.winter_sale_hub_on else Res.string.winter_sale_card_coming),
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        color = palette.heroAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    headline,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (headlineIsCountdown) {
                        SaleChip(dateChip, Icons.Filled.Event, compact)
                    } else {
                        SaleChip(countdownChip, Icons.Filled.Schedule, compact)
                    }
                    extraChips()
                }
                footer()
            }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.onHero,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
    val shape = RoundedCornerShape(if (compact) 16.dp else 20.dp)
    if (onClick != null) {
        Surface(onClick = onClick, color = palette.hero, contentColor = palette.onHero, shape = shape, modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Surface(color = palette.hero, contentColor = palette.onHero, shape = shape, modifier = modifier.fillMaxWidth()) { content() }
    }
}

/** A pill on the blue card: the countdown, a date, or the guide's region. Always one line, so it never changes height. */
@Composable
internal fun SaleChip(text: String, icon: ImageVector?, compact: Boolean = false) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
            Text(
                text,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun startsText(countdown: SaleCountdown): String = when (countdown) {
    is SaleCountdown.Days -> pluralStringResource(Res.plurals.winter_sale_card_starts_days, countdown.days, countdown.days)
    is SaleCountdown.Hours -> pluralStringResource(Res.plurals.winter_sale_card_starts_hours, countdown.hours, countdown.hours)
    SaleCountdown.Soon -> stringResource(Res.string.winter_sale_card_starts_soon)
}
