package pm.bam.gamedeals.domain.repositories.collection

import kotlinx.serialization.builtins.ListSerializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.CollectionEntry

/**
 * Persists the enriched collection snapshot (title/art/type/added — no price, the games are owned) so the
 * Collection list renders instantly and offline on open. Backed by [Storage] (the same `SETTINGS_QUALIFIER`
 * store as the waitlist display cache). Replaced wholesale; [get] returns null on a cold cache or any read
 * failure (lets the UI distinguish "not yet loaded" from "empty").
 */
interface CollectionDisplayStore {
    suspend fun get(): List<CollectionEntry>?
    suspend fun replace(games: List<CollectionEntry>)
    suspend fun clear()
}

internal const val COLLECTION_DISPLAY_SNAPSHOT_KEY = "collection_display_snapshot"

internal class CollectionDisplayStoreImpl(
    private val storage: Storage,
) : CollectionDisplayStore {

    override suspend fun get(): List<CollectionEntry>? =
        runCatching {
            storage.getNullable(COLLECTION_DISPLAY_SNAPSHOT_KEY, ListSerializer(CollectionEntry.serializer()))
        }.getOrNull()

    override suspend fun replace(games: List<CollectionEntry>) {
        storage.save(COLLECTION_DISPLAY_SNAPSHOT_KEY, games, ListSerializer(CollectionEntry.serializer()))
    }

    override suspend fun clear() {
        storage.remove(COLLECTION_DISPLAY_SNAPSHOT_KEY)
    }
}
