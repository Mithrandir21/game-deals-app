package pm.bam.gamedeals.common.storage

import platform.Foundation.NSUserDefaults
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boundary test for [NSUserDefaultsBackend] using a real `NSUserDefaults` suite. Each test gets
 * its own randomly-named suite so state doesn't leak between cases (and never touches the
 * standard user defaults of the simulator host process).
 */
class NSUserDefaultsBackendTest {

    private val key = "test-key"

    private lateinit var suiteName: String
    private lateinit var defaults: NSUserDefaults
    private lateinit var backend: NSUserDefaultsBackend

    @BeforeTest
    fun setUp() {
        suiteName = "ns-user-defaults-backend-test-${Random.nextLong()}"
        defaults = NSUserDefaults(suiteName = suiteName)!!
        backend = NSUserDefaultsBackend(defaults)
    }

    @AfterTest
    fun tearDown() {
        defaults.removePersistentDomainForName(suiteName)
    }

    @Test
    fun readString_missing_key_returns_null() {
        assertNull(backend.readString(key))
    }

    @Test
    fun writeString_then_readString_round_trips() {
        assertTrue(backend.writeString(key, "value"))
        assertEquals("value", backend.readString(key))
    }

    @Test
    fun contains_reflects_write_and_remove() {
        assertFalse(backend.contains(key))
        backend.writeString(key, "value")
        assertTrue(backend.contains(key))
        backend.remove(key)
        assertFalse(backend.contains(key))
    }

    @Test
    fun remove_returns_true() {
        backend.writeString(key, "value")
        assertTrue(backend.remove(key))
    }
}
