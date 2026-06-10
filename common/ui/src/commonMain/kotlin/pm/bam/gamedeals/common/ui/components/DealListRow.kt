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
import pm.bam.gamedeals.common.ui.generated.resources.deal_new_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_store_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_voucher_content_suffix
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
 * whole price block ([salePrice] null), each ITAD flag badge ([hasVoucher] / [isNewHistoricalLow] /
 * [isStoreLow] false), and the heart ([onToggleWaitlist] null).
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
    hasVoucher: Boolean = false,
    isNewHistoricalLow: Boolean = false,
    isStoreLow: Boolean = false,
    storeName: String? = null,
    storeIconUrl: String? = null,
    isWaitlisted: Boolean = false,
    onToggleWaitlist: (() -> Unit)? = null,
    addToWaitlistContentDescription: String = "",
    removeFromWaitlistContentDescription: String = "",
) {
    // The flag badges (new-low / store-low / voucher) are visual-only on this merged node, so append
    // their spoken equivalents to the row's content description — keeping each badge and its TalkBack
    // announcement in lock-step (#259). Mirrored in DealHeroTile.
    val newLowSuffix = stringResource(CommonRes.string.deal_new_low_content_suffix)
    val storeLowSuffix = stringResource(CommonRes.string.deal_store_low_content_suffix)
    val voucherSuffix = stringResource(CommonRes.string.deal_voucher_content_suffix)
    val rowContentDescription = contentDescription + dealBadgeSuffixes(
        isNewHistoricalLow = isNewHistoricalLow,
        isStoreLow = isStoreLow,
        hasVoucher = hasVoucher,
    ).joinToString(separator = "") { suffix ->
        when (suffix) {
            DealBadgeSuffix.NEW_LOW -> newLowSuffix
            DealBadgeSuffix.STORE_LOW -> storeLowSuffix
            DealBadgeSuffix.VOUCHER -> voucherSuffix
        }
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
                // ITAD-style badge cluster: [voucher] [-XX%] [N] [S]. Omitted entirely on ranked rows
                // (no discount or flags) so they keep a clean price-only column.
                if (discountPercent > 0 || hasVoucher || isNewHistoricalLow || isStoreLow) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                    ) {
                        if (hasVoucher) VoucherBadge()
                        DiscountBadge(discountPercent = discountPercent)
                        if (isNewHistoricalLow) NewHistoricalLowBadge()
                        if (isStoreLow) StoreLowBadge()
                    }
                }
                if (salePrice != null) {
                    PriceBlock(
                        salePrice = salePrice,
                        regularPrice = regularPrice,
                        salePriceStyle = MaterialTheme.typography.titleSmall,
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
                // Deal-style row: voucher + discount + new-low badge, struck regular price, heart.
                DealListRow(
                    title = "No Man's Sky",
                    contentDescription = "No Man's Sky, on sale for 18,86 €, was 58,99 €, at eTail.Market",
                    onClick = {},
                    imageUrl = "https://example.com/nms.jpg",
                    salePrice = "18,86 €",
                    regularPrice = "58,99 €",
                    discountPercent = 68,
                    hasVoucher = true,
                    isNewHistoricalLow = true,
                    storeName = "eTail.Market",
                    isWaitlisted = false,
                    onToggleWaitlist = {},
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
                // Store-low badge ("S").
                DealListRow(
                    title = "Resident Evil 2",
                    contentDescription = "Resident Evil 2, on sale for 6,88 €, was 39,99 €, at GreenManGaming",
                    onClick = {},
                    imageUrl = "https://example.com/re2.jpg",
                    salePrice = "6,88 €",
                    regularPrice = "39,99 €",
                    discountPercent = 83,
                    isStoreLow = true,
                    storeName = "GreenManGaming",
                )
                // Ranked-style row: boxart, title, price only (no badges).
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
                    hasVoucher = true,
                    isNewHistoricalLow = true,
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
