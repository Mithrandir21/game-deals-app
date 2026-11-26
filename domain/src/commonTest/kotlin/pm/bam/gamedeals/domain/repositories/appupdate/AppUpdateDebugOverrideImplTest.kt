package pm.bam.gamedeals.domain.repositories.appupdate

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The debug stand-in for the `force_update` payload. Small, but it carries the same "not yet read" vs
 * "read, but unset" distinction that `SettingsRepositoryImpl` needed a wrapper for — both are `null` at the
 * flow's surface, and getting it wrong means either re-reading storage on every collection or caching a cold
 * read forever.
 */
class AppUpdateDebugOverrideImplTest {

    private val payload = """{"minimum_version":"99.0.0","blocking":true}"""

    @Test
    fun `no override is set on a cold install`() = runTest {
        assertNull(AppUpdateDebugOverrideImpl(FakeStorage()).observe().first())
    }

    @Test
    fun `a set override is observed`() = runTest {
        val override = AppUpdateDebugOverrideImpl(FakeStorage())

        override.set(payload)

        assertEquals(payload, override.observe().first())
    }

    @Test
    fun `an override reaches storage under the documented key`() = runTest {
        // The key is a stored contract: a value written by a previous install must still be found.
        val storage = FakeStorage()

        AppUpdateDebugOverrideImpl(storage).set(payload)

        assertTrue(storage.containsKey(OVERRIDE_KEY))
        assertEquals(payload, AppUpdateDebugOverrideImpl(storage).observe().first())
    }

    @Test
    fun `clearing removes the key and hands control back to the real flag`() = runTest {
        val storage = FakeStorage()
        val override = AppUpdateDebugOverrideImpl(storage)
        override.set(payload)

        override.set(null)

        assertNull(override.observe().first())
        // Removed, not merely nulled in the flow — otherwise a fresh process would resurrect it.
        assertFalse(storage.containsKey(OVERRIDE_KEY))
        assertNull(AppUpdateDebugOverrideImpl(storage).observe().first())
    }

    @Test
    fun `a later set replaces the previous override`() = runTest {
        val override = AppUpdateDebugOverrideImpl(FakeStorage())

        override.set(payload)
        override.set("""{"minimum_version":"1.0.0"}""")

        assertEquals("""{"minimum_version":"1.0.0"}""", override.observe().first())
    }

    @Test
    fun `a cleared override is not re-read from storage on a new collection`() = runTest {
        // Regression guard for the Snapshot wrapper: after set(null) the cached snapshot is non-null but
        // holds null, so onStart must not treat it as "never loaded" and go back to storage.
        val storage = FakeStorage(stored = mapOf(OVERRIDE_KEY to payload))
        val override = AppUpdateDebugOverrideImpl(storage)
        assertEquals(payload, override.observe().first())

        override.set(null)

        assertNull(override.observe().first())
        assertNull(override.observe().first())
    }

    @Test
    fun `a failing storage read fails open to no override`() = runTest {
        // Corrupt or schema-drifted storage must not crash the update gate on launch.
        assertNull(AppUpdateDebugOverrideImpl(FakeStorage(throwOnGet = true)).observe().first())
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
