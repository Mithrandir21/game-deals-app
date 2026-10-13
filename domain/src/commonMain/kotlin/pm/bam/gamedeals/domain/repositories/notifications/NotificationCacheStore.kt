package pm.bam.gamedeals.domain.repositories.notifications

import kotlinx.serialization.Serializable
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationDetail

/**
 * What the app keeps of the user's notifications between launches, so a launch or a screen open doesn't refetch them.
 * A notification's detail is a snapshot taken when ITAD issued it, so it is kept until the notification leaves the list;
 * [details] are stored as fetched and joined with [art] (game id → artwork, which never changes) when read.
 */
@Serializable
internal data class NotificationCache(
    val notifications: List<ItadNotification> = emptyList(),
    /** When [notifications] was last fetched; 0 when never. */
    val fetchedAtMillis: Long = 0L,
    val details: Map<String, NotificationDetail> = emptyMap(),
    val art: Map<String, GameArtwork> = emptyMap(),
)

/**
 * Persists the [NotificationCache]. Backed by [Storage] (the same `SETTINGS_QUALIFIER` store as
 * [SurfacedNotificationStore]); replaced wholesale on each change and tolerant of schema drift ([get] returns null).
 */
internal interface NotificationCacheStore {
    suspend fun get(): NotificationCache?
    suspend fun replace(cache: NotificationCache)
    suspend fun clear()
}

internal const val NOTIFICATION_CACHE_KEY = "notification_cache"

internal class NotificationCacheStoreImpl(
    private val storage: Storage,
) : NotificationCacheStore {

    override suspend fun get(): NotificationCache? =
        runCatching { storage.getNullable<NotificationCache>(NOTIFICATION_CACHE_KEY) }.getOrNull()

    override suspend fun replace(cache: NotificationCache) {
        storage.save(NOTIFICATION_CACHE_KEY, cache)
    }

    override suspend fun clear() {
        storage.remove(NOTIFICATION_CACHE_KEY)
    }
}
