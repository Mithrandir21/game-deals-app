package pm.bam.gamedeals.common.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme

/**
 * The interactive waitlist toggle for the ITAD-style deal surfaces (UI Improvements board,
 * Phase A). ITAD shows a heart on every item; today the app only renders a *static* heart on
 * Home when a game is already waitlisted, and the real toggle lives only in the deal bottom
 * sheet — even though `observeIsWaitlisted` / `toggleWaitlist` are already wired into the
 * Home/Deals/Store/Game ViewModels. This is the reusable, hoisted-state heart those screens
 * can drop onto tiles and rows.
 *
 * State is fully hoisted: [isWaitlisted] drives the filled/outlined icon (crossfaded with
 * [AnimatedContent], matching the bottom sheet), and [onToggle] is invoked on tap. It is
 * wrapped in a Material3 [IconButton], so the touch target is ≥48dp automatically.
 *
 * String-agnostic: callers pass the two localized, action-oriented descriptions so TalkBack
 * announces what a tap will do in each state.
 *
 * @param tint icon colour; defaults to the primary accent, override for use over a hero scrim.
 */
@Composable
fun WaitlistHeartButton(
    isWaitlisted: Boolean,
    onToggle: () -> Unit,
    addToWaitlistContentDescription: String,
    removeFromWaitlistContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    IconButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier,
    ) {
        AnimatedContent(targetState = isWaitlisted, label = "waitlist-heart") { waitlisted ->
            Icon(
                imageVector = if (waitlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (waitlisted) removeFromWaitlistContentDescription else addToWaitlistContentDescription,
                tint = tint,
            )
        }
    }
}

@Preview
@Composable
private fun WaitlistHeartButton_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium)) {
                WaitlistHeartButton(
                    isWaitlisted = false,
                    onToggle = {},
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
                WaitlistHeartButton(
                    isWaitlisted = true,
                    onToggle = {},
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
                WaitlistHeartButton(
                    isWaitlisted = false,
                    onToggle = {},
                    enabled = false,
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
            }
        }
    }
}

@Preview
@Composable
private fun WaitlistHeartButton_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium)) {
                WaitlistHeartButton(
                    isWaitlisted = true,
                    onToggle = {},
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
                WaitlistHeartButton(
                    isWaitlisted = false,
                    onToggle = {},
                    // e.g. over a hero scrim
                    tint = MaterialTheme.colorScheme.onSurface,
                    addToWaitlistContentDescription = "Add to waitlist",
                    removeFromWaitlistContentDescription = "Remove from waitlist",
                )
            }
        }
    }
}
