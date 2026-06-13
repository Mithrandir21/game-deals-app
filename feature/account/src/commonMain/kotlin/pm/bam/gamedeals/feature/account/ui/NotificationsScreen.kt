package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_chooser_dismiss
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_chooser_title
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_mark_all_read
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_more_actions
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notifications
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationsScreenData
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationsUiEvent

@Composable
internal fun NotificationsScreen(
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is NotificationsUiEvent.OpenGame -> onGameClick(event.gameId)
        }
    }

    NotificationsScreenContent(
        data = data,
        onBack = onBack,
        onNotificationClick = viewModel::onNotificationClick,
        onMarkAllRead = viewModel::onMarkAllRead,
    )

    if (data.chooser.isNotEmpty()) {
        NotificationGameChooser(
            games = data.chooser,
            onGameClick = viewModel::onChooserGameClick,
            onDismiss = viewModel::onChooserDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsScreenContent(
    data: NotificationsScreenData,
    onBack: () -> Unit,
    onNotificationClick: (notification: ItadNotification) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.account_row_notifications)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.account_navigation_back),
                            )
                        }
                    },
                    actions = {
                        if (data.notifications.isNotEmpty()) {
                            MarkAllReadAction(enabled = data.hasUnread, onMarkAllRead = onMarkAllRead)
                        }
                    },
                )
            },
        ) { innerPadding: PaddingValues ->
            when {
                data.loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                data.notifications.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.account_notifications_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    items(data.notifications, key = { it.id }) { notification ->
                        NotificationRow(notification = notification, onClick = { onNotificationClick(notification) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkAllReadAction(enabled: Boolean, onMarkAllRead: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.account_notifications_more_actions))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.account_notifications_mark_all_read)) },
            enabled = enabled,
            onClick = {
                expanded = false
                onMarkAllRead()
            },
        )
    }
}

@Composable
private fun NotificationRow(notification: ItadNotification, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = notification.title,
                fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
            )
        },
        supportingContent = { Text(notification.timestamp.substringBefore('T')) },
        leadingContent = if (notification.read) {
            null
        } else {
            { UnreadDot() }
        },
    )
}

@Composable
private fun NotificationGameChooser(
    games: ImmutableList<NotificationGame>,
    onGameClick: (gameId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.account_notifications_chooser_title)) },
        text = {
            Column {
                games.forEach { game ->
                    Text(
                        text = game.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGameClick(game.gameId) }
                            .padding(vertical = GameDealsCustomTheme.spacing.medium),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.account_notifications_chooser_dismiss))
            }
        },
    )
}

@Composable
private fun UnreadDot() {
    Box(
        modifier = Modifier
            .size(GameDealsCustomTheme.spacing.small)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}
