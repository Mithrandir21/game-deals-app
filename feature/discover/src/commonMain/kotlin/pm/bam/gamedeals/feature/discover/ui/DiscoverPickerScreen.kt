package pm.bam.gamedeals.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.feature.discover.generated.resources.Res
import pm.bam.gamedeals.feature.discover.generated.resources.discover_dimension_game_mode
import pm.bam.gamedeals.feature.discover.generated.resources.discover_dimension_genre
import pm.bam.gamedeals.feature.discover.generated.resources.discover_dimension_keyword
import pm.bam.gamedeals.feature.discover.generated.resources.discover_dimension_perspective
import pm.bam.gamedeals.feature.discover.generated.resources.discover_dimension_theme
import pm.bam.gamedeals.feature.discover.generated.resources.discover_navigation_back
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_clear
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_error
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_retry
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_selected_count
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_show_results
import pm.bam.gamedeals.feature.discover.generated.resources.discover_picker_title
import pm.bam.gamedeals.feature.discover.ui.DiscoverPickerViewModel.PickerState

@Composable
internal fun DiscoverPickerScreen(
    onBack: () -> Unit,
    onShowResults: (IgdbTagFilter) -> Unit,
    viewModel: DiscoverPickerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoverPickerContent(
        state = state,
        onBack = onBack,
        onToggleTag = viewModel::toggleTag,
        onClear = viewModel::clear,
        onRetry = viewModel::retry,
        onShowResults = { onShowResults(viewModel.currentFilter()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DiscoverPickerContent(
    state: PickerState,
    onBack: () -> Unit,
    onToggleTag: (TagKey) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onShowResults: () -> Unit,
) {
    val selectedCount = (state as? PickerState.Ready)?.selected?.size ?: 0
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.discover_picker_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.discover_navigation_back),
                            )
                        }
                    },
                    actions = {
                        if (selectedCount > 0) {
                            TextButton(onClick = onClear) { Text(stringResource(Res.string.discover_picker_clear)) }
                        }
                    },
                )
            },
            bottomBar = {
                if (state is PickerState.Ready) {
                    Surface(shadowElevation = GameDealsCustomTheme.spacing.small) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(GameDealsCustomTheme.spacing.medium),
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedCount > 0,
                                onClick = onShowResults,
                            ) {
                                Text(
                                    if (selectedCount > 0) {
                                        stringResource(Res.string.discover_picker_show_results) +
                                            " · " + stringResource(Res.string.discover_picker_selected_count, selectedCount)
                                    } else {
                                        stringResource(Res.string.discover_picker_show_results)
                                    }
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding: PaddingValues ->
            when (state) {
                PickerState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                PickerState.Error -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(Res.string.discover_picker_error))
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.discover_picker_retry)) }
                    }
                }

                is PickerState.Ready -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(GameDealsCustomTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    state.groups.forEach { group ->
                        Text(
                            text = stringResource(group.dimension.labelRes()),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
                            group.tags.forEach { tag ->
                                val key = TagKey(tag.dimension, tag.igdbId)
                                FilterChip(
                                    selected = key in state.selected,
                                    onClick = { onToggleTag(key) },
                                    label = { Text(tag.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun IgdbTagDimension.labelRes(): StringResource = when (this) {
    IgdbTagDimension.Genre -> Res.string.discover_dimension_genre
    IgdbTagDimension.Theme -> Res.string.discover_dimension_theme
    IgdbTagDimension.GameMode -> Res.string.discover_dimension_game_mode
    IgdbTagDimension.PlayerPerspective -> Res.string.discover_dimension_perspective
    IgdbTagDimension.Keyword -> Res.string.discover_dimension_keyword
}

@Preview
@Composable
private fun DiscoverPickerContent_Ready_Preview() {
    GameDealsTheme {
        DiscoverPickerContent(
            state = PickerState.Ready(
                groups = persistentListOf(
                    TagGroup(
                        IgdbTagDimension.Genre,
                        persistentListOf(
                            IgdbTag(IgdbTagDimension.Genre, 12L, "Role-playing (RPG)", "role-playing-rpg"),
                            IgdbTag(IgdbTagDimension.Genre, 5L, "Shooter", "shooter"),
                        ),
                    ),
                    TagGroup(
                        IgdbTagDimension.Keyword,
                        persistentListOf(IgdbTag(IgdbTagDimension.Keyword, 270L, "roguelike", "roguelike")),
                    ),
                ),
                selected = persistentSetOf(TagKey(IgdbTagDimension.Genre, 12L)),
            ),
            onBack = {},
            onToggleTag = {},
            onClear = {},
            onRetry = {},
            onShowResults = {},
        )
    }
}

@Preview
@Composable
private fun DiscoverPickerContent_Loading_Preview() {
    GameDealsTheme {
        DiscoverPickerContent(
            state = PickerState.Loading,
            onBack = {},
            onToggleTag = {},
            onClear = {},
            onRetry = {},
            onShowResults = {},
        )
    }
}
