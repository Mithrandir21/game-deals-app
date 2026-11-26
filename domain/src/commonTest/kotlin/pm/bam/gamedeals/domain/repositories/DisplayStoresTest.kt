package pm.bam.gamedeals.domain.repositories

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.repositories.collection.CollectionDisplayStoreImpl
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistDisplayStoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Storage-backed round-trips for the two enriched display caches. Both return null (not empty) on a cold
 * cache and on any read failure, so the UI can distinguish "not yet loaded" from "loaded and empty".
 */
class DisplayStoresTest {

    // --- CollectionDisplayStore ---

    @Test
    fun collection_cold_cache_is_null() = runTest {
        assertNull(CollectionDisplayStoreImpl(FakeStorage()).get())
    }

    @Test
    fun collection_replace_round_trips_then_clear_returns_to_null() = runTest {
        val store = CollectionDisplayStoreImpl(FakeStorage())
        val games = listOf(CollectionEntry(gameId = "g1", title = "Halo"))

        store.replace(games)
        assertEquals(games, store.get())

        store.clear()
        assertNull(store.get())
    }

    @Test
    fun collection_empty_list_round_trips_as_empty_not_null() = runTest {
        // A loaded-but-empty collection must read back as empty (distinct from the null cold cache).
        val store = CollectionDisplayStoreImpl(FakeStorage())

        store.replace(emptyList())

        assertEquals(emptyList(), store.get())
    }

    @Test
    fun collection_falls_back_to_null_on_a_failing_read() = runTest {
        assertNull(CollectionDisplayStoreImpl(FakeStorage(throwOnGet = true)).get())
    }

    // --- WaitlistDisplayStore ---

    @Test
    fun waitlist_cold_cache_is_null() = runTest {
        assertNull(WaitlistDisplayStoreImpl(FakeStorage()).get())
    }

    @Test
    fun waitlist_replace_round_trips_then_clear_returns_to_null() = runTest {
        val store = WaitlistDisplayStoreImpl(FakeStorage())
        val snapshot = WaitlistDisplaySnapshot(regionCode = "US", refreshedAtEpochMs = 1_700_000_000_000L)

        store.replace(snapshot)
        assertEquals(snapshot, store.get())

        store.clear()
        assertNull(store.get())
    }

    @Test
    fun waitlist_falls_back_to_null_on_a_failing_read() = runTest {
        assertNull(WaitlistDisplayStoreImpl(FakeStorage(throwOnGet = true)).get())
    }
}

/** In-memory [Storage] holding values directly (per the project convention in NotificationStoresTest). */
private class FakeStorage(
    stored: Map<String, Any> = emptyMap(),
    private val throwOnGet: Boolean = false,
) : Storage {
    private val saved = stored.toMutableMap()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? {
        if (throwOnGet) throw RuntimeException("corrupt storage")
        return (saved[storageKey] as T?) ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T {
        if (throwOnGet) throw RuntimeException("corrupt storage")
        return (saved[storageKey] as T?) ?: defaultValue ?: error("no value for $storageKey")
    }

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        saved[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = saved.containsKey(storageKey)
    override suspend fun remove(storageKey: String): Boolean = saved.remove(storageKey) != null
}
