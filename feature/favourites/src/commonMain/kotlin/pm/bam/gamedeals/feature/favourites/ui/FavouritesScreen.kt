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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.FavouriteGame
import pm.bam.gamedeals.feature.favourites.generated.resources.Res
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_empty_hint
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_empty_icon
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_empty_title
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_favourite_indicator
import pm.bam.gamedeals.feature.favourites.generated.resources.favourites_screen_game_image
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

    ScreenScaffold(
        data = uiState.value,
        onBack = onBack,
        goToGame = goToGame,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    data: FavouritesViewModel.FavouritesScreenData,
    onBack: () -> Unit,
    goToGame: (Int) -> Unit,
) {
    val scrollState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)

    val errorMessage = stringResource(Res.string.favourites_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.favourites_screen_data_loading_error_retry)

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.testTag(TopAppBarTag),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            text = stringResource(Res.string.favourites_screen_toolbar_title),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            modifier = Modifier.testTag(TopAppNavBarTag),
                            onClick = { onBack() },
                        ) {
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
                        .testTag(LoadingDataTag),
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
                        actionLabel = errorRetry,
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
            .wrapContentSize(Alignment.Center)
            .testTag(EmptyFavouritesTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(Res.string.favourites_screen_empty_icon),
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
            .clickable { onClick() }
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
            .testTag(FavouriteListItemTag.plus(favourite.gameID)),
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
                contentDescription = stringResource(Res.string.favourites_screen_favourite_indicator),
            )
        },
    )
    HorizontalDivider(color = Color.Black)
}


internal const val TopAppBarTag = "FavouritesTopAppBarTag"
internal const val TopAppNavBarTag = "FavouritesTopAppNavBarTag"
internal const val LoadingDataTag = "FavouritesLoadingDataTag"
internal const val EmptyFavouritesTag = "EmptyFavouritesTag"
internal const val FavouriteListItemTag = "FavouriteListItemTag"
