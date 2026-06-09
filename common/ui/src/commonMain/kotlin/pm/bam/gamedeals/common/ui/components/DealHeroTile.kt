package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsElevation
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import kotlin.math.roundToInt
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The ITAD-style featured-deal tile (UI Improvements board, Phase B): wide-aspect banner art
 * with a vertical scrim and the deal's identity overlaid at the bottom — replacing the old
 * `HeroGridTile`, which stacked plain text *below* a cropped image. It assembles the Phase-A
 * building blocks ([StoreLabel], [DiscountBadge], [PriceBlock], [WaitlistHeartButton]) over the
 * art ([Deal.thumb] already prefers wide banners via `bestArt()`).
 *
 * Accessibility: the art + scrim + info form a single clickable node carrying the caller's
 * localized [contentDescription]; the optional waitlist heart is a *separate* sibling node on
 * top (so it stays independently actionable), mirroring the deal bottom sheet. The heart is only
 * shown when [onToggleWaitlist] is provided (e.g. omit it when logged out). Overlay text uses
 * fixed light colours because it always sits over the dark scrim regardless of theme.
 */
@Composable
fun DealHeroTile(
    deal: Deal,
    storeName: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    storeIconUrl: String? = null,
    isWaitlisted: Boolean = false,
    onToggleWaitlist: (() -> Unit)? = null,
    addToWaitlistContentDescription: String = "",
    removeFromWaitlistContentDescription: String = "",
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = GameDealsElevation.card),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            // Clickable content layer (art + scrim + info), merged into a single a11y node.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(role = Role.Button) { onClick() }
                    .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            ) {
                AsyncImage(
                    model = deal.thumb,
                    contentDescription = null, // the tile's contentDescription carries the spoken text
                    contentScale = ContentScale.Crop,
                    error = painterResource(CommonRes.drawable.videogame_thumb),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f), // darken top for the heart
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f), // darken bottom for the info
                                ),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(GameDealsCustomTheme.spacing.small),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                    ) {
                        Text(
                            text = deal.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (storeName != null) {
                            StoreLabel(
                                storeName = storeName,
                                iconUrl = storeIconUrl,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                    ) {
                        DiscountBadge(discountPercent = deal.savings.roundToInt())
                        PriceBlock(
                            salePrice = deal.salePriceDenominated,
                            regularPrice = deal.normalPriceDenominated,
                            salePriceColor = Color.White,
                            regularPriceColor = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            if (onToggleWaitlist != null) {
                WaitlistHeartButton(
                    isWaitlisted = isWaitlisted,
                    onToggle = onToggleWaitlist,
                    addToWaitlistContentDescription = addToWaitlistContentDescription,
                    removeFromWaitlistContentDescription = removeFromWaitlistContentDescription,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

@Preview
@Composable
private fun DealHeroTile_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.width(220.dp).padding(GameDealsCustomTheme.spacing.medium)) {
                DealHeroTile(
                    deal = PreviewDeal.copy(
                        title = "Resident Evil 4",
                        salePriceDenominated = "8,60 €",
                        normalPriceDenominated = "39,99 €",
                        savings = 78.0,
                    ),
                    storeName = "GreenManGaming",
                    contentDescription = "Resident Evil 4, on sale for 8,60 €, was 39,99 €",
                    onClick = {},
                    isWaitlisted = true,
                    onToggleWaitlist = {},
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
            }
        }
    }
}

@Preview
@Composable
private fun DealHeroTile_NoHeart_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.width(220.dp).padding(GameDealsCustomTheme.spacing.medium)) {
                DealHeroTile(
                    deal = PreviewDeal.copy(
                        title = "Expedition 33",
                        salePriceDenominated = "31,25 €",
                        normalPriceDenominated = "49,99 €",
                        savings = 37.0,
                    ),
                    storeName = "Playsum",
                    contentDescription = "Expedition 33, on sale for 31,25 €, was 49,99 €",
                    onClick = {},
                )
            }
        }
    }
}
