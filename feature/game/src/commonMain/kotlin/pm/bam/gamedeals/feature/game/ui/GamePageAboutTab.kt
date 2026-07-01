@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.components.AgeRatingsRow
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_links
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_similar
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_game_modes
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_platforms
import pm.bam.gamedeals.feature.game.generated.resources.game_page_series_follow
import pm.bam.gamedeals.feature.game.generated.resources.game_page_series_following
import pm.bam.gamedeals.feature.game.generated.resources.game_page_series_section
import pm.bam.gamedeals.feature.game.generated.resources.game_page_trailer_thumbnail_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_page_trailer_title_fallback
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_storyline
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_age_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_developer_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_genres_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_publisher_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_hltb_hours
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_details
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_dlcs
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_hltb
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_media
import pm.bam.gamedeals.feature.game.generated.resources.game_page_overview_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_read_more
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_show_less
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.GamePageData
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import androidx.compose.material3.Surface

private const val SCREENSHOT_ASPECT_RATIO = 16f / 9f

private val TRAILER_TILE_WIDTH = 320.dp // 180.dp tall × 16:9

private const val COLLAPSED_LINES = 5

// ----- About tab ------------------------------------------------------------------------------------------

/**
 * The game's IGDB content, clustered into scannable groups: description, a merged media gallery
 * (trailers + screenshots), a compact details table (platforms/modes/genres/HLTB/companies/age), "more
 * games" (DLC + similar + series) and a links footer. IGDB drives the empty/error message; links are a
 * best-effort extra.
 */
@Composable
internal fun AboutTab(
    data: GamePageData.Data,
    followedFranchiseIds: Set<Long>,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit,
    onToggleFollowFranchise: (franchiseId: Long, name: String) -> Unit,
    onRetryIgdb: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large)) {
        when (val igdb = data.igdb) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = onRetryIgdb)
            is SectionState.Loaded -> {
                val game = igdb.value
                if (game != null) {
                    if (!game.summary.isNullOrBlank() || !game.storyline.isNullOrBlank()) DescriptionSection(game)
                    if (game.videos.isNotEmpty() || game.screenshotImageIds.isNotEmpty()) MediaGallery(game, goToWeb)
                    DetailsTable(game)
                    val dlcs = game.dlcs + game.expansions
                    if (dlcs.isNotEmpty()) GameTileRow(Res.string.game_page_section_dlcs, dlcs, onSimilarGameClick)
                    if (game.similarGames.isNotEmpty()) GameTileRow(Res.string.game_details_section_similar, game.similarGames, onSimilarGameClick)
                    game.franchises.forEach { franchise ->
                        SeriesSection(
                            franchise = franchise,
                            isFollowed = franchise.id in followedFranchiseIds,
                            onToggleFollow = { onToggleFollowFranchise(franchise.id, franchise.name) },
                            onGameClick = onSimilarGameClick,
                        )
                    }
                } else if (data.websites.isEmpty()) {
                    TabEmpty(stringResource(Res.string.game_page_overview_empty))
                }
            }
        }
        if (data.websites.isNotEmpty()) LinksSection(data.websites)
    }
}

@Composable
private fun DescriptionSection(game: IgdbGame) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large)) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_details_section_description))
            game.summary?.let { CollapsibleParagraph(it) }
            game.storyline?.let {
                Spacer(modifier = Modifier.height(GameDealsCustomTheme.spacing.small))
                SectionHeader(stringResource(Res.string.game_details_section_storyline))
                CollapsibleParagraph(it)
            }
        }
    }
}

@Composable
private fun CollapsibleParagraph(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout -> if (!expanded) hasOverflow = layout.hasVisualOverflow },
    )
    if (hasOverflow || expanded) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.CenterEnd)) {
            Text(text = stringResource(if (expanded) Res.string.game_screen_summary_show_less else Res.string.game_screen_summary_read_more))
        }
    }
}

