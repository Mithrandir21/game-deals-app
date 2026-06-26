package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.RecentlyViewedGame
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.recently_viewed_clear
import pm.bam.gamedeals.common.ui.generated.resources.recently_viewed_open_cd
import pm.bam.gamedeals.common.ui.generated.resources.recently_viewed_remove_cd
import pm.bam.gamedeals.common.ui.generated.resources.recently_viewed_title
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The recently-viewed games carousel (#211) — a horizontal strip of cover tiles, shared by Home and the
 * Deals tab. Tapping a tile re-opens the game (via the peek sheet); long-pressing removes that one entry;
 * the header "Clear" wipes the whole history. Renders nothing when [games] is empty, so callers can place
 * it unconditionally.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentlyViewedCarousel(
    games: ImmutableList<RecentlyViewedGame>,
    onOpen: (RecentlyViewedGame) -> Unit,
    onRemove: (RecentlyViewedGame) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(CommonRes.string.recently_viewed_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onClearAll) {
                Text(stringResource(CommonRes.string.recently_viewed_clear))
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            items(games, key = { it.gameId }) { game ->
                RecentlyViewedTile(game = game, onOpen = { onOpen(game) }, onRemove = { onRemove(game) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyViewedTile(game: RecentlyViewedGame, onOpen: () -> Unit, onRemove: () -> Unit) {
    val openCd = stringResource(CommonRes.string.recently_viewed_open_cd, game.title)
    val removeCd = stringResource(CommonRes.string.recently_viewed_remove_cd, game.title)
    Column(
        modifier = Modifier
            .width(110.dp)
            // Tap opens the game; long-press removes this one entry from the history.
            .combinedClickable(
                role = Role.Button,
                onClickLabel = openCd,
                onLongClickLabel = removeCd,
                onClick = onOpen,
                onLongClick = onRemove,
            )
            .semantics { contentDescription = openCd },
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        AsyncImage(
            model = game.boxart,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(CommonRes.drawable.videogame_thumb),
            error = painterResource(CommonRes.drawable.videogame_thumb),
            fallback = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(GAME_COVER_ASPECT_RATIO)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Text(
            text = game.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

// Portrait cover ratio (matches typical boxart); width-driven so tiles line up in the row.
private const val GAME_COVER_ASPECT_RATIO = 3f / 4f
