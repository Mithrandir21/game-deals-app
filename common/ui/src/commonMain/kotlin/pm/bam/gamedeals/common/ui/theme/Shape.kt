package pm.bam.gamedeals.common.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ITAD-style shape scale (UI Improvements #253). Wired into `MaterialTheme(shapes = Shapes)` so
 * Cards, Buttons, and other Material components pick up consistent, moderately-rounded corners,
 * and referenced directly by the deal surfaces (e.g. the [DealHeroTile] card via
 * `MaterialTheme.shapes.medium`, the row thumbnail via `MaterialTheme.shapes.small`) for one
 * rounded language across the app.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Elevation tokens for the dense deal surfaces (UI Improvements #253). Kept deliberately low so
 * cards/tiles read flat against the near-black ITAD surfaces rather than floating.
 */
object GameDealsElevation {
    /** Resting elevation for deal cards/tiles. */
    val card: Dp = 1.dp
}