/** Trailers + screenshots merged into one horizontal gallery: video tiles play externally, image tiles open the viewer. */
@Composable
private fun MediaGallery(game: IgdbGame, goToWeb: (url: String, gameTitle: String) -> Unit) {
    var openIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_page_section_media), Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            itemsIndexed(game.videos) { index, video ->
                val title = video.name ?: stringResource(Res.string.game_page_trailer_title_fallback, index + 1)
                val description = stringResource(Res.string.game_page_trailer_thumbnail_cd, index + 1, game.videos.size, game.name, title)
                Column(
                    modifier = Modifier
                        .width(TRAILER_TILE_WIDTH)
                        .clickable(role = Role.Button) { goToWeb(youTubeWatchUrl(video.videoId), game.name) }
                        .semantics(mergeDescendants = true) { contentDescription = description },
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                            .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = youTubeThumbnailUrl(video.videoId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(GameDealsCustomTheme.spacing.extraSmall),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            itemsIndexed(game.screenshotImageIds) { index, imageId ->
                // Mirror the trailer tile's structure (same width + a reserved 2-line caption area) so the
                // LazyRow's height stays constant when scrolling from videos to screenshots. Screenshots
                // have no caption, so the text is blank — it only reserves the matching vertical space.
                Column(
                    modifier = Modifier.width(TRAILER_TILE_WIDTH),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                ) {
                    AsyncImage(
                        model = igdbImageUrl(imageId, IgdbImageSize.ScreenshotMed),
                        contentDescription = stringResource(Res.string.game_details_screenshot_image_cd, game.name, index + 1),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                            .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable(role = Role.Button) { openIndex = index },
                    )
                    Text(
                        text = "",
                        style = MaterialTheme.typography.labelMedium,
                        minLines = 2,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    openIndex?.let { startPage ->
        ScreenshotViewerDialog(
            screenshotImageIds = game.screenshotImageIds,
            gameName = game.name,
            initialPage = startPage,
            onDismiss = { openIndex = null },
        )
    }
}

/** The compact label/value "Details" card — folds the old platform/mode/genre/HLTB/company chip-rows + age badges into one scannable block. */
@Composable
private fun DetailsTable(game: IgdbGame) {
    val separator = ", "
    val platforms = game.platforms.takeIf { it.isNotEmpty() }?.joinToString(separator)
    val modes = game.gameModes.takeIf { it.isNotEmpty() }?.joinToString(separator)
    val genres = (game.genres + game.themes).takeIf { it.isNotEmpty() }?.joinToString(separator)
    val developers = game.involvedCompanies.filter { it.role == IgdbGame.IgdbCompanyRole.Role.Developer }.map { it.companyName }.distinct().takeIf { it.isNotEmpty() }?.joinToString(separator)
    val publishers = game.involvedCompanies.filter { it.role == IgdbGame.IgdbCompanyRole.Role.Publisher }.map { it.companyName }.distinct().takeIf { it.isNotEmpty() }?.joinToString(separator)
    val hltb = game.timeToBeat?.let { ttb ->
        listOfNotNull(ttb.hastily, ttb.normally, ttb.completely).map { stringResource(Res.string.game_page_hltb_hours, hoursFromSeconds(it)) }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    val hasAge = game.ageRatings.isNotEmpty()
    if (platforms == null && modes == null && genres == null && developers == null && publishers == null && hltb == null && !hasAge) return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large)) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_page_section_details))
            platforms?.let { DetailRow(stringResource(Res.string.game_page_section_platforms), it) }
            modes?.let { DetailRow(stringResource(Res.string.game_page_section_game_modes), it) }
            genres?.let { DetailRow(stringResource(Res.string.game_page_details_genres_label), it) }
            hltb?.let { DetailRow(stringResource(Res.string.game_page_section_hltb), it) }
            developers?.let { DetailRow(stringResource(Res.string.game_page_details_developer_label), it) }
            publishers?.let { DetailRow(stringResource(Res.string.game_page_details_publisher_label), it) }
            if (hasAge) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(Res.string.game_page_details_age_label), modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AgeRatingsRow(ratings = game.ageRatings, modifier = Modifier.weight(1f), badgeHeight = 32.dp)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(text = label, modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GameTileRow(
    titleRes: StringResource,
    games: List<IgdbGame.IgdbSimilarGame>,
    onClick: (igdbGameId: Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(titleRes), Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            items(games, key = { it.id }) { game -> IgdbGameTile(game, onClick, Modifier.width(112.dp)) }
        }
    }
}

/**
 * A franchise/series the game belongs to (#7): the series name with a Follow/Following toggle, and its
 * other member games as tappable tiles (reusing the similar-games row).
 */
@Composable
private fun SeriesSection(
    franchise: IgdbGame.IgdbFranchise,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onGameClick: (igdbGameId: Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(Res.string.game_page_series_section, franchise.name), Modifier.weight(1f))
            FilterChip(
                selected = isFollowed,
                onClick = onToggleFollow,
                label = {
                    Text(stringResource(if (isFollowed) Res.string.game_page_series_following else Res.string.game_page_series_follow))
                },
                leadingIcon = if (isFollowed) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
        if (franchise.games.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                items(franchise.games, key = { it.id }) { game -> IgdbGameTile(game, onGameClick, Modifier.width(112.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksSection(websites: List<WebsiteUiModel>) {
    val uriHandler = LocalUriHandler.current
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large)) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_details_section_links))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
                websites.forEach { site ->
                    AssistChip(
                        onClick = { uriHandler.openUri(site.url) },
                        label = { Text(site.category.name) },
                        leadingIcon = site.faviconUrl?.let { faviconUrl ->
                            {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(faviconUrl)
                                        .memoryCacheKey(site.faviconCacheKey)
                                        .diskCacheKey(site.faviconCacheKey)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(AssistChipDefaults.IconSize)
                                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}


@Preview
@Composable
private fun AboutTabPreview() {
    GameDealsTheme {
        Surface {
            AboutTab(
                data = PreviewGamePageData,
                followedFranchiseIds = emptySet(),
                goToWeb = { _, _ -> },
                onSimilarGameClick = {},
                onToggleFollowFranchise = { _, _ -> },
                onRetryIgdb = {},
            )
        }
    }
}

@Preview
@Composable
private fun GameTileRowPreview() {
    GameDealsTheme { Surface { GameTileRow(Res.string.game_details_section_similar, PreviewSimilarGames, onClick = {}) } }
}

@Preview
@Composable
private fun DetailsTablePreview() {
    GameDealsTheme { Surface { DetailsTable(PreviewIgdbGame) } }
}

@Preview
@Composable
private fun DescriptionSectionPreview() {
    GameDealsTheme { Surface { DescriptionSection(PreviewIgdbGame) } }
}

@Preview
@Composable
private fun MediaGalleryPreview() {
    GameDealsTheme { Surface { MediaGallery(PreviewIgdbGame, goToWeb = { _, _ -> }) } }
}

@Preview
@Composable
private fun SeriesSectionPreview() {
    GameDealsTheme {
        Surface {
            SeriesSection(
                franchise = PreviewIgdbGame.franchises.first(),
                isFollowed = true,
                onToggleFollow = {},
                onGameClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun LinksSectionPreview() {
    GameDealsTheme { Surface { LinksSection(PreviewWebsites) } }
}
