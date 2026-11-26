package pm.bam.gamedeals.common.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a reading column gets. On tablets, list screens and pages centre at this width instead of
 * stretching each row across the screen (a game's price ending up far from its title).
 */
val READABLE_CONTENT_MAX_WIDTH: Dp = 720.dp

/**
 * The side margin that centres content at [READABLE_CONTENT_MAX_WIDTH] within [available] width; zero on
 * phones. Applied as content padding of a full-width list, so the margins still scroll.
 */
fun readableSideMargin(available: Dp): Dp = ((available - READABLE_CONTENT_MAX_WIDTH) / 2).coerceAtLeast(0.dp)
