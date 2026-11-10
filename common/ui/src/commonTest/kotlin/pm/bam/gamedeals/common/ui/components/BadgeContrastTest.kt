package pm.bam.gamedeals.common.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import pm.bam.gamedeals.common.ui.theme.allTimeLowBadgeContentDark
import pm.bam.gamedeals.common.ui.theme.allTimeLowBadgeContentLight
import pm.bam.gamedeals.common.ui.theme.darkScheme
import pm.bam.gamedeals.common.ui.theme.lightScheme
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerDark
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerLight
import pm.bam.gamedeals.common.ui.theme.onNewLowBadgeContainerDark
import pm.bam.gamedeals.common.ui.theme.onNewLowBadgeContainerLight
import pm.bam.gamedeals.common.ui.theme.onTopDiscountContainerDark
import pm.bam.gamedeals.common.ui.theme.onTopDiscountContainerLight
import pm.bam.gamedeals.common.ui.theme.topDiscountContainerDark
import pm.bam.gamedeals.common.ui.theme.topDiscountContainerLight
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every badge label in the shared item look stays readable on its own chip in both themes: WCAG AA (4.5:1) for
 * the discount tiers and the N / H / S letters. Catches a colour tweak that would make a chip hard to read.
 */
class BadgeContrastTest {

    private data class BadgePair(val name: String, val container: Color, val label: Color)

    private fun pairs(scheme: ColorScheme, dark: Boolean): List<BadgePair> = listOf(
        BadgePair("under 25%", scheme.surfaceVariant, scheme.onSurfaceVariant),
        BadgePair("25%+", scheme.secondaryContainer, scheme.onSecondaryContainer),
        BadgePair("50%+", scheme.tertiaryContainer, scheme.onTertiaryContainer),
        BadgePair(
            "75%+",
            if (dark) topDiscountContainerDark else topDiscountContainerLight,
            if (dark) onTopDiscountContainerDark else onTopDiscountContainerLight,
        ),
        BadgePair(
            "N",
            if (dark) newLowBadgeContainerDark else newLowBadgeContainerLight,
            if (dark) onNewLowBadgeContainerDark else onNewLowBadgeContainerLight,
        ),
        BadgePair("H", scheme.surfaceContainerHighest, if (dark) allTimeLowBadgeContentDark else allTimeLowBadgeContentLight),
        BadgePair("S", scheme.surfaceVariant, scheme.onSurfaceVariant),
    )

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun every_badge_label_is_readable_on_its_chip_in_the_light_theme() {
        pairs(lightScheme, dark = false).forEach { (name, container, label) ->
            val ratio = contrast(container, label)
            assertTrue(ratio >= 4.5f, "light $name badge is only $ratio:1")
        }
    }

    @Test
    fun every_badge_label_is_readable_on_its_chip_in_the_dark_theme() {
        pairs(darkScheme, dark = true).forEach { (name, container, label) ->
            val ratio = contrast(container, label)
            assertTrue(ratio >= 4.5f, "dark $name badge is only $ratio:1")
        }
    }

    @Test
    fun the_top_tier_red_is_not_mistaken_for_the_orange_N_or_the_blue_50_percent_tier() {
        // Different hue families: red's green channel sits well below orange's, and its blue below the 50% tier's.
        listOf(
            Triple(topDiscountContainerLight, newLowBadgeContainerLight, lightScheme.tertiaryContainer),
            Triple(topDiscountContainerDark, newLowBadgeContainerDark, darkScheme.tertiaryContainer),
        ).forEach { (top, orange, blue) ->
            assertTrue(orange.green - top.green > 0.1f, "top tier too close to the N orange")
            assertTrue(top.red > top.blue && blue.blue > blue.red, "top tier too close to the 50% blue")
        }
    }
}
