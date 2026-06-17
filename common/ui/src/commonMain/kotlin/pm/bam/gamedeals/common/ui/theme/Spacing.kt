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

// Shared deal-surface dimensions (UI Improvements #254). The recurring row thumbnail and the inline
// store/badge icon sizes live in the theme so the dense ITAD-style rows and tiles reference tokens
// instead of hardcoded dp — one rhythm across Home, Deals, Store, and Search, tunable in one place.
//
// The thumbnail is sized to the **art's** aspect ratio so `ContentScale.Crop` stops slicing the
// sides: the art used by list rows is ITAD's `banner300` (300×140 ≈ 2.14:1 Steam-header landscape),
// so 128×60 (≈2.13:1) matches it almost exactly. Height stays 60 because it's also reused on its own
// as a square section-icon size (see FeedComposables); only the width tracks the banner ratio.
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
    // Shared deal-surface dimensions for the dense list rows/tiles (UI Improvements #254).
    val rowThumbnailWidth: Dp = rowThumbnailWidthSize,
    val rowThumbnailHeight: Dp = rowThumbnailHeightSize,
    val storeIcon: Dp = storeIconSize,
    val badgeIcon: Dp = badgeIconSize,
    val storeDot: Dp = storeDotSize,
)