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
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_company_role_developer
import pm.bam.gamedeals.feature.game.generated.resources.game_details_company_role_porting
import pm.bam.gamedeals.feature.game.generated.resources.game_details_company_role_publisher
import pm.bam.gamedeals.feature.game.generated.resources.game_details_company_role_supporting
import pm.bam.gamedeals.feature.game.generated.resources.game_details_cover_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_critic_rating_label
import pm.bam.gamedeals.feature.game.generated.resources.game_details_released_label
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screen_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_companies
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_links
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_screenshots
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_message
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_search_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_search_deals_cta
import pm.bam.gamedeals.feature.game.generated.resources.game_details_search_deals_cta_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_similar
import pm.bam.gamedeals.feature.game.generated.resources.game_details_similar_game_row_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_view_deals_cta
import pm.bam.gamedeals.feature.game.generated.resources.game_details_view_deals_cta_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_storyline
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_close
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_current_tile_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_error
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_explanation
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_loading_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_warning_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_user_rating_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_navigation_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_read_more
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_show_less
import kotlin.time.Instant

@Composable
internal fun GameDetailsScreen(
    onBack: () -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit = {},
    onViewDealsClick: (cheapsharkGameId: Int) -> Unit = {},
    onSearchDealsByTitle: (title: String) -> Unit = {},
    viewModel: GameDetailsViewModel = koinViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val onRetry: () -> Unit = { viewModel.reload() }
    val scope = rememberCoroutineScope()
    val onDealsCtaClick: () -> Unit = {
        scope.launch {
            when (val action = viewModel.resolveDealsAction()) {
                is GameDetailsViewModel.DealsAction.OpenGame -> onViewDealsClick(action.cheapsharkGameId)
                is GameDetailsViewModel.DealsAction.SearchByTitle -> onSearchDealsByTitle(action.title)
                null -> Unit
            }
        }
    }
    GameDetailsScreenContent(
        data = state.value,
        onBack = onBack,
        onRetry = onRetry,
        onSimilarGameClick = onSimilarGameClick,
        onDealsCtaClick = onDealsCtaClick,
        onSearchDealsByTitle = onSearchDealsByTitle,
        onWarningTap = viewModel::onWarningTap,
        onPickerDismiss = viewModel::onPickerDismiss,
        onCandidatePicked = viewModel::onCandidatePicked,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailsScreenContent(
    data: GameDetailsViewModel.GameDetailsScreenData,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit,
    onDealsCtaClick: () -> Unit = {},
    onSearchDealsByTitle: (title: String) -> Unit = {},
    onWarningTap: () -> Unit = {},
    onPickerDismiss: () -> Unit = {},
    onCandidatePicked: (igdbGameId: Long) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnRetry by rememberUpdatedState(onRetry)
    val errorMessage = stringResource(Res.string.game_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.game_screen_data_loading_error_retry)

    val title = when (data) {
        is GameDetailsViewModel.GameDetailsScreenData.Data -> data.game.name
        else -> stringResource(Res.string.game_details_screen_title)
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            modifier = Modifier.semantics { heading() },
                            text = title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.game_screen_navigation_back_button),
                            )
                        }
                    },
                    actions = {
                        if (data is GameDetailsViewModel.GameDetailsScreenData.Data && data.resolvedByTitle) {
                            IconButton(onClick = onWarningTap) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = stringResource(Res.string.game_details_title_match_warning_cd),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding: PaddingValues ->
            when (data) {
                GameDetailsViewModel.GameDetailsScreenData.Loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                )

                GameDetailsViewModel.GameDetailsScreenData.Error -> LaunchedEffect(snackbarHostState) {
                    val result = snackbarHostState.showSnackbar(message = errorMessage, actionLabel = errorRetry)
                    if (result == SnackbarResult.ActionPerformed) currentOnRetry()
                }

                is GameDetailsViewModel.GameDetailsScreenData.NoMatch ->
                    NoMatchSection(
                        modifier = Modifier.padding(innerPadding),
                        title = data.title,
                        onSearch = { onSearchDealsByTitle(data.title) },
                        onBack = onBack,
                    )

                is GameDetailsViewModel.GameDetailsScreenData.Data ->
                    GameDetailsBody(
                        modifier = Modifier.padding(innerPadding),
                        data = data,
                        onSimilarGameClick = onSimilarGameClick,
                        onDealsCtaClick = onDealsCtaClick,
                    )
            }
        }
    }

    if (data is GameDetailsViewModel.GameDetailsScreenData.Data && data.showPicker) {
        CandidatePickerSheet(
            data = data,
            onDismiss = onPickerDismiss,
            onCandidatePicked = onCandidatePicked,
            onRetry = onWarningTap,
        )
    }
}

