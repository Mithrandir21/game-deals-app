package pm.bam.gamedeals.common.storage

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.exceptions.DataExistsException
import pm.bam.gamedeals.common.exceptions.DataNotFoundException
import pm.bam.gamedeals.common.serializer.SerializerImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StorageImplTest {

    private val key = "test-key"
    private val stringStrategy = String.serializer()

    private lateinit var backend: FakeKeyValueBackend
    private lateinit var storage: StorageImpl

    @BeforeTest
    fun setUp() {
        backend = FakeKeyValueBackend()
        storage = StorageImpl(SerializerImpl(Json), backend, UnconfinedTestDispatcher())
    }

    @Test
    fun getNullable_missing_key_returns_default() = runTest {
        assertEquals("fallback", storage.getNullable(key, stringStrategy, defaultValue = "fallback"))
    }

    @Test
    fun getNullable_missing_key_with_no_default_returns_null() = runTest {
        assertNull(storage.getNullable(key, stringStrategy))
    }

    @Test
    fun save_then_get_round_trips_via_serializer() = runTest {
        storage.save(key, "payload", stringStrategy, overwrite = true)
        assertEquals("payload", storage.get(key, stringStrategy))
    }

    @Test
    fun get_missing_key_throws_DataNotFoundException() = runTest {
        assertFails { storage.get(key, stringStrategy) }.also {
            assertTrue(it is DataNotFoundException)
        }
    }

    @Test
    fun save_overwrite_false_with_existing_key_throws_DataExistsException() = runTest {
        storage.save(key, "first", stringStrategy, overwrite = true)

        assertFails { storage.save(key, "second", stringStrategy, overwrite = false) }.also {
            assertTrue(it is DataExistsException)
        }
        assertEquals("first", storage.get(key, stringStrategy))
    }

    @Test
    fun save_overwrite_true_replaces_existing_value() = runTest {
        storage.save(key, "first", stringStrategy, overwrite = true)
        storage.save(key, "second", stringStrategy, overwrite = true)

        assertEquals("second", storage.get(key, stringStrategy))
    }

    @Test
    fun containsKey_reflects_save_and_remove() = runTest {
        assertFalse(storage.containsKey(key))
        storage.save(key, "payload", stringStrategy, overwrite = true)
        assertTrue(storage.containsKey(key))
        storage.remove(key)
        assertFalse(storage.containsKey(key))
    }

    @Test
    fun remove_returns_true_and_clears_value() = runTest {
        storage.save(key, "payload", stringStrategy, overwrite = true)

        assertTrue(storage.remove(key))
        assertNull(storage.getNullable(key, stringStrategy))
    }

    private class FakeKeyValueBackend : KeyValueBackend {
        private val store = mutableMapOf<String, String>()

        override fun readString(key: String): String? = store[key]

        override fun writeString(key: String, value: String): Boolean {
            store[key] = value
            return true
        }

        override fun contains(key: String): Boolean = store.containsKey(key)

        override fun remove(key: String): Boolean = store.remove(key) != null
    }
}
