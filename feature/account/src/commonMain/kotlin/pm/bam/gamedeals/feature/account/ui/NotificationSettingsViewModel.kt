package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.scheduling.NotificationScheduler

/**
 * Backs the Account hub's background-alerts toggle (background-notifications feature, Phase D). Exposes the
 * persisted opt-in flag reactively and, on toggle, persists it **and** (de)registers the platform poll via
 * [NotificationScheduler]. The OS permission prompt is handled in the composable
 * ([rememberNotificationPermissionRequester][pm.bam.gamedeals.common.ui.platform.rememberNotificationPermissionRequester]);
 * [onEnable] is only called once permission is granted.
 */
internal class NotificationSettingsViewModel(
    private val settings: NotificationSettings,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onEnable() {
        viewModelScope.launch {
            settings.setEnabled(true)
            scheduler.schedule()
        }
    }

    fun onDisable() {
        viewModelScope.launch {
            settings.setEnabled(false)
            scheduler.cancel()
        }
    }
}
