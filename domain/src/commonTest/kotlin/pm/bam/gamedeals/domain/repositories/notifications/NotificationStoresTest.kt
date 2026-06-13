package pm.bam.gamedeals.domain.repositories.notifications

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Storage-backed round-trips for the background-notification stores. */
class NotificationStoresTest {

    @Test
    fun surfaced_store_defaults_to_empty_then_round_trips() = runTest {
        val store = SurfacedNotificationStoreImpl(FakeStorage())

        assertEquals(emptySet(), store.get())

        store.replace(setOf("n1", "n2"))
        assertEquals(setOf("n1", "n2"), store.get())

        store.replace(setOf("n3")) // replace, not append
        assertEquals(setOf("n3"), store.get())
    }

    @Test
    fun settings_default_off_then_round_trips_and_emits() = runTest {
        val settings = NotificationSettingsImpl(FakeStorage())

        assertFalse(settings.isEnabled())
        assertFalse(settings.observeEnabled().first())

        settings.setEnabled(true)
        assertTrue(settings.isEnabled())
        assertTrue(settings.observeEnabled().first())
    }
}

private class FakeStorage(stored: Map<String, Any> = emptyMap()) : Storage {
    private val saved = stored.toMutableMap()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        (saved[storageKey] as T?) ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        (saved[storageKey] as T?) ?: defaultValue ?: error("no value for $storageKey")

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        saved[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = saved.containsKey(storageKey)
    override suspend fun remove(storageKey: String): Boolean = saved.remove(storageKey) != null
}
