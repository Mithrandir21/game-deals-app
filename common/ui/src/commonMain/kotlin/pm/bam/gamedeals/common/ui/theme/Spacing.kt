package pm.bam.gamedeals.common.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Internal set so that only CustomSpaces can be accessed via GameDealsTheme.spaces

internal val extraSmallSpace: Dp = 2.dp
internal val smallSpace: Dp = 4.dp
internal val mediumSpace: Dp = 8.dp
internal val largeSpace: Dp = 16.dp
internal val extraLargeSpace: Dp = 40.dp

// Shared deal-surface dimensions: thumbnail + icon sizes live in the theme so rows/tiles reference
// tokens, not hardcoded dp. The thumbnail tracks the art's aspect ratio so `ContentScale.Crop` won't
// slice the sides — ITAD's `banner300` is ≈2.14:1, so 128×60 (≈2.13:1) matches; height 60 doubles as
// a square section-icon size (see FeedComposables), so only the width tracks the banner ratio.
internal val rowThumbnailWidthSize: Dp = 128.dp
internal val rowThumbnailHeightSize: Dp = 60.dp
internal val storeIconSize: Dp = 16.dp
internal val badgeIconSize: Dp = 12.dp
internal val storeDotSize: Dp = 8.dp


@Immutable
data class CustomSpaces(
    val extraSmall: Dp = extraSmallSpace,
    val small: Dp = smallSpace,
    val medium: Dp = mediumSpace,
    val large: Dp = largeSpace,
    val extraLarge: Dp = extraLargeSpace,
    // Shared deal-surface dimensions for the dense list rows/tiles.
    val rowThumbnailWidth: Dp = rowThumbnailWidthSize,
    val rowThumbnailHeight: Dp = rowThumbnailHeightSize,
    val storeIcon: Dp = storeIconSize,
    val badgeIcon: Dp = badgeIconSize,
    val storeDot: Dp = storeDotSize,
)