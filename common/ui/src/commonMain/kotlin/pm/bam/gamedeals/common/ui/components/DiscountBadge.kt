package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme

/**
 * A compact discount pill (e.g. `✂ -78%`) for the ITAD-style deal surfaces (UI Improvements
 * board, Phase A). The app already fetches the discount (`Deal.savings` / ITAD `cut`) but never
 * rendered it; this is the reusable badge that finally surfaces it on tiles and rows.
 *
 * The pill colour is graded by magnitude (see [discountTier]) so deeper cuts read as more
 * prominent, and it adapts to light/dark automatically by mapping each tier onto Material3
 * container roles. It renders **nothing** when [discountPercent] is non-positive.
 *
 * It is string-agnostic: callers pass a localized [contentDescription] (e.g. "78% off"); when
 * omitted the visible `-78%` text carries the semantics.
 */
@Composable
fun DiscountBadge(
    discountPercent: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    showIcon: Boolean = true,
) {
    if (discountPercent <= 0) return

    val colors = badgeColors(discountTier(discountPercent))
    val semanticsModifier = contentDescription?.let { cd ->
        Modifier.clearAndSetSemantics { this.contentDescription = cd }
    } ?: Modifier

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(colors.container)
            .padding(horizontal = GameDealsCustomTheme.spacing.small, vertical = GameDealsCustomTheme.spacing.extraSmall)
            .then(semanticsModifier),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Icon(
                imageVector = Icons.Filled.ContentCut,
                contentDescription = null, // decorative; the pill's text/contentDescription speaks
                tint = colors.onContainer,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = "-$discountPercent%",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onContainer,
        )
    }
}

/** Discount magnitude buckets that drive the badge's colour emphasis. */
internal enum class DiscountTier { LOW, MEDIUM, HIGH }

/** Pure, JVM-testable mapping from a discount percentage to its emphasis [DiscountTier]. */
internal fun discountTier(discountPercent: Int): DiscountTier = when {
    discountPercent >= 50 -> DiscountTier.HIGH
    discountPercent >= 25 -> DiscountTier.MEDIUM
    else -> DiscountTier.LOW
}

private data class BadgeColors(val container: Color, val onContainer: Color)

@Composable
private fun badgeColors(tier: DiscountTier): BadgeColors {
    val scheme = MaterialTheme.colorScheme
    return when (tier) {
        DiscountTier.HIGH -> BadgeColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        DiscountTier.MEDIUM -> BadgeColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        DiscountTier.LOW -> BadgeColors(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

@Preview
@Composable
private fun DiscountBadge_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                DiscountBadge(discountPercent = 12, contentDescription = "12% off")
                DiscountBadge(discountPercent = 37, contentDescription = "37% off")
                DiscountBadge(discountPercent = 78, contentDescription = "78% off")
            }
        }
    }
}

@Preview
@Composable
private fun DiscountBadge_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                DiscountBadge(discountPercent = 12, showIcon = false, contentDescription = "12% off")
                DiscountBadge(discountPercent = 37, contentDescription = "37% off")
                DiscountBadge(discountPercent = 78, contentDescription = "78% off")
            }
        }
    }
}
