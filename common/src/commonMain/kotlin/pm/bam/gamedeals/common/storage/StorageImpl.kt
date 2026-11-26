package pm.bam.gamedeals.common.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.exceptions.DataExistsException
import pm.bam.gamedeals.common.exceptions.DataNotFoundException
import pm.bam.gamedeals.common.serializer.Serializer

internal class StorageImpl(
    private val serializer: Serializer,
    private val backend: KeyValueBackend,
    private val ioDispatcher: CoroutineDispatcher,
) : Storage {

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: throw DataNotFoundException(storageKey)

    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        withContext(ioDispatcher) {
            backend.readString(storageKey)
                ?.let { serializer.deserialize(it, deserializationStrategy) }
                .let { it ?: defaultValue }
        }

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean =
        withContext(ioDispatcher) {
            if (!overwrite && backend.contains(storageKey)) {
                throw DataExistsException(storageKey)
            }
            backend.writeString(storageKey, serializer.serialize(data, serializationStrategy))
        }

    override suspend fun containsKey(storageKey: String): Boolean = withContext(ioDispatcher) {
        backend.contains(storageKey)
    }

    override suspend fun remove(storageKey: String): Boolean = withContext(ioDispatcher) {
        backend.remove(storageKey)
    }
}
