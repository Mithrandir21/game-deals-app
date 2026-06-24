package pm.bam.gamedeals.domain.repositories.notifications

import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import pm.bam.gamedeals.common.storage.Storage

/**
 * Persists the set of notification ids already surfaced to the OS tray, so background delivery
 * ([NotificationSync]) doesn't re-notify the same item on every poll. Backed by [Storage] (the same
 * `SETTINGS_QUALIFIER` key/value store as [AuthTokenStore][pm.bam.gamedeals.domain.auth.AuthTokenStore]).
 * The set is replaced (pruned to the current server set) each sync rather than appended to, so it can't
 * grow unbounded.
 */
interface SurfacedNotificationStore {
    suspend fun get(): Set<String>
    suspend fun replace(ids: Set<String>)
}

internal const val SURFACED_NOTIFICATION_IDS_KEY = "surfaced_notification_ids"

internal class SurfacedNotificationStoreImpl(
    private val storage: Storage,
) : SurfacedNotificationStore {

    override suspend fun get(): Set<String> =
        runCatching { storage.getNullable(SURFACED_NOTIFICATION_IDS_KEY, SetSerializer(String.serializer())) }
            .getOrNull() ?: emptySet()

    override suspend fun replace(ids: Set<String>) {
        storage.save(SURFACED_NOTIFICATION_IDS_KEY, ids, SetSerializer(String.serializer()))
    }
}
