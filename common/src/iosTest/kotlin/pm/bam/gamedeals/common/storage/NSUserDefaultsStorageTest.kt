package pm.bam.gamedeals.common.storage

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults
import pm.bam.gamedeals.common.exceptions.DataExistsException
import pm.bam.gamedeals.common.exceptions.DataNotFoundException
import pm.bam.gamedeals.common.serializer.SerializerImpl
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boundary test for [NSUserDefaultsStorage] using a real `NSUserDefaults` suite. Each test gets
 * its own randomly-named suite so state doesn't leak between cases (and never touches the
 * standard user defaults of the simulator host process).
 *
 * Mokkery isn't applied to `:common`, so we use the real `SerializerImpl` rather than a mock —
 * tests stay boundary-shaped because the only behaviour exercised is the round-trip of a String
 * payload through NSUserDefaults.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NSUserDefaultsStorageTest {

    private val key = "test-key"
    private val serializer = SerializerImpl(Json)
    private val stringStrategy = String.serializer()

    private lateinit var suiteName: String
    private lateinit var defaults: NSUserDefaults
    private lateinit var storage: NSUserDefaultsStorage

    @BeforeTest
    fun setUp() {
        suiteName = "ns-user-defaults-storage-test-${Random.nextLong()}"
        defaults = NSUserDefaults(suiteName = suiteName)!!
        storage = NSUserDefaultsStorage(serializer, defaults, UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suiteName)
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
        // First write must remain untouched.
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
}
