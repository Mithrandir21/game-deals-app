package pm.bam.gamedeals.common.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * Reusable composables for the curated Home feed (epic #219, Phase 5.3): a compact stat card, a
 * featured-deal hero tile, and a ranked-game row. They are string-agnostic — callers pass localized
 * [contentDescription] text — so they can live in `:common:ui` and be reused across feeds.
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

/** A featured-deal tile for the hero grid: boxart, title, sale + struck-through original price. */
@Composable
fun HeroGridTile(
    deal: Deal,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(role = Role.Button) { onClick() }
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
    ) {
        Column {
            AsyncImage(
                model = deal.thumb,
                contentDescription = null, // the tile's contentDescription carries the spoken text
                contentScale = ContentScale.Crop,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
            Column(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
            ) {
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deal.salePriceDenominated,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = deal.normalPriceDenominated,
                        style = MaterialTheme.typography.bodySmall.merge(TextStyle(textDecoration = TextDecoration.LineThrough)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A ranked-game row (Most Waitlisted / Most Collected): boxart, title, optional current price. */
@Composable
fun RankedGameRow(
    game: RankedGame,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick() }
            .padding(bottom = GameDealsCustomTheme.spacing.small)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = game.boxart,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            textAlign = TextAlign.Start,
            text = game.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        game.priceDenominated?.let { price ->
            Text(
                modifier = Modifier
                    .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                    .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.extraSmall)
                    .padding(GameDealsCustomTheme.spacing.medium),
                text = price,
            )
        }
    }
}
