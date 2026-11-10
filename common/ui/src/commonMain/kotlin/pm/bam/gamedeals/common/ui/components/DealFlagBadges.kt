package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.theme.allTimeLowBadgeContentDark
import pm.bam.gamedeals.common.ui.theme.allTimeLowBadgeContentLight
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerDark
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerLight
import pm.bam.gamedeals.common.ui.theme.onNewLowBadgeContainerDark
import pm.bam.gamedeals.common.ui.theme.onNewLowBadgeContainerLight
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_all_time_low_badge_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_all_time_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_new_low_badge_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_new_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_store_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_voucher_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_store_low_badge_label

/**
 * The ITAD-style deal flag badges (deal-badge work) that sit in a cluster around the [DiscountBadge]
 * on the deal tiles/rows, mirroring the ITAD website: an orange **"N"** for a brand-new historical
 * low, an orange-outlined **"H"** for a price at its all-time low, a neutral **"S"** for a
 * store-specific low, and a **scissors** icon when the deal needs a voucher. They reuse
 * [DiscountBadge]'s compact chip styling so the whole cluster reads as one set; [DealBadgeCluster]
 * keeps them next to the discount wherever it sits.
 *
 * Inside the merged deal node each badge is decorative — the caller leaves [contentDescription] null
 * and the tile/row appends the spoken equivalent to its own content description (see
 * [dealBadgeSuffixes]). Pass a [contentDescription] only when using a badge standalone.
 */
@Composable
fun NewHistoricalLowBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = newLowBadgeColors()
    BadgeChip(container = colors.container, modifier = modifier, contentDescription = contentDescription) {
        Text(
            text = stringResource(CommonRes.string.deal_new_low_badge_label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onContainer,
        )
    }
}

@Composable
fun StoreLowBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    BadgeChip(
        container = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
        contentDescription = contentDescription,
    ) {
        Text(
            text = stringResource(CommonRes.string.deal_store_low_badge_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AllTimeLowBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val content = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) allTimeLowBadgeContentDark else allTimeLowBadgeContentLight
    BadgeChip(
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.border(1.dp, content, MaterialTheme.shapes.small),
        contentDescription = contentDescription,
    ) {
        Text(
            text = stringResource(CommonRes.string.deal_all_time_low_badge_label),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
fun VoucherBadge(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    BadgeChip(
        container = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
        contentDescription = contentDescription,
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCut,
            contentDescription = null, // decorative; the merged deal node carries the spoken text
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(GameDealsCustomTheme.spacing.badgeIcon),
        )
    }
}

/**
 * A deal's voucher, discount and low-price letter as one group, so the letter always sits right next to
 * the discount: beside the price on rows, on the art's corner on tiles and cards ([onArt], which adds a
 * small shadow so the chips stay readable over bright art). One letter at most ([dealLowFlag]).
 * Renders nothing when there is nothing to show.
 */
@Composable
fun DealBadgeCluster(
    discountPercent: Int,
    modifier: Modifier = Modifier,
    hasVoucher: Boolean = false,
    isNewHistoricalLow: Boolean = false,
    isLowestEver: Boolean = false,
    isStoreLow: Boolean = false,
    onArt: Boolean = false,
) {
    val lowFlag = dealLowFlag(isNewHistoricalLow = isNewHistoricalLow, isLowestEver = isLowestEver, isStoreLow = isStoreLow)
    if (discountPercent <= 0 && !hasVoucher && lowFlag == null) return
    val chip = if (onArt) Modifier.shadow(elevation = 2.dp, shape = MaterialTheme.shapes.small) else Modifier
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (onArt) GameDealsCustomTheme.spacing.small else GameDealsCustomTheme.spacing.extraSmall),
    ) {
        if (hasVoucher) VoucherBadge(chip)
        DiscountBadge(discountPercent = discountPercent, modifier = chip)
        when (lowFlag) {
            DealLowFlag.NEW_LOW -> NewHistoricalLowBadge(chip)
            DealLowFlag.ALL_TIME_LOW -> AllTimeLowBadge(chip)
            DealLowFlag.STORE_LOW -> StoreLowBadge(chip)
            null -> Unit
        }
    }
}

/** Shared chip container matching [DiscountBadge]'s styling (clip + background + dense padding). */
@Composable
private fun BadgeChip(
    container: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val semanticsModifier = contentDescription?.let { cd ->
        Modifier.clearAndSetSemantics { this.contentDescription = cd }
    } ?: Modifier
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = GameDealsCustomTheme.spacing.small, vertical = GameDealsCustomTheme.spacing.extraSmall)
            .then(semanticsModifier),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

private data class FlagBadgeColors(val container: Color, val onContainer: Color)

/**
 * The orange new-low palette. There is no Material3 role for orange, so the tokens live outside the
 * `ColorScheme`; light/dark is chosen from the active scheme's surface luminance (rather than
 * `isSystemInDarkTheme()`) so forced-dark previews resolve correctly.
 */
@Composable
private fun newLowBadgeColors(): FlagBadgeColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        FlagBadgeColors(newLowBadgeContainerDark, onNewLowBadgeContainerDark)
    } else {
        FlagBadgeColors(newLowBadgeContainerLight, onNewLowBadgeContainerLight)
    }
}

