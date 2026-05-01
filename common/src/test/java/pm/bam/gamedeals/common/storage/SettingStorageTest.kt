package pm.bam.gamedeals.common.storage

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.bam.gamedeals.common.exceptions.DataExistsException
import pm.bam.gamedeals.common.exceptions.DataNotFoundException
import pm.bam.gamedeals.common.serializer.Serializer

/**
 * Boundary test for [SettingStorage]: verifies that the suspending API performs the expected
 * SharedPreferences read / write side effects via a mocked editor, and that exception cases
 * surface correctly.
 *
 * The implementation wraps every prefs call in `withContext(Dispatchers.IO)`; we substitute an
 * [UnconfinedTestDispatcher] so the suspend functions execute synchronously inside `runTest`
 * without relying on real I/O scheduling. No Robolectric / Android runtime needed.
 */
class SettingStorageTest {

    private val key = "test-key"
    private val stringStrategy: SerializationStrategy<String> = String.serializer()
    private val deserStrategy: DeserializationStrategy<String> = String.serializer()

    private val serializer: Serializer = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()

    private val storage = SettingStorage(serializer, sharedPreferences, dispatcher)

    @Test
    fun `getNullable - missing key returns default`() = runTest {
        every { sharedPreferences.getString(key, null) } returns null

        val result = storage.getNullable(key, deserStrategy, defaultValue = "fallback")

        assertEquals("fallback", result)
    }

    @Test
    fun `getNullable - missing key with no default returns null`() = runTest {
        every { sharedPreferences.getString(key, null) } returns null

        val result = storage.getNullable(key, deserStrategy)

        assertNull(result)
    }

    @Test
    fun `getNullable - present key deserializes via Serializer`() = runTest {
        every { sharedPreferences.getString(key, null) } returns "serialized"
        every { serializer.deserialize<String>("serialized", deserStrategy) } returns "decoded"

        val result = storage.getNullable(key, deserStrategy)

        assertEquals("decoded", result)
    }

    @Test
    fun `get - missing key throws DataNotFoundException`() = runTest {
        every { sharedPreferences.getString(key, null) } returns null

        assertThrows(DataNotFoundException::class.java) {
            kotlinx.coroutines.runBlocking { storage.get(key, deserStrategy) }
        }
    }

    @Test
    fun `save - overwrite true commits serialized value`() = runTest {
        every { serializer.serialize("payload", stringStrategy) } returns "serialized-payload"
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(key, "serialized-payload") } returns editor
        every { editor.commit() } returns true

        val result = storage.save(key, "payload", stringStrategy, overwrite = true)

        assertTrue(result)
        verify { editor.putString(key, "serialized-payload") }
        verify { editor.commit() }
    }

    @Test
    fun `save - overwrite false with existing key throws DataExistsException`() = runTest {
        every { sharedPreferences.contains(key) } returns true

        assertThrows(DataExistsException::class.java) {
            kotlinx.coroutines.runBlocking {
                storage.save(key, "payload", stringStrategy, overwrite = false)
            }
        }
        // Implementation must not attempt any write when the key already exists.
        verify(exactly = 0) { sharedPreferences.edit() }
    }

    @Test
    fun `containsKey - delegates to SharedPreferences`() = runTest {
        every { sharedPreferences.contains(key) } returns true

        assertTrue(storage.containsKey(key))
        verify { sharedPreferences.contains(key) }
    }

    @Test
    fun `remove - commits editor remove and returns its result`() = runTest {
        every { sharedPreferences.edit() } returns editor
        every { editor.remove(key) } returns editor
        every { editor.commit() } returns false

        val result = storage.remove(key)

        assertFalse(result)
        verify { editor.remove(key) }
        verify { editor.commit() }
    }
}
