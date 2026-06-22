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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import pm.bam.gamedeals.feature.account.generated.resources.account_ignored_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_ignored_unignore
import pm.bam.gamedeals.feature.account.generated.resources.account_ignored_unignore_cd
import pm.bam.gamedeals.feature.account.generated.resources.account_list_loading
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_row_ignored
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun IgnoredScreen(
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    viewModel: IgnoredViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    IgnoredScreenContent(
        state = state,
        onBack = onBack,
        onGameClick = onGameClick,
        onUnignore = viewModel::onUnignore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IgnoredScreenContent(
    state: GameListState,
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    onUnignore: (gameId: String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.account_row_ignored)) },
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
                    Text(
                        text = stringResource(Res.string.account_ignored_empty),
                        modifier = Modifier.politeLiveRegion(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                ) {
                    items(state.items, key = { it.gameId }) { item ->
                        IgnoredRow(
                            item = item,
                            onClick = { onGameClick(item.gameId) },
                            onUnignore = { onUnignore(item.gameId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IgnoredRow(item: GameListItem, onClick: () -> Unit, onUnignore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.boxart,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                modifier = Modifier
                    .height(60.dp)
                    .width(100.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
            )
            Text(
                text = item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = GameDealsCustomTheme.spacing.medium),
            )
        }
        // The visible label is just "Un-ignore"; give TalkBack the game name too so the target is clear when
        // the button is focused out of list context. clearAndSetSemantics on the child stops a double-read.
        val unignoreCd = stringResource(Res.string.account_ignored_unignore_cd, item.title)
        TextButton(onClick = onUnignore, modifier = Modifier.semantics { contentDescription = unignoreCd }) {
            Text(stringResource(Res.string.account_ignored_unignore), modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Preview
@Composable
private fun IgnoredScreenPreview() {
    GameDealsTheme {
        IgnoredScreenContent(
            state = GameListState(
                loading = false,
                items = persistentListOf(
                    GameListItem(gameId = "g1", title = "Untitled Goose Game", boxart = null),
                    GameListItem(gameId = "g2", title = "Goat Simulator", boxart = null),
                ),
            ),
            onBack = {},
            onGameClick = {},
            onUnignore = {},
        )
    }
}

@Preview
@Composable
private fun IgnoredScreenEmptyPreview() {
    GameDealsTheme {
        IgnoredScreenContent(
            state = GameListState(loading = false),
            onBack = {},
            onGameClick = {},
            onUnignore = {},
        )
    }
}
