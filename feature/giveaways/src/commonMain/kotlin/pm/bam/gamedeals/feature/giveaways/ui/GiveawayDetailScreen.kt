package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewGiveaway
import pm.bam.gamedeals.common.ui.components.StoreLabel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_back_button
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_description_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_error_msg
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_error_retry
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_go_to_giveaway
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_image
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_instructions_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_loading_indicator
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_free_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_worth_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_no_expiry
import pm.bam.gamedeals.feature.giveaways.ui.GiveawayDetailViewModel.GiveawayDetailScreenData
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun GiveawayDetailScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: GiveawayDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GiveawayDetailScreenContent(
        state = state,
        onBack = onBack,
        goToWeb = goToWeb,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiveawayDetailScreenContent(
    state: GiveawayDetailScreenData,
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onRetry: () -> Unit,
) {
    val title = (state as? GiveawayDetailScreenData.Data)?.giveaway?.title.orEmpty()
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.giveaway_detail_back_button),
                            )
                        }
                    },
                )
            },
        ) { innerPadding: PaddingValues ->
            when (state) {
                GiveawayDetailScreenData.Loading -> {
                    val loadingCd = stringResource(Res.string.giveaway_detail_loading_indicator)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .semantics { contentDescription = loadingCd },
                    )
                }

                GiveawayDetailScreenData.Error -> Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(GameDealsCustomTheme.spacing.large)
                        .wrapContentSize(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    Text(stringResource(Res.string.giveaway_detail_error_msg))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.giveaway_detail_error_retry)) }
                }

                is GiveawayDetailScreenData.Data -> GiveawayDetailBody(
                    data = state,
                    goToWeb = goToWeb,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GiveawayDetailBody(
    data: GiveawayDetailScreenData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val giveaway = data.giveaway
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        item {
            AsyncImage(
                model = giveaway.image,
                contentDescription = stringResource(Res.string.giveaway_detail_image, giveaway.title),
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small)),
            )
        }

        item {
            Button(
                onClick = { goToWeb(giveaway.openGiveawayUrl, giveaway.title) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.giveaway_detail_go_to_giveaway))
            }
        }

        item {
            data.endDateMillis?.let {
                GiveawayCountdown(expiryEpochMs = it, modifier = Modifier.fillMaxWidth())
            } ?: Text(
                text = stringResource(Res.string.giveaway_screen_no_expiry),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (giveaway.platforms.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                ) {
                    giveaway.platforms.forEach { platform ->
                        StoreLabel(storeName = platform.platformValue)
                    }
                }
            }
        }

        giveaway.worthDenominated?.let { worth ->
            item {
                Text(text = buildAnnotatedString {
                    withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle()) {
                        append(stringResource(Res.string.giveaway_screen_list_item_free_label))
                    }
                    append(" ")
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(stringResource(Res.string.giveaway_screen_list_item_worth_label, worth))
                    }
                })
            }
        }

        if (giveaway.description.isNotBlank()) {
            item {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(Res.string.giveaway_detail_description_heading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    text = giveaway.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (giveaway.instructions.isNotBlank()) {
            item {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(Res.string.giveaway_detail_instructions_heading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    text = giveaway.instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun GiveawayDetailScreenPreview() {
    GameDealsTheme {
        GiveawayDetailScreenContent(
            state = GiveawayDetailScreenData.Data(
                giveaway = PreviewGiveaway.copy(
                    title = "Tell Me Why - Chapters 1,2,3",
                    worthDenominated = "$29.99",
                    description = "Get Tell Me Why's three chapters free on Steam for a limited time.",
                    instructions = "1. Sign in to Steam.\n2. Open the store page.\n3. Click \"Add to Account\".",
                    platforms = persistentListOf(GiveawayPlatform.PC, GiveawayPlatform.STEAM),
                ),
                endDateMillis = 4_102_444_800_000L,
            ),
            onBack = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}
