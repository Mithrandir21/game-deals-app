package pm.bam.gamedeals.common.storage

import pm.bam.gamedeals.common.exceptions.DataExistsException
import pm.bam.gamedeals.common.exceptions.DataNotFoundException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy


interface Storage {

    /**
     * Retrieves the data found at the given [storageKey] mapping, or the [defaultValue] if given.
     * Also deserializes to given type token for complex types such as as Lists, Sets or Maps.
     *
     * @throws DataNotFoundException if no data found for given [storageKey] and no [defaultValue] is given.
     */
    @Throws(DataNotFoundException::class)
    suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T? = null): T

    /**
     * Retrieves the data found at the given [storageKey] mapping, or the [defaultValue] if given.
     * Also deserializes to given type token for complex types such as as Lists, Sets or Maps.
     */
    suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T? = null): T?

    /**
     * Saves the data at the given [storageKey] mapping as a serialized string for complex types such as Lists, Sets or Maps,
     * overwriting only if the given boolean is True.
     *
     * @throws DataExistsException if [overwrite] is true and data exists for the given [storageKey].
     */
    @Throws(DataExistsException::class)
    suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean = true): Boolean

    /** Returns a boolean indicating whether the storage contains data for the given [storageKey]. */
    suspend fun containsKey(storageKey: String): Boolean

    /**
     * Removes any data found at the given [storageKey] mapping.
     *
     * @return True for remove data, False for any other scenario.
     */
    suspend fun remove(storageKey: String): Boolean
}
