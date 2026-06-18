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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notes_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notes
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun MyNotesScreen(
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    viewModel: MyNotesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyNotesScreenContent(state = state, onBack = onBack, onGameClick = onGameClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyNotesScreenContent(
    state: NotesListState,
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
                    title = { Text(stringResource(Res.string.account_row_notes)) },
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
                        text = stringResource(Res.string.account_notes_empty),
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
                        NotesRow(item = item, onClick = { onGameClick(item.gameId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesRow(item: NotesListItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = GameDealsCustomTheme.spacing.medium),
        ) {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = item.note,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun MyNotesScreenPreview() {
    GameDealsTheme {
        MyNotesScreenContent(
            state = NotesListState(
                loading = false,
                items = persistentListOf(
                    NotesListItem(gameId = "g1", title = "Elden Ring", boxart = null, note = "Wait for a deeper sale — never below 20% yet."),
                    NotesListItem(gameId = "g2", title = "Disco Elysium", boxart = null, note = "Final Cut only."),
                ),
            ),
            onBack = {},
            onGameClick = {},
        )
    }
}

@Preview
@Composable
private fun MyNotesScreenEmptyPreview() {
    GameDealsTheme {
        MyNotesScreenContent(
            state = NotesListState(loading = false),
            onBack = {},
            onGameClick = {},
        )
    }
}
