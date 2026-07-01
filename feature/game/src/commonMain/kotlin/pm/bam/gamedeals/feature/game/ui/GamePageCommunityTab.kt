@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_page_players_peak
import pm.bam.gamedeals.feature.game.generated.resources.game_page_players_recent
import pm.bam.gamedeals.feature.game.generated.resources.game_page_review_count
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_players
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_reviews
import pm.bam.gamedeals.feature.game.generated.resources.game_page_stats_empty
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.GamePageData
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import androidx.compose.material3.Surface

/** Green used for the positive share of review bars/labels; reads on both light and dark surfaces. */
private val ReviewPositiveColor = Color(0xFF43A047)

// ----- Community tab --------------------------------------------------------------------------------------

/** The social-proof tab: critic/user scores (full, with counts), current player counts and per-source review bars. */
@Composable
internal fun CommunityTab(data: GamePageData.Data, onRetry: () -> Unit) {
    val igdb = data.igdbGameOrNull
    val hasRatings = igdb != null && (igdb.rating != null || igdb.aggregatedRating != null)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        if (igdb != null) RatingsRow(igdb)
        when (val meta = data.gameMeta) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = onRetry)
            is SectionState.Loaded -> {
                val value = meta.value
                val hasPlayers = value?.players?.let { it.recent != null || it.peak != null } == true
                val hasReviews = value?.reviews?.isNotEmpty() == true
                if (!hasPlayers && !hasReviews && !hasRatings) {
                    TabEmpty(stringResource(Res.string.game_page_stats_empty))
                } else {
                    value?.players?.let { PlayersBlock(it) }
                    value?.reviews?.takeIf { it.isNotEmpty() }?.let { ReviewsBlock(it) }
                }
            }
        }
    }
}

@Composable
private fun PlayersBlock(players: GameMeta.Players) {
    val recent = players.recent
    val peak = players.peak
    if (recent == null && peak == null) return
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_page_section_players))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
            // Display-only stat chips: clear the (no-op) button semantics so TalkBack reads them as text.
            recent?.let {
                val label = stringResource(Res.string.game_page_players_recent, formatCount(it))
                AssistChip(onClick = {}, modifier = Modifier.clearAndSetSemantics { contentDescription = label }, label = { Text(label) })
            }
            peak?.let {
                val label = stringResource(Res.string.game_page_players_peak, formatCount(it))
                AssistChip(onClick = {}, modifier = Modifier.clearAndSetSemantics { contentDescription = label }, label = { Text(label) })
            }
        }
    }
}

/** Storefront/critic review scores (Steam %, Metacritic /100, …) from ITAD `reviews`, one card each. */
@Composable
private fun ReviewsBlock(reviews: List<GameMeta.Review>) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_page_section_reviews))
        reviews.forEach { ReviewCard(it) }
    }
}

/** A positive/negative split-bar review card: 👍 score% … negative% 👎, a proportional bar, source + count. */
@Composable
private fun ReviewCard(review: GameMeta.Review) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            val positive = review.score?.coerceIn(0, 100)
            if (positive != null) {
                val negative = 100 - positive
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = ReviewPositiveColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = "$positive%",
                        modifier = Modifier.padding(start = GameDealsCustomTheme.spacing.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ReviewPositiveColor,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$negative%",
                        modifier = Modifier.padding(end = GameDealsCustomTheme.spacing.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Icon(Icons.Filled.ThumbDown, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
                ) {
                    if (positive > 0) Box(Modifier.weight(positive.toFloat()).fillMaxHeight().background(ReviewPositiveColor))
                    if (negative > 0) Box(Modifier.weight(negative.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.error))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = review.source, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                review.count?.let {
                    Text(
                        text = stringResource(Res.string.game_page_review_count, formatCount(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}


@Preview
@Composable
private fun CommunityTabPreview() {
    GameDealsTheme { Surface { CommunityTab(data = PreviewGamePageData, onRetry = {}) } }
}

@Preview
@Composable
private fun ReviewCardPreview() {
    GameDealsTheme { Surface { Column(Modifier.padding(GameDealsCustomTheme.spacing.large)) { ReviewCard(PreviewGameMeta.reviews.first()) } } }
}

@Preview
@Composable
private fun PlayersBlockPreview() {
    GameDealsTheme {
        Surface {
            Column(Modifier.padding(GameDealsCustomTheme.spacing.large)) {
                PreviewGameMeta.players?.let { PlayersBlock(it) }
            }
        }
    }
}
