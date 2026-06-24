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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerDark
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerLight
import pm.bam.gamedeals.common.ui.theme.onNewLowBadgeContainerDark
import pm.bam.gamedeals.common.ui.theme.onNewLowBadgeContainerLight
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_new_low_badge_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_store_low_badge_label

/**
 * The ITAD-style deal flag badges (deal-badge work) that sit in a cluster around the [DiscountBadge]
 * on the deal tiles/rows, mirroring the ITAD website: an orange **"N"** for a brand-new historical
 * low, a neutral **"S"** for a store-specific low, and a **scissors** icon when the deal needs a
 * voucher. They reuse [DiscountBadge]'s compact chip styling so the whole cluster reads as one set.
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

/** A deal badge whose visual presence has a spoken (TalkBack) equivalent on the merged deal node. */
enum class DealBadgeSuffix { NEW_LOW, STORE_LOW, VOUCHER }

/**
 * The ordered list of badge suffixes to append to a deal's merged content description, given which
 * flags are set: new-/store-low first, then voucher. Pure (JVM-testable) so the precedence/order
 * isn't buried in a composable. ITAD's flag is a single value, so new-low and store-low are mutually
 * exclusive; flag `"H"` (lowest-ever but not *new*) sets none of these and produces no suffix —
 * matching the visual, which shows no badge for it.
 */
internal fun dealBadgeSuffixes(
    isNewHistoricalLow: Boolean,
    isStoreLow: Boolean,
    hasVoucher: Boolean,
): List<DealBadgeSuffix> = buildList {
    if (isNewHistoricalLow) add(DealBadgeSuffix.NEW_LOW)
    if (isStoreLow) add(DealBadgeSuffix.STORE_LOW)
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
                StoreLowBadge()
            }
        }
    }
}