/**
 * The spoken form of a deal's badges, appended to its merged content description so TalkBack says what
 * [DealBadgeCluster] shows (e.g. ", at its lowest price ever, voucher available").
 */
@Composable
fun dealBadgeContentSuffix(
    isNewHistoricalLow: Boolean,
    isLowestEver: Boolean,
    isStoreLow: Boolean,
    hasVoucher: Boolean,
): String {
    val newLow = stringResource(CommonRes.string.deal_new_low_content_suffix)
    val allTimeLow = stringResource(CommonRes.string.deal_all_time_low_content_suffix)
    val storeLow = stringResource(CommonRes.string.deal_store_low_content_suffix)
    val voucher = stringResource(CommonRes.string.deal_voucher_content_suffix)
    return dealBadgeSuffixes(isNewHistoricalLow = isNewHistoricalLow, isStoreLow = isStoreLow, hasVoucher = hasVoucher, isLowestEver = isLowestEver)
        .joinToString(separator = "") { suffix ->
            when (suffix) {
                DealBadgeSuffix.NEW_LOW -> newLow
                DealBadgeSuffix.ALL_TIME_LOW -> allTimeLow
                DealBadgeSuffix.STORE_LOW -> storeLow
                DealBadgeSuffix.VOUCHER -> voucher
            }
        }
}

/** A deal badge whose visual presence has a spoken (TalkBack) equivalent on the merged deal node. */
enum class DealBadgeSuffix { NEW_LOW, ALL_TIME_LOW, STORE_LOW, VOUCHER }

/** The one low-price letter a deal shows: N (new low), H (at its all-time low) or S (store low). */
enum class DealLowFlag { NEW_LOW, ALL_TIME_LOW, STORE_LOW }

/**
 * Which low-price letter a deal shows. ITAD gives a deal one flag, but [isLowestEver] is set for both a new
 * low and an all-time low, so a new low wins, then an all-time low, then a store low.
 */
fun dealLowFlag(isNewHistoricalLow: Boolean, isLowestEver: Boolean, isStoreLow: Boolean): DealLowFlag? = when {
    isNewHistoricalLow -> DealLowFlag.NEW_LOW
    isLowestEver -> DealLowFlag.ALL_TIME_LOW
    isStoreLow -> DealLowFlag.STORE_LOW
    else -> null
}

/**
 * The ordered list of badge suffixes to append to a deal's merged content description, given which
 * flags are set: the one low-price letter ([dealLowFlag]) first, then voucher — the same badges
 * [DealBadgeCluster] draws. Pure (JVM-testable) so the precedence/order isn't buried in a composable.
 */
internal fun dealBadgeSuffixes(
    isNewHistoricalLow: Boolean,
    isStoreLow: Boolean,
    hasVoucher: Boolean,
    isLowestEver: Boolean = false,
): List<DealBadgeSuffix> = buildList {
    when (dealLowFlag(isNewHistoricalLow = isNewHistoricalLow, isLowestEver = isLowestEver, isStoreLow = isStoreLow)) {
        DealLowFlag.NEW_LOW -> add(DealBadgeSuffix.NEW_LOW)
        DealLowFlag.ALL_TIME_LOW -> add(DealBadgeSuffix.ALL_TIME_LOW)
        DealLowFlag.STORE_LOW -> add(DealBadgeSuffix.STORE_LOW)
        null -> Unit
    }
    if (hasVoucher) add(DealBadgeSuffix.VOUCHER)
}

@Preview
@Composable
private fun DealFlagBadges_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoucherBadge()
                DiscountBadge(discountPercent = 39)
                NewHistoricalLowBadge()
                AllTimeLowBadge()
                StoreLowBadge()
            }
        }
    }
}

@Preview
@Composable
private fun DealFlagBadges_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoucherBadge()
                DiscountBadge(discountPercent = 39)
                NewHistoricalLowBadge()
                AllTimeLowBadge()
                StoreLowBadge()
            }
        }
    }
}
