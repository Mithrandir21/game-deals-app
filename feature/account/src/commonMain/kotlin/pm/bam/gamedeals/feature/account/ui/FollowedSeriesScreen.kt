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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import pm.bam.gamedeals.common.ui.components.DiscountBadge
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_backlog
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_game_image
import pm.bam.gamedeals.feature.account.generated.resources.account_followed_series_owned
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
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowedSeriesContent(
    state: FollowedSeriesState,
    onBack: () -> Unit,
    onGameClick: (igdbGameId: Long) -> Unit,
    onUnfollow: (franchiseId: Long) -> Unit,
    onRefresh: () -> Unit,
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

                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = GameDealsCustomTheme.spacing.large),
                        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                    ) {
                        items(state.items, key = { it.franchiseId }) { item ->
                            FollowedSeriesCard(item, state.loggedIn, onGameClick, onUnfollow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowedSeriesCard(
    item: FollowedSeriesItem,
    loggedIn: Boolean,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    // Ownership backlog — only meaningful when signed in and at least one entry is priceable.
                    if (loggedIn && item.resolvableCount > 0) {
                        Text(
                            text = stringResource(Res.string.account_followed_series_backlog, item.ownedCount, item.resolvableCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                // Owned entries are dimmed so the unowned backlog stands out.
                .alpha(if (game.owned) 0.4f else 1f),
        )
        Text(
            text = game.title,
            style = MaterialTheme.typography.bodySmall,
            // Always reserve two lines so a 1-line and a 2-line title take the same vertical space —
            // otherwise the LazyRow's height changes as tiles scroll in and out.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        // Fixed height so owned / on-sale / neither tiles all take the same vertical space — the
        // status line is the other thing that made this row's height wobble as tiles scrolled by.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SeriesTileStatusRowHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (game.owned) {
                Text(
                    text = stringResource(Res.string.account_followed_series_owned),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            } else if (game.onSale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                ) {
                    game.cutPercent?.let { DiscountBadge(discountPercent = it) }
                    game.priceDenominated?.let { price ->
                        Text(
                            text = price,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// Tall enough for the tallest status occupant (a DiscountBadge: labelMedium + 2x extraSmall padding).
private val SeriesTileStatusRowHeight = 24.dp

private val previewItems = persistentListOf(
    FollowedSeriesItem(
        franchiseId = 1L,
        name = "Halo",
        games = persistentListOf(
            FollowedSeriesGame(10L, "Halo: Combat Evolved", null, itadGameId = "a", cutPercent = 75, priceDenominated = "€4.99"),
            FollowedSeriesGame(11L, "Halo 2", null, itadGameId = "b"),
            FollowedSeriesGame(12L, "Halo 3", null, itadGameId = "c", owned = true),
        ),
        ownedCount = 1,
        resolvableCount = 3,
    ),
    FollowedSeriesItem(franchiseId = 2L, name = "Half-Life"),
)

@Preview
@Composable
private fun FollowedSeriesContentPreview() {
    GameDealsTheme {
        FollowedSeriesContent(
            state = FollowedSeriesState(loggedIn = true, items = previewItems),
            onBack = {},
            onGameClick = {},
            onUnfollow = {},
            onRefresh = {},
        )
    }
}

@Preview
@Composable
private fun FollowedSeriesEmptyPreview() {
    GameDealsTheme {
        FollowedSeriesContent(state = FollowedSeriesState(), onBack = {}, onGameClick = {}, onUnfollow = {}, onRefresh = {})
    }
}
