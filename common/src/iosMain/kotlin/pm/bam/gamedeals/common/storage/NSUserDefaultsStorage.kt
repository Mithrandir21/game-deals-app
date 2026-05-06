package pm.bam.gamedeals.common.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import platform.Foundation.NSUserDefaults
import pm.bam.gamedeals.common.exceptions.DataExistsException
import pm.bam.gamedeals.common.exceptions.DataNotFoundException
import pm.bam.gamedeals.common.serializer.Serializer

/**
 * iOS counterpart of Android's `SettingStorage`. Persists serialized JSON strings under the
 * supplied [NSUserDefaults] suite.
 *
 * Two behavioural differences vs. the SharedPreferences-backed Android impl, both inherent to
 * NSUserDefaults itself:
 *  - `save()` always returns `true`. NSUserDefaults has no synchronous commit; `synchronize()`
 *    has been deprecated since iOS 12 and the docs explicitly say to ignore it. Writes are
 *    persisted on a framework-managed schedule and the API surfaces no failure signal callers
 *    could honour.
 *  - `Dispatchers.Default` rather than `Dispatchers.IO`. Kotlin/Native's `IO` aliases to the
 *    default pool anyway; using `Default` directly keeps the dispatcher choice honest.
 */
internal class NSUserDefaultsStorage(
    private val serializer: Serializer,
    private val defaults: NSUserDefaults,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Storage {

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: throw DataNotFoundException(storageKey)

    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        withContext(ioDispatcher) {
            defaults.stringForKey(storageKey)
                ?.let { serializedData -> serializer.deserialize(serializedData, deserializationStrategy) }
                .let { data -> data ?: defaultValue }
        }

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean =
        withContext(ioDispatcher) {
            if (!overwrite && defaults.objectForKey(storageKey) != null) {
                throw DataExistsException(storageKey)
            }
            defaults.setObject(serializer.serialize(data, serializationStrategy), forKey = storageKey)
            true
        }

    override suspend fun containsKey(storageKey: String): Boolean = withContext(ioDispatcher) {
        defaults.objectForKey(storageKey) != null
    }

    override suspend fun remove(storageKey: String): Boolean = withContext(ioDispatcher) {
        defaults.removeObjectForKey(storageKey)
        true
    }
}
