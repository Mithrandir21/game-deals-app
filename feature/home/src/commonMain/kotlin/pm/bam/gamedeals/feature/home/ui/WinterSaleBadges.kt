package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.repositories.wintersale.WINTER_SALE_BIG_CUT_PERCENT
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealFlag
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_badge_all_time_low
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_badge_new_low
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_badge_store_low
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_badge_store_low_at
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_spoken_all_time_low
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_spoken_new_low
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_spoken_store_low

/**
 * The sale area's own colours: an icy blue for price lows and a warm orange for big cuts, on top of the app theme.
 * There are no Material roles for these, so light and dark come from the surface's lightness (as the deal badges do).
 * Each text colour keeps at least 4.5:1 against what it sits on.
 */
internal data class WinterSalePalette(
    val ice: Color,
    val onIce: Color,
    val iceLine: Color,
    val ribbon: Color,
    val onRibbon: Color,
    val hero: Color,
    val onHero: Color,
    val heroAccent: Color,
)

@Composable
internal fun winterSalePalette(): WinterSalePalette =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        WinterSalePalette(
            ice = Color(0xFF8FD3FF), onIce = Color(0xFF0E1626), iceLine = Color(0xFF8FD3FF),
            ribbon = Color(0xFFFF9F43), onRibbon = Color(0xFF1A1206),
            hero = Color(0xFF16365A), onHero = Color(0xFFEEF3FA), heroAccent = Color(0xFF8FD3FF),
        )
    } else {
        WinterSalePalette(
            ice = Color(0xFFCDEBFF), onIce = Color(0xFF06304D), iceLine = Color(0xFF0B5C8A),
            ribbon = Color(0xFFFF9F43), onRibbon = Color(0xFF1A1206),
            hero = Color(0xFFDCEFFF), onHero = Color(0xFF0A2540), heroAccent = Color(0xFF0B5C8A),
        )
    }

/**
 * The price badge for a deal's ITAD flag. Cards say "Store low"; list rows have room to name the store
 * ([storeName]). Decorative: the card or row speaks it through [spokenFlag].
 */
@Composable
internal fun WinterSaleFlagBadge(flag: WinterSaleDealFlag, modifier: Modifier = Modifier, storeName: String? = null) {
    val palette = winterSalePalette()
    when (flag) {
        WinterSaleDealFlag.NewLow -> Row(
            modifier = modifier.clip(RoundedCornerShape(50)).background(palette.ice).padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = palette.onIce, modifier = Modifier.size(11.dp))
            Text(stringResource(Res.string.winter_sale_badge_new_low), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = palette.onIce)
        }
        WinterSaleDealFlag.AllTimeLow -> Row(
            modifier = modifier.border(BorderStroke(1.dp, palette.iceLine), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = palette.iceLine, modifier = Modifier.size(11.dp))
            Text(stringResource(Res.string.winter_sale_badge_all_time_low), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = palette.iceLine)
        }
        WinterSaleDealFlag.StoreLow -> Text(
            text = storeName?.let { stringResource(Res.string.winter_sale_badge_store_low_at, it) }
                ?: stringResource(Res.string.winter_sale_badge_store_low),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** The cut as a bold orange ribbon at [WINTER_SALE_BIG_CUT_PERCENT] or more, otherwise a quiet tag. */
@Composable
internal fun WinterSaleCutBadge(cutPercent: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    if (cutPercent <= 0) return
    val palette = winterSalePalette()
    if (cutPercent >= WINTER_SALE_BIG_CUT_PERCENT) {
        Text(
            text = "-$cutPercent%",
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = palette.onRibbon,
            modifier = modifier.background(palette.ribbon, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                text = "-$cutPercent%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** The flag's spoken form, for the content description of the card or row it sits on. */
@Composable
internal fun spokenFlag(flag: WinterSaleDealFlag?): String? = when (flag) {
    WinterSaleDealFlag.NewLow -> stringResource(Res.string.winter_sale_spoken_new_low)
    WinterSaleDealFlag.AllTimeLow -> stringResource(Res.string.winter_sale_spoken_all_time_low)
    WinterSaleDealFlag.StoreLow -> stringResource(Res.string.winter_sale_spoken_store_low)
    null -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
private fun WinterSaleBadges_Preview() {
    GameDealsTheme {
        Surface {
            Column(Modifier.padding(GameDealsCustomTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WinterSaleFlagBadge(WinterSaleDealFlag.NewLow)
                    WinterSaleFlagBadge(WinterSaleDealFlag.AllTimeLow)
                    WinterSaleFlagBadge(WinterSaleDealFlag.StoreLow)
                    WinterSaleFlagBadge(WinterSaleDealFlag.StoreLow, storeName = "Steam")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WinterSaleCutBadge(60)
                    WinterSaleCutBadge(90)
                    WinterSaleCutBadge(90, large = true)
                }
            }
        }
    }
}
