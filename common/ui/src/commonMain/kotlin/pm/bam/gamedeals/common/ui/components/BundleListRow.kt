package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import kotlin.time.Instant
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.bundle_row_art_strip
import pm.bam.gamedeals.common.ui.generated.resources.bundle_row_description
import pm.bam.gamedeals.common.ui.generated.resources.bundle_row_expiry
import pm.bam.gamedeals.common.ui.generated.resources.bundle_row_from_price
import pm.bam.gamedeals.common.ui.generated.resources.bundle_row_game_count
import pm.bam.gamedeals.common.ui.generated.resources.bundle_row_overflow
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The shared bundle list row — a Card with the title, a horizontal strip of the bundle's cover art (a
 * "+N" overflow chip past five slots), and a metadata line (store · game count · expiry · "From <price>").
 * Used by both the Bundles tab and the Home screen's Bundles section so the two stay in lock-step (mirrors
 * how [DealListRow] consolidated the deal rows). [modifier] lets the caller add outer padding (Home wraps
 * it in section padding; the Bundles list relies on the LazyColumn's content padding).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleListRow(
    bundle: Bundle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowCd = stringResource(CommonRes.string.bundle_row_description, bundle.title, bundle.storeName)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = rowCd },
    ) {
        Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium)) {
            Text(
                text = bundle.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BundleArtStrip(
                games = bundle.games,
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.small),
            )
            Text(
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.small),
                text = bundle.storeName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GameDealsCustomTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(CommonRes.string.bundle_row_game_count, bundle.gameCount),
                    style = MaterialTheme.typography.labelLarge,
                )
                bundle.expiryEpochMs?.let { expiry ->
                    Text(
                        text = stringResource(CommonRes.string.bundle_row_expiry, formatBundleShortDate(expiry)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bundle.priceDenominated?.let { price ->
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(CommonRes.string.bundle_row_from_price, price),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** A horizontal strip of the bundle's cover art, capped at five slots with a "+N" overflow chip. */
@Composable
private fun BundleArtStrip(
    games: ImmutableList<Bundle.BundleGame>,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) return
    val maxSlots = 5
    val showOverflow = games.size > maxSlots
    val shownCount = if (showOverflow) maxSlots - 1 else games.size
    val overflow = games.size - shownCount
    val shape = RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)
    val stripCd = stringResource(CommonRes.string.bundle_row_art_strip)
    Row(
        modifier = modifier.semantics { contentDescription = stripCd },
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        games.take(shownCount).forEach { game ->
            AsyncImage(
                model = game.boxart,
                contentDescription = null,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .clip(shape),
            )
        }
        if (showOverflow) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(CommonRes.string.bundle_row_overflow, overflow),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Formats a bundle epoch-ms as a short local date, e.g. "Jul 7, 2026". Shared by the row + detail footer. */
fun formatBundleShortDate(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${MONTH_ABBREV[date.month.ordinal]} ${date.dayOfMonth}, ${date.year}"
}

private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

@Preview
@Composable
private fun BundleListRow_Preview() {
    val games = persistentListOf(
        Bundle.BundleGame("a", "Game A", ""),
        Bundle.BundleGame("b", "Game B", ""),
        Bundle.BundleGame("c", "Game C", ""),
        Bundle.BundleGame("d", "Game D", ""),
        Bundle.BundleGame("e", "Game E", ""),
        Bundle.BundleGame("f", "Game F", ""),
        Bundle.BundleGame("g", "Game G", ""),
    )
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            BundleListRow(
                bundle = Bundle(
                    id = 1,
                    title = "Redline Racing Bundle",
                    storeName = "Humble Store",
                    url = "https://example.com/1",
                    expiryEpochMs = 1_999_999_999_000L,
                    gameCount = 7,
                    priceDenominated = "€5.38",
                    games = games,
                ),
                onClick = {},
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
            )
        }
    }
}
