package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_owned_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlisted_content_suffix

/**
 * Small **passive** (non-interactive) status badges shown over a game's cover art on [DealListRow]
 * and [DealHeroTile]: a bookmark when the game is on the user's ITAD waitlist and a library-check
 * when it's in their collection (owned). They replace the old interactive heart — state is now
 * toggled inside the `GamePeekSheet` / game page; these only *show* it at a glance so a user
 * scanning a list can see what they already track or own (e.g. to avoid re-buying a game).
 *
 * Renders nothing when neither flag is set. Both icons sit in one compact, translucent pill so they
 * stay legible over bright art without darkening the whole image. The icons are decorative: their
 * spoken equivalents are appended to the parent row/tile's merged content description via
 * [gameStateContentSuffix] (mirroring [dealBadgeSuffixes]), so each badge carries no
 * [androidx.compose.ui.semantics.contentDescription] of its own.
 */
@Composable
fun GameStateBadges(
    isWaitlisted: Boolean,
    isCollected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isWaitlisted && !isCollected) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = GameDealsCustomTheme.spacing.small,
                vertical = GameDealsCustomTheme.spacing.extraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isWaitlisted) StateBadgeIcon(Icons.Filled.Bookmark)
            if (isCollected) StateBadgeIcon(Icons.Filled.LibraryAddCheck)
        }
    }
}

@Composable
private fun StateBadgeIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null, // decorative; the parent's merged node carries the spoken text
        modifier = Modifier.size(16.dp),
    )
}

/**
 * The spoken (TalkBack) equivalent of the [GameStateBadges] shown for a game, to append to the
 * row/tile's merged content description — keeping each badge and its announcement in lock-step,
 * exactly like [dealBadgeSuffixes] does for the deal-flag badges. Both strings are read
 * unconditionally (then appended only when set) so the composable's call shape stays stable.
 */
@Composable
fun gameStateContentSuffix(isWaitlisted: Boolean, isCollected: Boolean): String {
    val waitlisted = stringResource(CommonRes.string.deal_waitlisted_content_suffix)
    val owned = stringResource(CommonRes.string.deal_owned_content_suffix)
    return buildString {
        if (isWaitlisted) append(waitlisted)
        if (isCollected) append(owned)
    }
}

@Preview
@Composable
private fun GameStateBadges_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            ) {
                GameStateBadges(isWaitlisted = true, isCollected = false)
                GameStateBadges(isWaitlisted = false, isCollected = true)
                GameStateBadges(isWaitlisted = true, isCollected = true)
            }
        }
    }
}

@Preview
@Composable
private fun GameStateBadges_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium)) {
                GameStateBadges(isWaitlisted = true, isCollected = true)
            }
        }
    }
}
