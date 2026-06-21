package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_day_more
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_day_subtitle
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_day_subtitle_one
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_mark_all_read
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_more_actions
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_unread_state
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notifications
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationDay
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationGameThumb
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
                    itemsIndexed(data.days, key = { _, day -> day.date }) { index, day ->
                        NotificationDayRow(day = day, onClick = { onDayClick(day) })
                        // Separate each day from the next; no trailing divider after the last row.
                        if (index < data.days.lastIndex) HorizontalDivider()
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
    val subtitle = if (day.count == 1) {
        stringResource(Res.string.account_notifications_day_subtitle_one, day.date)
    } else {
        stringResource(Res.string.account_notifications_day_subtitle, day.date, day.count)
    }
    ListItem(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (day.hasUnread) stateDescription = unreadState },
        headlineContent = {
            Text(
                text = dayTitle(day),
                fontWeight = if (day.hasUnread) FontWeight.Bold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                Text(subtitle)
                GameThumbnailStrip(day.games)
            }
        },
        leadingContent = if (day.hasUnread) {
            { UnreadDot() }
        } else {
            null
        },
    )
}

/** A scrollable strip of the day's game covers (wide Steam-header banners). Decorative — the row title
 *  already names the games — so each image is hidden from TalkBack. Missing art shows the placeholder. */
@Composable
private fun GameThumbnailStrip(games: List<NotificationGameThumb>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        games.forEach { game ->
            AsyncImage(
                model = game.thumbnailUrl,
                contentDescription = null,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(THUMBNAIL_HEIGHT)
                    // Steam-header ratio (≈2.14:1) — the variant GameArtwork.thumbnail resolves to.
                    .aspectRatio(2.14f)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
            )
        }
    }
}

private val THUMBNAIL_HEIGHT = 36.dp

/** The games that dropped that day: the first [MAX_TITLE_NAMES] names, then a "+N more" overflow. Falls
 *  back to the date if the games couldn't be resolved (every detail fetch failed). */
@Composable
private fun dayTitle(day: NotificationDay): String {
    if (day.games.isEmpty()) return day.date
    val shown = day.games.take(MAX_TITLE_NAMES).joinToString(", ") { it.title }
    val remaining = day.games.size - MAX_TITLE_NAMES
    return if (remaining > 0) {
        shown + " " + stringResource(Res.string.account_notifications_day_more, remaining)
    } else {
        shown
    }
}

private const val MAX_TITLE_NAMES = 3

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
                    NotificationDay(
                        date = "2026-06-18",
                        games = persistentListOf(
                            NotificationGameThumb("1", "Halo", null),
                            NotificationGameThumb("2", "Hades", null),
                            NotificationGameThumb("3", "Celeste", null),
                            NotificationGameThumb("4", "Stardew Valley", null),
                            NotificationGameThumb("5", "Hollow Knight", null),
                        ),
                        count = 5,
                        hasUnread = true,
                    ),
                    NotificationDay(
                        date = "2026-06-17",
                        games = persistentListOf(
                            NotificationGameThumb("6", "Cyberpunk 2077", null),
                            NotificationGameThumb("7", "Disco Elysium", null),
                        ),
                        count = 2,
                        hasUnread = true,
                    ),
                    NotificationDay(
                        date = "2026-06-15",
                        games = persistentListOf(NotificationGameThumb("8", "Baldur's Gate 3", null)),
                        count = 1,
                        hasUnread = false,
                    ),
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
