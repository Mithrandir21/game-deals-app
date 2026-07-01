package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_cover_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_critic_rating_label
import pm.bam.gamedeals.feature.game.generated.resources.game_details_similar_game_row_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_current_tile_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_user_rating_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_error
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_loading_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry

// IGDB video ids are YouTube ids; we open the watch page externally and show YouTube's thumbnail tile.
internal fun youTubeWatchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
internal fun youTubeThumbnailUrl(videoId: String): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

internal const val COVER_ASPECT_RATIO = 0.75f

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(modifier = modifier.semantics { heading() }, text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

/** Short "nothing here" line for an empty tab/section — matches the Regions tab's empty state. */
@Composable
internal fun TabEmpty(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.politeLiveRegion(),
    )
}

/** "Couldn't load" line + a Retry button for a tab/section whose fetch failed (re-fetches that facet only). */
@Composable
internal fun TabError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        Text(
            text = stringResource(Res.string.game_page_section_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.politeLiveRegion(),
        )
        TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(Res.string.game_screen_data_loading_error_retry))
        }
    }
}

/** Spinner shown while a tab/section is re-fetching after a Retry (initial load resolves before the tab renders). */
@Composable
internal fun TabLoading(modifier: Modifier = Modifier) {
    val loadingCd = stringResource(Res.string.game_page_section_loading_cd)
    Box(modifier = modifier.fillMaxWidth().height(120.dp)) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingCd })
    }
}

@Composable
internal fun IgdbGameTile(game: IgdbGame.IgdbSimilarGame, onClick: (Long) -> Unit, modifier: Modifier = Modifier, isCurrent: Boolean = false) {
    val rowCd = if (isCurrent) stringResource(Res.string.game_details_title_match_picker_current_tile_cd, game.name)
    else stringResource(Res.string.game_details_similar_game_row_description, game.name)
    val borderModifier = if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(GameDealsCustomTheme.spacing.small)) else Modifier
    Column(
        modifier = modifier.clickable(role = Role.Button) { onClick(game.id) }.semantics(mergeDescendants = true) { contentDescription = rowCd },
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                .then(borderModifier),
        ) {
            game.coverImageId?.let { imageId ->
                AsyncImage(
                    model = igdbImageUrl(imageId, IgdbImageSize.CoverBig),
                    contentDescription = stringResource(Res.string.game_details_cover_image_cd, game.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(text = game.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
internal fun RatingsRow(game: IgdbGame, onClick: (() -> Unit)? = null) {
    val user = game.rating?.toInt()
    val critic = game.aggregatedRating?.toInt()
    if (user == null && critic == null) return
    val rowModifier = if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier
    Row(modifier = rowModifier, horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)) {
        if (user != null) RatingPill(stringResource(Res.string.game_details_user_rating_label), user, game.ratingCount)
        if (critic != null) RatingPill(stringResource(Res.string.game_details_critic_rating_label), critic, game.aggregatedRatingCount)
    }
}

@Composable
internal fun RatingPill(label: String, value: Int, count: Long?) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(GameDealsCustomTheme.spacing.small))
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = "$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) Text(text = "($count)", style = MaterialTheme.typography.labelSmall)
    }
}

/** KMP-safe thousands grouping for player/review counts (no java.text on common). */
internal fun formatCount(value: Int): String {
    val digits = value.toString()
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(digits[i])
    }
    return sb.toString()
}

/** Seconds → nearest whole hour, as a string (HowLongToBeat values are coarse, so whole hours read fine). */
internal fun hoursFromSeconds(seconds: Long): String = ((seconds + 1800) / 3600).toString()

private val MONTH_ABBREV = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun formatReleaseDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.UTC).date
    return "${MONTH_ABBREV[date.month.ordinal]} ${date.dayOfMonth}, ${date.year}"
}
