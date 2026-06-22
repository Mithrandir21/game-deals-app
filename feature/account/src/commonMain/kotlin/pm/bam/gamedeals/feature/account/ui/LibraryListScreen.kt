package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_collection_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_list_loading
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_section_collection
import pm.bam.gamedeals.feature.account.generated.resources.account_section_waitlist
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_empty
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/** A single game row in a library list (#274). */
@Immutable
internal data class GameListItem(
    val gameId: String,
    val title: String,
    val boxart: String?,
)

@Immutable
internal data class GameListState(
    val loading: Boolean = false,
    val items: ImmutableList<GameListItem> = persistentListOf(),
)

@Composable
internal fun WaitlistListScreen(
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    viewModel: WaitlistListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GameListScaffold(
        title = stringResource(Res.string.account_section_waitlist),
        state = state,
        emptyText = stringResource(Res.string.account_waitlist_empty),
        onBack = onBack,
        onGameClick = onGameClick,
    )
}

@Composable
internal fun CollectionListScreen(
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    viewModel: CollectionListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GameListScaffold(
        title = stringResource(Res.string.account_section_collection),
        state = state,
        emptyText = stringResource(Res.string.account_collection_empty),
        onBack = onBack,
        onGameClick = onGameClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameListScaffold(
    title: String,
    state: GameListState,
    emptyText: String,
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(title) },
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
                    val loadingLabel = stringResource(Res.string.account_list_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loadingLabel })
                }

                state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = emptyText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.politeLiveRegion())
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                ) {
                    items(state.items, key = { it.gameId }) { item ->
                        GameRow(item.title, item.boxart) { onGameClick(item.gameId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameRow(title: String, boxart: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = boxart,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
        )
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = GameDealsCustomTheme.spacing.medium),
        )
    }
}

private val previewGameListItems = persistentListOf(
    GameListItem(gameId = "g1", title = "Hades", boxart = null),
    GameListItem(gameId = "g2", title = "Stardew Valley", boxart = null),
    GameListItem(gameId = "g3", title = "Hollow Knight", boxart = null),
)

@Preview
@Composable
private fun GameListScaffoldPreview() {
    GameDealsTheme {
        GameListScaffold(
            title = "Waitlist",
            state = GameListState(loading = false, items = previewGameListItems),
            emptyText = "Your waitlist is empty.",
            onBack = {},
            onGameClick = {},
        )
    }
}

@Preview
@Composable
private fun GameListScaffoldEmptyPreview() {
    GameDealsTheme {
        GameListScaffold(
            title = "Collection",
            state = GameListState(loading = false),
            emptyText = "You haven’t collected any games yet.",
            onBack = {},
            onGameClick = {},
        )
    }
}
