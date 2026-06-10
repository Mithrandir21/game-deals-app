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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsElevation
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import kotlin.math.roundToInt
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_lowest_ever_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_lowest_ever_label
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The ITAD-style featured-deal tile (UI Improvements board, Phase B): wide-aspect banner art
 * on top, with the deal's identity stacked *below* it on the card surface — matching the ITAD
 * website's hero grid. It assembles the Phase-A building blocks ([StoreLabel], [DiscountBadge],
 * [PriceBlock], [WaitlistHeartButton]) over and under the art ([Deal.thumb] already prefers wide
 * banners via `bestArt()`). The only thing left over the art is the waitlist heart, backed by a
 * small top-only scrim so it keeps contrast over bright covers (#257).
 *
 * Accessibility: the art + info form a single clickable node carrying the caller's localized
 * [contentDescription]; the optional waitlist heart is a *separate* sibling node on top (so it
 * stays independently actionable), mirroring the deal bottom sheet. The heart is only shown when
 * [onToggleWaitlist] is provided (e.g. omit it when logged out). The info below the art uses the
 * building blocks' themed colours (like [DealListRow]); only the heart over the scrim is forced light.
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
    // The "Lowest ever" caption is visual-only on this merged node, so append its spoken equivalent
    // to the tile's content description when shown — keeping the badge and its TalkBack announcement
    // in lock-step (#259). Mirrored in DealListRow.
    val tileContentDescription = if (deal.isLowestEver) {
        contentDescription + stringResource(CommonRes.string.deal_lowest_ever_content_suffix)
    } else {
        contentDescription
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = GameDealsElevation.card),
    ) {
        // Outer Box lets the waitlist heart overlay the art's top corner as a separate a11y sibling.
        Box {
            // Clickable content layer (art + info below), merged into a single a11y node.
            Column(
                modifier = Modifier
                    .clickable(role = Role.Button) { onClick() }
                    .semantics(mergeDescendants = true) { this.contentDescription = tileContentDescription },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                ) {
                    AsyncImage(
                        model = deal.thumb,
                        contentDescription = null, // the tile's contentDescription carries the spoken text
                        contentScale = ContentScale.Crop,
                        error = painterResource(CommonRes.drawable.videogame_thumb),
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Top-only scrim, purely so the white heart keeps contrast over bright art (#257).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
                // Info on the card surface, left-aligned and stacked (title / discount+price / store).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(GameDealsCustomTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                ) {
                    Text(
                        text = deal.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                    ) {
                        DiscountBadge(discountPercent = deal.savings.roundToInt())
                        PriceBlock(
                            salePrice = deal.salePriceDenominated,
                            regularPrice = deal.normalPriceDenominated,
                            horizontalAlignment = Alignment.Start,
                            caption = if (deal.isLowestEver) {
                                {
                                    Text(
                                        text = stringResource(CommonRes.string.deal_lowest_ever_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    if (storeName != null) {
                        StoreLabel(
                            storeName = storeName,
                            iconUrl = storeIconUrl,
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
                        isLowestEver = true,
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