@Composable
private fun GameDetailsBody(
    modifier: Modifier,
    data: GameDetailsViewModel.GameDetailsScreenData.Data,
    onSimilarGameClick: (igdbGameId: Long) -> Unit,
    onDealsCtaClick: () -> Unit = {},
) {
    val game = data.game
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large),
    ) {
        HeroSection(game = game)
        DealsCtaSection(game = game, onClick = onDealsCtaClick)
        if (!game.summary.isNullOrBlank() || !game.storyline.isNullOrBlank()) DescriptionSection(game = game)
        if (game.genres.isNotEmpty() || game.themes.isNotEmpty()) ChipsSection(game = game)
        if (game.screenshotImageIds.isNotEmpty()) ScreenshotsSection(game = game)
        if (game.involvedCompanies.isNotEmpty()) CompaniesSection(companies = game.involvedCompanies)
        if (data.websites.isNotEmpty()) LinksSection(websites = data.websites)
        if (game.similarGames.isNotEmpty()) SimilarGamesSection(games = game.similarGames, onSimilarGameClick = onSimilarGameClick)
    }
}

@Composable
private fun HeroSection(game: IgdbGame) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large),
        verticalAlignment = Alignment.Top,
    ) {
        val coverId = game.coverImageId
        Box(
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(COVER_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(GameDealsCustomTheme.spacing.small)),
        ) {
            if (coverId != null) {
                AsyncImage(
                    model = igdbImageUrl(coverId, IgdbImageSize.CoverBig),
                    contentDescription = stringResource(Res.string.game_details_cover_image_cd, game.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = GameDealsCustomTheme.spacing.medium)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            Text(text = game.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            game.firstReleaseDate?.let { instant ->
                Text(
                    text = stringResource(Res.string.game_details_released_label, formatReleaseDate(instant)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            RatingsRow(game = game)
        }
    }
}

@Composable
private fun NoMatchSection(
    modifier: Modifier,
    title: String,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            ) {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(Res.string.game_details_no_match_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.game_details_no_match_message, title),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSearch,
        ) {
            Text(text = stringResource(Res.string.game_details_no_match_search_button))
        }
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
        ) {
            Text(text = stringResource(Res.string.game_details_no_match_back_button))
        }
    }
}

@Composable
private fun DealsCtaSection(game: IgdbGame, onClick: () -> Unit) {
    val hasSteamMapping = game.steamAppId != null
    val labelRes = if (hasSteamMapping) Res.string.game_details_view_deals_cta else Res.string.game_details_search_deals_cta
    val cdRes = if (hasSteamMapping) Res.string.game_details_view_deals_cta_cd else Res.string.game_details_search_deals_cta_cd
    val cd = stringResource(cdRes, game.name)
    FilledTonalButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large)
            .semantics { contentDescription = cd },
        onClick = onClick,
    ) {
        Text(text = stringResource(labelRes))
    }
}

@Composable
private fun RatingsRow(game: IgdbGame) {
    val user = game.rating?.toInt()
    val critic = game.aggregatedRating?.toInt()
    if (user == null && critic == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        if (user != null) RatingPill(label = stringResource(Res.string.game_details_user_rating_label), value = user, count = game.ratingCount)
        if (critic != null) RatingPill(label = stringResource(Res.string.game_details_critic_rating_label), value = critic, count = game.aggregatedRatingCount)
    }
}

