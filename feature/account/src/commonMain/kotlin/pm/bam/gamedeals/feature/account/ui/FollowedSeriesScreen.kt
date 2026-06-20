package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_game_image
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_unfollow
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_section_followed_series
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun FollowedSeriesScreen(
    onBack: () -> Unit,
    onGameClick: (igdbGameId: Long) -> Unit,
    viewModel: FollowedSeriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FollowedSeriesContent(
        state = state,
        onBack = onBack,
        onGameClick = onGameClick,
        onUnfollow = viewModel::unfollow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowedSeriesContent(
    state: FollowedSeriesState,
    onBack: () -> Unit,
    onGameClick: (igdbGameId: Long) -> Unit,
    onUnfollow: (franchiseId: Long) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.account_section_followed_series)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.account_navigation_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding: PaddingValues ->
            when {
                state.loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.account_followed_series_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(GameDealsCustomTheme.spacing.large)
                            .politeLiveRegion(),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(vertical = GameDealsCustomTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    items(state.items, key = { it.franchiseId }) { item ->
                        FollowedSeriesCard(item, onGameClick, onUnfollow)
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowedSeriesCard(
    item: FollowedSeriesItem,
    onGameClick: (igdbGameId: Long) -> Unit,
    onUnfollow: (franchiseId: Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large),
    ) {
        Column(
            modifier = Modifier.padding(vertical = GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GameDealsCustomTheme.spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { onUnfollow(item.franchiseId) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.account_followed_series_unfollow, item.name),
                    )
                }
            }
            if (item.games.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                ) {
                    items(item.games, key = { it.igdbGameId }) { game ->
                        FollowedSeriesGameTile(game, onGameClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowedSeriesGameTile(game: FollowedSeriesGame, onGameClick: (igdbGameId: Long) -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(role = Role.Button) { onGameClick(game.igdbGameId) },
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        AsyncImage(
            model = game.coverUrl,
            contentDescription = stringResource(Res.string.account_followed_series_game_image, game.title),
            contentScale = ContentScale.Crop,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
        )
        Text(
            text = game.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val previewItems = persistentListOf(
    FollowedSeriesItem(
        franchiseId = 1L,
        name = "Halo",
        games = persistentListOf(
            FollowedSeriesGame(10L, "Halo: Combat Evolved", null),
            FollowedSeriesGame(11L, "Halo 2", null),
            FollowedSeriesGame(12L, "Halo 3", null),
        ),
    ),
    FollowedSeriesItem(franchiseId = 2L, name = "Half-Life"),
)

@Preview
@Composable
private fun FollowedSeriesContentPreview() {
    GameDealsTheme {
        FollowedSeriesContent(
            state = FollowedSeriesState(items = previewItems),
            onBack = {},
            onGameClick = {},
            onUnfollow = {},
        )
    }
}

@Preview
@Composable
private fun FollowedSeriesEmptyPreview() {
    GameDealsTheme {
        FollowedSeriesContent(state = FollowedSeriesState(), onBack = {}, onGameClick = {}, onUnfollow = {})
    }
}
