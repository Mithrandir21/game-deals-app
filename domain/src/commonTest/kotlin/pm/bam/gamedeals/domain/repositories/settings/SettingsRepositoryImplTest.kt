package pm.bam.gamedeals.domain.repositories.settings

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SettingsRepositoryImplTest {

    // Minimal in-memory [Storage] — the install-id path only touches getNullable/save with String values.
    private val backing = mutableMapOf<String, String>()
    private val storage = object : Storage {
        override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
            getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("no value for $storageKey")

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
            (backing[storageKey] as T?) ?: defaultValue

        override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
            backing[storageKey] = data as String
            return true
        }

        override suspend fun containsKey(storageKey: String): Boolean = backing.containsKey(storageKey)
        override suspend fun remove(storageKey: String): Boolean = backing.remove(storageKey) != null
    }

    private val repository = SettingsRepositoryImpl(storage)

    @Test
    fun install_id_is_generated_and_persisted_on_first_access() = runTest {
        val id = repository.getInstallId()

        assertTrue(id.isNotBlank())
        assertEquals(id, backing[INSTALL_ID_KEY])
    }

    @Test
    fun install_id_is_stable_across_calls() = runTest {
        val first = repository.getInstallId()
        val second = repository.getInstallId()

        assertEquals(first, second)
    }

    @Test
    fun a_previously_persisted_install_id_is_returned_unchanged() = runTest {
        backing[INSTALL_ID_KEY] = "existing-install-id"

        assertEquals("existing-install-id", repository.getInstallId())
    }

    @Test
    fun separate_installs_generate_distinct_ids() = runTest {
        val firstInstall = repository.getInstallId()

        // A fresh repository over empty storage models a reinstall / cleared data.
        backing.clear()
        val secondInstall = SettingsRepositoryImpl(storage).getInstallId()

        assertNotEquals(firstInstall, secondInstall)
    }
}