@Composable
private fun RatingPill(label: String, value: Int, count: Long?) {
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

@Composable
private fun DescriptionSection(game: IgdbGame) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large),
    ) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_details_section_description))
            game.summary?.let { CollapsibleParagraph(text = it) }
            game.storyline?.let {
                Spacer(modifier = Modifier.height(GameDealsCustomTheme.spacing.small))
                SectionHeader(stringResource(Res.string.game_details_section_storyline))
                CollapsibleParagraph(text = it)
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
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.CenterEnd),
        ) {
            Text(
                text = stringResource(
                    if (expanded) Res.string.game_screen_summary_show_less
                    else Res.string.game_screen_summary_read_more
                )
            )
        }
    }
}

@Composable
private fun ChipsSection(game: IgdbGame) {
    val chips = game.genres + game.themes
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        items(chips) { label ->
            AssistChip(onClick = {}, label = { Text(label) })
        }
    }
}

@Composable
private fun ScreenshotsSection(game: IgdbGame) {
    var openIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(
            text = stringResource(Res.string.game_details_section_screenshots),
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            itemsIndexed(game.screenshotImageIds) { index, imageId ->
                AsyncImage(
                    model = igdbImageUrl(imageId, IgdbImageSize.ScreenshotMed),
                    contentDescription = stringResource(Res.string.game_details_screenshot_image_cd, game.name, index + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(180.dp)
                        .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { openIndex = index },
                )
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

@Composable
private fun CompaniesSection(companies: List<IgdbGame.IgdbCompanyRole>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large),
    ) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_details_section_companies))
            companies.forEachIndexed { index, role ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = GameDealsCustomTheme.spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = companyRoleLabel(role.role),
                        modifier = Modifier.width(112.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = role.companyName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun companyRoleLabel(role: IgdbGame.IgdbCompanyRole.Role): String = when (role) {
    IgdbGame.IgdbCompanyRole.Role.Developer -> stringResource(Res.string.game_details_company_role_developer)
    IgdbGame.IgdbCompanyRole.Role.Publisher -> stringResource(Res.string.game_details_company_role_publisher)
    IgdbGame.IgdbCompanyRole.Role.Porting -> stringResource(Res.string.game_details_company_role_porting)
    IgdbGame.IgdbCompanyRole.Role.Supporting -> stringResource(Res.string.game_details_company_role_supporting)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksSection(websites: List<WebsiteUiModel>) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large),
    ) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_details_section_links))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)
            ) {
                websites.forEach { site ->
                    WebsiteChip(site = site, onClick = { uriHandler.openUri(site.url) })
                }
            }
        }
    }
}

@Composable
private fun WebsiteChip(site: WebsiteUiModel, onClick: () -> Unit) {
    val context = LocalPlatformContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(site.faviconUrl)
            .memoryCacheKey(site.faviconCacheKey)
            .diskCacheKey(site.faviconCacheKey)
            .build(),
    )
    val state by painter.state.collectAsState()
    // Pass `null` (not an empty composable) for the leading icon when the favicon isn't ready / failed
    // — `AssistChip` collapses the slot entirely on null, so the chip reads as label-only without the
    // dead 18 dp gap where the icon would have been. Many sites (Discord, Bluesky, small indie pages)
    // have no working /favicon.ico at all; the truthful UX is no slot, not a placeholder arrow.
    val hasIcon = state is AsyncImagePainter.State.Success
    AssistChip(
        onClick = onClick,
        label = { Text(site.category.name) },
        leadingIcon = if (hasIcon) {
            {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        } else null,
    )
}

