@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.favourites.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import pm.bam.gamedeals.common.ui.PreviewFavourite
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.FavouriteGame
import pm.bam.gamedeals.feature.favourites.generated.resources.Res
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_empty_hint
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_empty_title
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_game_image
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_loading_indicator
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_navigation_back_button
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_toolbar_title
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun FavouritesScreen(
    onBack: () -> Unit,
    goToGame: (Int) -> Unit,
    viewModel: FavouritesViewModel = koinViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    FavouritesScreenContent(
        data = uiState.value,
        onBack = onBack,
        goToGame = goToGame,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavouritesScreenContent(
    data: FavouritesViewModel.FavouritesScreenData,
    onBack: () -> Unit,
    goToGame: (Int) -> Unit,
) {
    val scrollState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)

    val errorMessage = stringResource(Res.string.favourites_screen_data_loading_error_msg)
    val backLabel = stringResource(Res.string.favourites_screen_navigation_back_button)
    val loadingCd = stringResource(Res.string.favourites_screen_loading_indicator)

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
                            text = stringResource(Res.string.favourites_screen_toolbar_title),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onBack() }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.favourites_screen_navigation_back_button),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding: PaddingValues ->
            when (data.status) {
                FavouritesViewModel.FavouritesScreenStatus.LOADING -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                        .semantics { contentDescription = loadingCd },
                )

                FavouritesViewModel.FavouritesScreenStatus.SUCCESS -> if (data.favourites.isEmpty()) {
                    EmptyFavourites(modifier = Modifier.padding(innerPadding))
                } else {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.padding(innerPadding),
                        content = {
                            items(
                                count = data.favourites.size,
                                key = { index -> data.favourites[index].gameID },
                            ) {
                                FavouriteListItem(data.favourites[it]) { goToGame(data.favourites[it].gameID) }
                            }
                        },
                    )
                }

                FavouritesViewModel.FavouritesScreenStatus.ERROR -> LaunchedEffect(snackbarHostState) {
                    val result = snackbarHostState.showSnackbar(
                        message = errorMessage,
                        actionLabel = backLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        currentOnBack()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavourites(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(GameDealsCustomTheme.spacing.large)
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.height(48.dp).width(48.dp),
        )
        Text(
            text = stringResource(Res.string.favourites_screen_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.favourites_screen_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FavouriteListItem(
    favourite: FavouriteGame,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clickable(role = Role.Button) { onClick() }
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
            .semantics(mergeDescendants = true) { contentDescription = favourite.title },
        headlineContent = { Text(favourite.title) },
        leadingContent = {
            AsyncImage(
                model = favourite.thumb,
                contentDescription = stringResource(Res.string.favourites_screen_game_image, favourite.title),
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(60.dp)
                    .width(100.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
            )
        },
    )
    HorizontalDivider()
}


private val previewFavouritesList = persistentListOf(
    PreviewFavourite,
    PreviewFavourite.copy(gameID = 456, title = "Hollow Knight"),
    PreviewFavourite.copy(gameID = 789, title = "Stardew Valley"),
)

@Preview
@Composable
private fun FavouritesScreen_Success_Preview() {
    GameDealsTheme {
        FavouritesScreenContent(
            data = FavouritesViewModel.FavouritesScreenData(
                status = FavouritesViewModel.FavouritesScreenStatus.SUCCESS,
                favourites = previewFavouritesList,
            ),
            onBack = {},
            goToGame = {},
        )
    }
}

@Preview
@Composable
private fun FavouritesScreen_Success_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        FavouritesScreenContent(
            data = FavouritesViewModel.FavouritesScreenData(
                status = FavouritesViewModel.FavouritesScreenStatus.SUCCESS,
                favourites = previewFavouritesList,
            ),
            onBack = {},
            goToGame = {},
        )
    }
}

@Preview
@Composable
private fun FavouritesScreen_Empty_Preview() {
    GameDealsTheme {
        FavouritesScreenContent(
            data = FavouritesViewModel.FavouritesScreenData(
                status = FavouritesViewModel.FavouritesScreenStatus.SUCCESS,
                favourites = persistentListOf(),
            ),
            onBack = {},
            goToGame = {},
        )
    }
}

@Preview
@Composable
private fun FavouritesScreen_Loading_Preview() {
    GameDealsTheme {
        FavouritesScreenContent(
            data = FavouritesViewModel.FavouritesScreenData(
                status = FavouritesViewModel.FavouritesScreenStatus.LOADING,
            ),
            onBack = {},
            goToGame = {},
        )
    }
}
