package pm.bam.gamedeals.feature.home.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The sale area's own colours on top of the app theme: the blue sale card ([hero]) and the icy accent of the
 * "For you" tile ([ice]). Game items use the app's shared badges and prices, so no deal colours live here.
 * There are no Material roles for these, so light and dark come from the surface's lightness.
 * Each text colour keeps at least 4.5:1 against what it sits on.
 */
internal data class WinterSalePalette(
    val ice: Color,
    val onIce: Color,
    val hero: Color,
    val onHero: Color,
    val heroAccent: Color,
)

@Composable
internal fun winterSalePalette(): WinterSalePalette =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        WinterSalePalette(
            ice = Color(0xFF8FD3FF), onIce = Color(0xFF0E1626),
            hero = Color(0xFF16365A), onHero = Color(0xFFEEF3FA), heroAccent = Color(0xFF8FD3FF),
        )
    } else {
        WinterSalePalette(
            ice = Color(0xFFCDEBFF), onIce = Color(0xFF06304D),
            hero = Color(0xFFDCEFFF), onHero = Color(0xFF0A2540), heroAccent = Color(0xFF0B5C8A),
        )
    }
