package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_lowest_ever_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_lowest_ever_label
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The ITAD-style deal list row (UI Improvements board, Phase B): a wide thumbnail, the title and
 * [StoreLabel] stacked on the left, and the [DiscountBadge] + [PriceBlock] on the right, with an
 * optional waitlist heart. It replaces the three near-duplicate rows — `HomeDealRow`, `DealRow`,
 * and `RankedGameRow` — so it deliberately takes **discrete, mostly-optional fields** rather than
 * a `Deal`: ranked games carry only a (nullable) boxart, title, and price, so they simply omit
 * the store/discount/regular-price/heart.
 *
 * Hidden when absent: the store line (no [storeName]), the discount badge ([discountPercent] <= 0,
 * handled by [DiscountBadge]), the regular-price strike-through (handled by [PriceBlock]), the
 * whole price block ([salePrice] null), the "lowest ever" caption ([isLowestEver] false), and the
 * heart ([onToggleWaitlist] null).
 *
 * Accessibility mirrors [DealHeroTile]: the thumbnail + text + price form a single clickable node
 * carrying the caller's [contentDescription], while the heart is a separate, independently
 * actionable sibling node.
 */
@Composable
fun DealListRow(
    title: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    salePrice: String? = null,
    regularPrice: String? = null,
    discountPercent: Int = 0,
    isLowestEver: Boolean = false,
    storeName: String? = null,
    storeIconUrl: String? = null,
    isWaitlisted: Boolean = false,
    onToggleWaitlist: (() -> Unit)? = null,
    addToWaitlistContentDescription: String = "",
    removeFromWaitlistContentDescription: String = "",
) {
    // The "Lowest ever" caption is visual-only on this merged node, so append its spoken equivalent
    // to the row's content description whenever the badge is shown — keeping the badge and its
    // TalkBack announcement in lock-step (#259). Mirrored in DealHeroTile.
    val rowContentDescription = if (salePrice != null && isLowestEver) {
        contentDescription + stringResource(CommonRes.string.deal_lowest_ever_content_suffix)
    } else {
        contentDescription
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button) { onClick() }
                .padding(vertical = GameDealsCustomTheme.spacing.small)
                .semantics(mergeDescendants = true) { this.contentDescription = rowContentDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null, // the row's contentDescription carries the spoken text
                contentScale = ContentScale.Crop,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                modifier = Modifier
                    .width(GameDealsCustomTheme.spacing.rowThumbnailWidth)
                    .height(GameDealsCustomTheme.spacing.rowThumbnailHeight)
                    .clip(MaterialTheme.shapes.small),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (storeName != null) {
                    StoreLabel(storeName = storeName, iconUrl = storeIconUrl)
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
            ) {
                DiscountBadge(discountPercent = discountPercent)
                if (salePrice != null) {
                    PriceBlock(
                        salePrice = salePrice,
                        regularPrice = regularPrice,
                        salePriceStyle = MaterialTheme.typography.titleSmall,
                        caption = if (isLowestEver) {
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
            }
        }
        if (onToggleWaitlist != null) {
            WaitlistHeartButton(
                isWaitlisted = isWaitlisted,
                onToggle = onToggleWaitlist,
                addToWaitlistContentDescription = addToWaitlistContentDescription,
                removeFromWaitlistContentDescription = removeFromWaitlistContentDescription,
            )
        }
    }
}

@Preview
@Composable
private fun DealListRow_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(vertical = GameDealsCustomTheme.spacing.small)) {
                // Deal-style row: store, discount, struck regular price, heart.
                DealListRow(
                    title = "No Man's Sky",
                    contentDescription = "No Man's Sky, on sale for 18,86 €, was 58,99 €, at eTail.Market",
                    onClick = {},
                    imageUrl = "https://example.com/nms.jpg",
                    salePrice = "18,86 €",
                    regularPrice = "58,99 €",
                    discountPercent = 68,
                    isLowestEver = true,
                    storeName = "eTail.Market",
                    isWaitlisted = false,
                    onToggleWaitlist = {},
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
                // Ranked-style row: boxart, title, price only.
                DealListRow(
                    title = "Hollow Knight",
                    contentDescription = "Hollow Knight, 7,49 €",
                    onClick = {},
                    imageUrl = "https://example.com/hk.jpg",
                    salePrice = "7,49 €",
                )
            }
        }
    }
}

@Preview
@Composable
private fun DealListRow_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(vertical = GameDealsCustomTheme.spacing.small)) {
                DealListRow(
                    title = "Monster Hunter Wilds",
                    contentDescription = "Monster Hunter Wilds, on sale for 26,46 €, was 69,99 €, at GamersGate",
                    onClick = {},
                    imageUrl = "https://example.com/mhw.jpg",
                    salePrice = "26,46 €",
                    regularPrice = "69,99 €",
                    discountPercent = 62,
                    storeName = "GamersGate",
                    isWaitlisted = true,
                    onToggleWaitlist = {},
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
            }
        }
    }
}
