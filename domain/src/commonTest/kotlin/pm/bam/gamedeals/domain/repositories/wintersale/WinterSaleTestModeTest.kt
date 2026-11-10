package pm.bam.gamedeals.domain.repositories.wintersale

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.version.AppInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinterSaleTestModeTest {
    private val storage = TestModeMemoryStorage()

    @Test
    fun a_debug_build_reads_the_test_document_once_switched_on_and_remembers_it() = runTest {
        val mode = WinterSaleTestModeImpl(storage, appInfo(debug = true))
        assertFalse(mode.isOn())

        mode.set(true)
        assertTrue(mode.isOn())
        assertTrue(WinterSaleTestModeImpl(storage, appInfo(debug = true)).isOn())

        mode.set(false)
        assertFalse(mode.observe().first())
        assertFalse(WinterSaleTestModeImpl(storage, appInfo(debug = true)).isOn())
    }

    @Test
    fun a_release_build_ignores_a_stored_value() = runTest {
        WinterSaleTestModeImpl(storage, appInfo(debug = true)).set(true)

        val release = WinterSaleTestModeImpl(storage, appInfo(debug = false))

        assertFalse(release.isOn())
        assertFalse(release.observe().first())
    }

    private fun appInfo(debug: Boolean) = AppInfo(versionName = "1.0.0", versionCode = 1, storeId = "", isDebug = debug)
}

private class TestModeMemoryStorage : Storage {
    private val values = mutableMapOf<String, Any>()

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("Missing $storageKey")

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        values[storageKey] as? T ?: defaultValue

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        values[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = storageKey in values
    override suspend fun remove(storageKey: String): Boolean = values.remove(storageKey) != null
}
