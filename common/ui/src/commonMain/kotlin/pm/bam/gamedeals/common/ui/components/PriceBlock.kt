package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme

/**
 * The single source of truth for deal pricing on the ITAD-style surfaces (UI Improvements
 * board, Phase A): a prominent, colour-emphasised sale price with the regular price struck
 * through beside it, plus an optional trailing [caption] slot (Phase E's historical-low badge
 * plugs in here).
 *
 * It replaces the ad-hoc price text currently duplicated in `HeroGridTile`, `HomeDealRow`,
 * `DealRow`, and `RankedGameRow`. The regular price is hidden when it is null or equal to the
 * sale price (no discount), so non-discounted items don't show a pointless strike-through.
 *
 * Colours/styles/alignment are parameterised so the same component reads correctly in a list
 * row and overlaid on a hero scrim (where callers pass light colours). String-agnostic: callers
 * may pass a localized [contentDescription]; otherwise the visible prices carry the semantics.
 */
@Composable
fun PriceBlock(
    salePrice: String,
    modifier: Modifier = Modifier,
    regularPrice: String? = null,
    salePriceStyle: TextStyle = MaterialTheme.typography.titleMedium,
    salePriceColor: Color = MaterialTheme.colorScheme.primary,
    regularPriceColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    contentDescription: String? = null,
    caption: (@Composable () -> Unit)? = null,
) {
    val semanticsModifier = contentDescription?.let { cd ->
        Modifier.clearAndSetSemantics { this.contentDescription = cd }
    } ?: Modifier

    Column(
        modifier = modifier.then(semanticsModifier),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = salePrice,
                style = salePriceStyle,
                color = salePriceColor,
            )
            if (regularPrice != null && regularPrice != salePrice) {
                Text(
                    text = regularPrice,
                    style = MaterialTheme.typography.bodySmall,
                    color = regularPriceColor,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
        caption?.invoke()
    }
}

@Preview
@Composable
private fun PriceBlock_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            ) {
                PriceBlock(salePrice = "8,60 €", regularPrice = "39,99 €")
                PriceBlock(salePrice = "19,99 €") // no discount -> no strike-through
                PriceBlock(
                    salePrice = "10,22 €",
                    regularPrice = "21,99 €",
                    caption = {
                        Text(
                            text = "Lowest ever",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PriceBlock_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            ) {
                PriceBlock(
                    salePrice = "6,88 €",
                    regularPrice = "39,99 €",
                    horizontalAlignment = Alignment.Start,
                )
                PriceBlock(
                    salePrice = "31,25 €",
                    regularPrice = "49,99 €",
                    // e.g. over a hero scrim
                    salePriceColor = MaterialTheme.colorScheme.onSurface,
                    regularPriceColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