@Composable
private fun SimilarGamesSection(
    games: List<IgdbGame.IgdbSimilarGame>,
    onSimilarGameClick: (igdbGameId: Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(
            text = stringResource(Res.string.game_details_section_similar),
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            items(games, key = { it.id }) { similar ->
                IgdbGameTile(
                    game = similar,
                    onClick = onSimilarGameClick,
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}

@Composable
private fun IgdbGameTile(
    game: IgdbGame.IgdbSimilarGame,
    onClick: (igdbGameId: Long) -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
) {
    val baseCd = stringResource(Res.string.game_details_similar_game_row_description, game.name)
    val currentCd = stringResource(Res.string.game_details_title_match_picker_current_tile_cd, game.name)
    val rowCd = if (isCurrent) currentCd else baseCd
    val borderModifier = if (isCurrent) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(GameDealsCustomTheme.spacing.small))
    } else Modifier
    Column(
        modifier = modifier
            .clickable(role = Role.Button) { onClick(game.id) }
            .semantics(mergeDescendants = true) { contentDescription = rowCd },
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
        Text(
            text = game.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidatePickerSheet(
    data: GameDetailsViewModel.GameDetailsScreenData.Data,
    onDismiss: () -> Unit,
    onCandidatePicked: (igdbGameId: Long) -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(Res.string.game_details_title_match_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.game_details_title_match_picker_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (val state = data.candidatesState) {
                GameDetailsViewModel.CandidatesState.Idle,
                GameDetailsViewModel.CandidatesState.Loading -> {
                    val loadingCd = stringResource(Res.string.game_details_title_match_picker_loading_cd)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .semantics { contentDescription = loadingCd },
                        )
                    }
                }
                is GameDetailsViewModel.CandidatesState.Loaded -> {
                    if (state.items.isEmpty()) {
                        Text(text = stringResource(Res.string.game_details_title_match_picker_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp),
                        ) {
                            gridItems(state.items, key = { it.id }) { candidate ->
                                IgdbGameTile(
                                    game = candidate,
                                    onClick = onCandidatePicked,
                                    isCurrent = candidate.id == data.game.id,
                                )
                            }
                        }
                    }
                }
                GameDetailsViewModel.CandidatesState.Error -> {
                    Text(
                        text = stringResource(Res.string.game_details_title_match_picker_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(Res.string.game_details_title_match_picker_retry))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(Res.string.game_details_title_match_picker_close))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.semantics { heading() },
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

private const val COLLAPSED_LINES = 5
private const val COVER_ASPECT_RATIO = 0.75f
private const val SCREENSHOT_ASPECT_RATIO = 16f / 9f

private fun formatReleaseDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.UTC).date
    val month = MONTH_ABBREV[date.month.ordinal]
    return "$month ${date.dayOfMonth}, ${date.year}"
}

private val MONTH_ABBREV = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val previewWebsites = persistentListOf(
    WebsiteUiModel(
        url = "https://www.halowaypoint.com",
        category = IgdbGame.IgdbWebsite.Category.Official,
        faviconUrl = "https://www.halowaypoint.com/favicon.ico",
        faviconCacheKey = null,
    ),
    WebsiteUiModel(
        url = "https://store.steampowered.com/app/1240440",
        category = IgdbGame.IgdbWebsite.Category.Steam,
        faviconUrl = "https://store.steampowered.com/favicon.ico",
        faviconCacheKey = "brand:steam",
    ),
    WebsiteUiModel(
        url = "https://en.wikipedia.org/wiki/Halo_Infinite",
        category = IgdbGame.IgdbWebsite.Category.Wikipedia,
        faviconUrl = "https://en.wikipedia.org/favicon.ico",
        faviconCacheKey = "brand:wikipedia",
    ),
    WebsiteUiModel(
        url = "https://www.halowaypoint.com",
        category = IgdbGame.IgdbWebsite.Category.Official,
        faviconUrl = "https://www.halowaypoint.com/favicon.ico",
        faviconCacheKey = null,
    ),
    WebsiteUiModel(
        url = "https://store.steampowered.com/app/1240440",
        category = IgdbGame.IgdbWebsite.Category.Steam,
        faviconUrl = "https://store.steampowered.com/favicon.ico",
        faviconCacheKey = "brand:steam",
    ),
    WebsiteUiModel(
        url = "https://en.wikipedia.org/wiki/Halo_Infinite",
        category = IgdbGame.IgdbWebsite.Category.Wikipedia,
        faviconUrl = "https://en.wikipedia.org/favicon.ico",
        faviconCacheKey = "brand:wikipedia",
    ),
)

private val previewIgdbDetails = IgdbGame(
    id = 103281,
    name = "Halo Infinite",
    summary = "The Master Chief returns in Halo Infinite – the next chapter of the legendary franchise. " +
        "When all hope is lost and humanity's fate hangs in the balance, the Master Chief is ready to confront the most " +
        "ruthless foe he's ever faced. Step inside the armor of humanity's greatest hero to experience an epic adventure " +
        "and explore the massive scale of the Halo ring.",
    storyline = "Six years after the events of Halo 5, the Master Chief awakens aboard a UNSC ship adrift in space above " +
        "the shattered remains of Zeta Halo.",
    coverImageId = "co2dto",
    screenshotImageIds = persistentListOf("sc98jj", "sc98jk", "sc98jl"),
    firstReleaseDate = Instant.fromEpochSeconds(1638921600L),
    rating = 80.6,
    ratingCount = 289,
    aggregatedRating = 87.9,
    aggregatedRatingCount = 9,
    genres = persistentListOf("Shooter", "Adventure"),
    themes = persistentListOf("Action", "Science fiction", "Open world", "Warfare"),
    involvedCompanies = persistentListOf(
        IgdbGame.IgdbCompanyRole("343 Industries", IgdbGame.IgdbCompanyRole.Role.Developer),
        IgdbGame.IgdbCompanyRole("Xbox Game Studios", IgdbGame.IgdbCompanyRole.Role.Publisher),
        IgdbGame.IgdbCompanyRole("Skybox Labs", IgdbGame.IgdbCompanyRole.Role.Supporting),
    ),
    websites = persistentListOf(
        IgdbGame.IgdbWebsite("https://www.halowaypoint.com", IgdbGame.IgdbWebsite.Category.Official),
        IgdbGame.IgdbWebsite("https://store.steampowered.com/app/1240440", IgdbGame.IgdbWebsite.Category.Steam),
        IgdbGame.IgdbWebsite("https://en.wikipedia.org/wiki/Halo_Infinite", IgdbGame.IgdbWebsite.Category.Wikipedia),
    ),
    similarGames = persistentListOf(
        IgdbGame.IgdbSimilarGame(987, "Halo 3", "co1xhc"),
        IgdbGame.IgdbSimilarGame(25657, "Destiny 2", "cobj1z"),
        IgdbGame.IgdbSimilarGame(3225, "No Man's Sky", "coacrk"),
    ),
    steamAppId = 1240440,
)

@Preview
@Composable
private fun GameDetailsScreen_Data_Preview() {
    GameDealsTheme {
        GameDetailsScreenContent(
            data = GameDetailsViewModel.GameDetailsScreenData.Data(previewIgdbDetails, previewWebsites),
            onBack = {},
            onRetry = {},
            onSimilarGameClick = {},
        )
    }
}

@Preview
@Composable
private fun GameDetailsScreen_Data_NoSteam_Preview() {
    GameDealsTheme {
        GameDetailsScreenContent(
            data = GameDetailsViewModel.GameDetailsScreenData.Data(previewIgdbDetails.copy(steamAppId = null), previewWebsites),
            onBack = {},
            onRetry = {},
            onSimilarGameClick = {},
        )
    }
}

@Preview
@Composable
private fun GameDetailsScreen_NoMatch_Preview() {
    GameDealsTheme {
        GameDetailsScreenContent(
            data = GameDetailsViewModel.GameDetailsScreenData.NoMatch(
                title = "Suicide Squad: Kill the Justice League - Digital Deluxe Edition",
            ),
            onBack = {},
            onRetry = {},
            onSimilarGameClick = {},
        )
    }
}

@Preview
@Composable
private fun GameDetailsScreen_Loading_Preview() {
    GameDealsTheme {
        GameDetailsScreenContent(
            data = GameDetailsViewModel.GameDetailsScreenData.Loading,
            onBack = {},
            onRetry = {},
            onSimilarGameClick = {},
        )
    }
}
