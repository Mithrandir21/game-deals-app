package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the Notifications **list** sub-screen (#7 notification revamp): ITAD emits one notification entry per
 * game price-drop, so the list groups them by **calendar day** — one row per day (e.g. "5 price drops"),
 * tapping it opens that day's [NotificationDayScreen] (the games + deals notified that day).
 *
 * Observes the reactive notifications list (so per-game mark-read edits made in the day detail reflect here
 * immediately) and triggers the remote-as-truth load on open. [onMarkAllRead] goes through the repository.
 */
internal class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<NotificationsScreenData>
        field = MutableStateFlow(NotificationsScreenData(loading = true))

    init {
        viewModelScope.launch {
            notificationsRepository.observeNotifications().collect { list ->
                val days = list
                    .groupBy { it.timestamp.substringBefore('T') }
                    .map { (date, entries) -> NotificationDay(date = date, count = entries.size, hasUnread = entries.any { !it.read }) }
                    .sortedByDescending { it.date }
                    .toImmutableList()
                uiState.update { it.copy(days = days) }
            }
        }
        viewModelScope.launch {
            runCatching { notificationsRepository.getNotifications() }.onFailure { fatal(logger, it) }
            uiState.update { it.copy(loading = false) }
        }
    }

    fun onMarkAllRead() {
        viewModelScope.launch {
            runCatching { notificationsRepository.markAllRead() }.onFailure { fatal(logger, it) }
        }
    }

    /** One calendar day of notifications. [date] is the ISO date (e.g. "2026-06-18") and the nav key. */
    @Immutable
    data class NotificationDay(
        val date: String,
        val count: Int,
        val hasUnread: Boolean,
    )

    @Immutable
    data class NotificationsScreenData(
        val loading: Boolean = false,
        val days: ImmutableList<NotificationDay> = persistentListOf(),
    ) {
        val hasUnread: Boolean get() = days.any { it.hasUnread }
    }
}
