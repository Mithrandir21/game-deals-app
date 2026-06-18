package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_mark_all_read
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_more_actions
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notifications
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationsScreenData

@Composable
internal fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenDetail: (notificationId: String) -> Unit,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()

    NotificationsScreenContent(
        data = data,
        onBack = onBack,
        onNotificationClick = { notification -> onOpenDetail(notification.id) },
        onMarkAllRead = viewModel::onMarkAllRead,
    )
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
private fun UnreadDot() {
    Box(
        modifier = Modifier
            .size(GameDealsCustomTheme.spacing.small)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

private fun previewNotification(id: String, read: Boolean) =
    ItadNotification(id = id, type = "waitlist", title = "Price drop", timestamp = "2026-06-18T09:30:00+00:00", read = read)

@Preview
@Composable
private fun NotificationsScreenPreview() {
    GameDealsTheme {
        NotificationsScreenContent(
            data = NotificationsScreenData(
                loading = false,
                notifications = persistentListOf(
                    previewNotification("n1", read = false),
                    previewNotification("n2", read = false),
                    previewNotification("n3", read = true),
                ),
            ),
            onBack = {},
            onNotificationClick = {},
            onMarkAllRead = {},
        )
    }
}

@Preview
@Composable
private fun NotificationsScreenEmptyPreview() {
    GameDealsTheme {
        NotificationsScreenContent(
            data = NotificationsScreenData(loading = false, notifications = persistentListOf()),
            onBack = {},
            onNotificationClick = {},
            onMarkAllRead = {},
        )
    }
}
