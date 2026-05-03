package pm.bam.gamedeals.common.storage

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.di.Settings
import pm.bam.gamedeals.common.exceptions.DataExistsException
import pm.bam.gamedeals.common.exceptions.DataNotFoundException
import pm.bam.gamedeals.common.serializer.Serializer
import javax.inject.Inject

internal class SettingStorage @Inject constructor(
    private val serializer: Serializer,
    @Settings val sharedPreferences: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Storage {

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: throw DataNotFoundException(storageKey)

    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        withContext(ioDispatcher) {
            sharedPreferences.getString(storageKey, null)
                ?.let { serializedData -> serializer.deserialize(serializedData, deserializationStrategy) }
                .let { data -> data ?: defaultValue }
        }

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean =
        withContext(ioDispatcher) {
            if (!overwrite && sharedPreferences.contains(storageKey)) {
                throw DataExistsException(storageKey)
            }

            // commit() is intentionally retained over apply(): we are already off the main thread
            // inside withContext(Dispatchers.IO), and callers that suspend on save() expect a
            // truthful Boolean result reflecting whether the write actually succeeded.
            sharedPreferences.edit().putString(storageKey, serializer.serialize(data, serializationStrategy)).commit()
        }

    override suspend fun containsKey(storageKey: String): Boolean = withContext(ioDispatcher) {
        sharedPreferences.contains(storageKey)
    }

    override suspend fun remove(storageKey: String): Boolean = withContext(ioDispatcher) {
        sharedPreferences.edit().remove(storageKey).commit()
    }
}
