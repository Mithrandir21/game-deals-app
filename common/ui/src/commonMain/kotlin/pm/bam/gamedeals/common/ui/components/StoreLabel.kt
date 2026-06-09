package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * A compact store identity chip — a small leading icon/dot plus the store name — for the
 * ITAD-style deal surfaces (UI Improvements board, Phase A). ITAD tags every deal with its
 * store; our rows showed nothing, though `Store.storeName` / `Store.iconUrl` are available.
 *
 * When [iconUrl] is present the store icon is shown (clipped to a circle, the established store
 * art pattern with a [videogame_thumb] error fallback). When it is blank — the common case for
 * ITAD until store logos land — it degrades to a neutral dot tinted with [color], so rows stay
 * aligned and the label reads cleanly anywhere (including over a hero scrim, where callers pass
 * a light [color]).
 *
 * String-agnostic: callers may pass a localized [contentDescription]; otherwise the visible
 * store name carries the semantics.
 */
@Composable
fun StoreLabel(
    storeName: String,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null,
) {
    val semanticsModifier = contentDescription?.let { cd ->
        Modifier.clearAndSetSemantics { this.contentDescription = cd }
    } ?: Modifier

    Row(
        modifier = modifier.then(semanticsModifier),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null, // decorative; the name carries the spoken text
                contentScale = ContentScale.Fit,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = storeName,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun StoreLabel_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                StoreLabel(storeName = "GreenManGaming", iconUrl = "https://example.com/gmg.png")
                StoreLabel(storeName = "Steam") // no icon -> neutral dot
            }
        }
    }
}

@Preview
@Composable
private fun StoreLabel_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                StoreLabel(storeName = "GamesPlanet US")
                StoreLabel(
                    storeName = "Epic Game Store",
                    color = MaterialTheme.colorScheme.onSurface, // e.g. over a hero scrim
                )
            }
        }
    }
}
