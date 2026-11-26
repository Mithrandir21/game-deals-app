package pm.bam.gamedeals.common.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * Whether the current window is roomy enough to show a genuine side-by-side (two-pane) layout with
 * **both** panes visible at once.
 *
 * True only when width ≥ Expanded (840dp) **and** height ≥ Medium (480dp). Expanded width is
 * deliberate: it's where a list-detail scaffold shows both panes simultaneously. Medium width
 * (portrait tablets) is excluded on purpose — there the scaffold collapses to a single pane and a
 * full detail screen (with its own chrome) would nest awkwardly inside the hosting tab, so those
 * form factors keep the compact single-column layout instead. The height gate drops landscape phones
 * (Compact height). Reads [currentWindowAdaptiveInfo], so it recomposes as the window is resized/rotated.
 */
@Composable
fun rememberIsWideLayout(): Boolean {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) &&
        sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
}

/**
 * Discrete width tier for adaptive layouts that rearrange content by column count rather than
 * splitting into panes (e.g. the Home feed). Unlike [rememberIsWideLayout] — which gates a
 * two-pane list-detail on Expanded width only — a feed benefits from the intermediate Medium tier
 * (portrait tablets, ~600–840dp) too, so this exposes all three Material window-width breakpoints.
 */
enum class WidthSizeClass { COMPACT, MEDIUM, EXPANDED }

/**
 * The current window's [WidthSizeClass]: Compact (<600dp), Medium (600–840dp) or Expanded (≥840dp).
 * Reads [currentWindowAdaptiveInfo], so it recomposes as the window is resized/rotated.
 */
@Composable
fun rememberWidthSizeClass(): WidthSizeClass {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WidthSizeClass.EXPANDED
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WidthSizeClass.MEDIUM
        else -> WidthSizeClass.COMPACT
    }
}
