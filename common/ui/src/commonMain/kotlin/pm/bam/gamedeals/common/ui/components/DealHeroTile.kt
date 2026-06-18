package pm.bam.gamedeals.common.ui.components

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
import pm.bam.gamedeals.domain.models.hero
import kotlin.math.roundToInt
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_new_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_store_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_voucher_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The ITAD-style featured-deal tile (UI Improvements board, Phase B): wide-aspect banner art
 * on top, with the deal's identity stacked *below* it on the card surface — matching the ITAD
 * website's hero grid. It assembles the Phase-A building blocks ([StoreLabel], [DiscountBadge],
 * [PriceBlock]) over and under the art.
 *
 * The art slot is `16:9`, fed by [Deal.artwork]'s [hero] accessor (ITAD's `banner600`, 600×344 ≈ `16:9`)
 * rather than the [thumbnail][pm.bam.gamedeals.domain.models.thumbnail] (`banner300`, 300×140) the list
 * rows use: at full card width the 300px thumb looked soft, and its 2.14:1 ratio left `ContentScale.Crop`
 * slicing the sides. `banner600` is both crisper and the right shape, so the card stops cropping and stays sharp.
 *
 * The interactive waitlist heart (and the full-image scrim it needed for contrast) was retired —
 * toggling now lives in the `GamePeekSheet`. The only thing over the art is now the small **passive**
 * [GameStateBadges], shown at the top corner only when the game is [isWaitlisted] and/or [isCollected];
 * the art is otherwise undimmed.
 *
 * Accessibility: the art + info form a single clickable node carrying the caller's localized
 * [contentDescription], extended with the badge suffixes ([gameStateContentSuffix]). The info below
 * the art uses the building blocks' themed colours (like [DealListRow]).
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
    isCollected: Boolean = false,
) {
    // The flag badges (new-low / store-low / voucher) are visual-only on this merged node, so append
    // their spoken equivalents to the tile's content description, keeping each badge and its TalkBack
    // announcement in lock-step. Mirrored in DealListRow.
    val newLowSuffix = stringResource(CommonRes.string.deal_new_low_content_suffix)
    val storeLowSuffix = stringResource(CommonRes.string.deal_store_low_content_suffix)
    val voucherSuffix = stringResource(CommonRes.string.deal_voucher_content_suffix)
    val tileContentDescription = contentDescription + dealBadgeSuffixes(
        isNewHistoricalLow = deal.isNewHistoricalLow,
        isStoreLow = deal.isStoreLow,
        hasVoucher = deal.hasVoucher,
    ).joinToString(separator = "") { suffix ->
        when (suffix) {
            DealBadgeSuffix.NEW_LOW -> newLowSuffix
            DealBadgeSuffix.STORE_LOW -> storeLowSuffix
            DealBadgeSuffix.VOUCHER -> voucherSuffix
        }
    } + gameStateContentSuffix(isWaitlisted = isWaitlisted, isCollected = isCollected)
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
                        model = deal.artwork.hero,
                        contentDescription = null, // the tile's contentDescription carries the spoken text
                        contentScale = ContentScale.Crop,
                        error = painterResource(CommonRes.drawable.videogame_thumb),
                        modifier = Modifier.fillMaxSize(),
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
                    // ITAD-style badge cluster + price: [voucher] [-XX%] [N] [S]  price.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                    ) {
                        if (deal.hasVoucher) VoucherBadge()
                        DiscountBadge(discountPercent = deal.savings.roundToInt())
                        if (deal.isNewHistoricalLow) NewHistoricalLowBadge()
                        if (deal.isStoreLow) StoreLowBadge()
                        PriceBlock(
                            salePrice = deal.salePriceDenominated,
                            regularPrice = deal.normalPriceDenominated,
                            horizontalAlignment = Alignment.Start,
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
            // Passive waitlist/collection status over the art's top corner (no interactive heart, so
            // no full-image scrim needed); the compact pill keeps it legible over bright covers.
            GameStateBadges(
                isWaitlisted = isWaitlisted,
                isCollected = isCollected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(GameDealsCustomTheme.spacing.small),
            )
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
                        isNewHistoricalLow = true,
                        hasVoucher = true,
                    ),
                    storeName = "GreenManGaming",
                    contentDescription = "Resident Evil 4, on sale for 8,60 €, was 39,99 €",
                    onClick = {},
                    isWaitlisted = true,
                    isCollected = true,
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
                        hasVoucher = true,
                    ),
                    storeName = "Playsum",
                    contentDescription = "Expedition 33, on sale for 31,25 €, was 49,99 €",
                    onClick = {},
                )
            }
        }
    }
}
