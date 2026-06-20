package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_day_count
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_mark_all_read
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_more_actions
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_unread_state
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notifications
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationDay
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationsScreenData

@Composable
internal fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenDay: (date: String) -> Unit,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()

    NotificationsScreenContent(
        data = data,
        onBack = onBack,
        onDayClick = { day -> onOpenDay(day.date) },
        onMarkAllRead = viewModel::onMarkAllRead,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsScreenContent(
    data: NotificationsScreenData,
    onBack: () -> Unit,
    onDayClick: (day: NotificationDay) -> Unit,
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
                        if (data.days.isNotEmpty()) {
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

                data.days.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.account_notifications_empty),
                        modifier = Modifier.politeLiveRegion(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    items(data.days, key = { it.date }) { day ->
                        NotificationDayRow(day = day, onClick = { onDayClick(day) })
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
private fun NotificationDayRow(day: NotificationDay, onClick: () -> Unit) {
    // Unread is otherwise conveyed only by the dot + bold weight (both visual-only); announce it to
    // TalkBack so read/unread rows don't sound identical.
    val unreadState = stringResource(Res.string.account_notification_unread_state)
    ListItem(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (day.hasUnread) stateDescription = unreadState },
        headlineContent = {
            Text(
                text = day.date,
                fontWeight = if (day.hasUnread) FontWeight.Bold else FontWeight.Normal,
            )
        },
        supportingContent = { Text(stringResource(Res.string.account_notifications_day_count, day.count)) },
        leadingContent = if (day.hasUnread) {
            { UnreadDot() }
        } else {
            null
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

@Preview
@Composable
private fun NotificationsScreenPreview() {
    GameDealsTheme {
        NotificationsScreenContent(
            data = NotificationsScreenData(
                loading = false,
                days = persistentListOf(
                    NotificationDay(date = "2026-06-18", count = 5, hasUnread = true),
                    NotificationDay(date = "2026-06-17", count = 2, hasUnread = true),
                    NotificationDay(date = "2026-06-15", count = 1, hasUnread = false),
                ),
            ),
            onBack = {},
            onDayClick = {},
            onMarkAllRead = {},
        )
    }
}

@Preview
@Composable
private fun NotificationsScreenEmptyPreview() {
    GameDealsTheme {
        NotificationsScreenContent(
            data = NotificationsScreenData(loading = false, days = persistentListOf()),
            onBack = {},
            onDayClick = {},
            onMarkAllRead = {},
        )
    }
}
