package pm.bam.gamedeals.domain.repositories.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save

/**
 * The user's opt-in preference for background (OS-tray) notification delivery — **default OFF**. Backed by
 * [Storage] and exposed reactively via a lazily-seeded `StateFlow`, mirroring
 * [AuthTokenStoreImpl][pm.bam.gamedeals.domain.auth.AuthTokenStoreImpl]. Toggling this drives the platform
 * [NotificationScheduler][pm.bam.gamedeals.domain.scheduling.NotificationScheduler] on/off (see the hub toggle).
 */
interface NotificationSettings {
    fun observeEnabled(): Flow<Boolean>
    suspend fun isEnabled(): Boolean
    suspend fun setEnabled(enabled: Boolean)
}

internal const val NOTIFICATIONS_ENABLED_KEY = "background_notifications_enabled"

internal class NotificationSettingsImpl(
    private val storage: Storage,
) : NotificationSettings {

    // Lazily seeded from storage on first access (null = not yet loaded), like AuthTokenStoreImpl.
    private val enabled = MutableStateFlow<Boolean?>(null)

    override fun observeEnabled(): Flow<Boolean> =
        enabled
            .onStart { if (enabled.value == null) enabled.value = load() }
            .filterNotNull()

    override suspend fun isEnabled(): Boolean = load()

    override suspend fun setEnabled(enabled: Boolean) {
        storage.save(NOTIFICATIONS_ENABLED_KEY, enabled)
        this.enabled.value = enabled
    }

    private suspend fun load(): Boolean =
        runCatching { storage.getNullable<Boolean>(NOTIFICATIONS_ENABLED_KEY) }.getOrNull() ?: false
}
