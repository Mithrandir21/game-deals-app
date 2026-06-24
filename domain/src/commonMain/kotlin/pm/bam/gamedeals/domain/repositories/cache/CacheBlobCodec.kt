package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

/**
 * Cache-blob (de)serialization moved off the caller's dispatcher. A suspend repository read resumes on
 * the caller's dispatcher — the UI ViewModels run on `Dispatchers.Main` — so decoding/encoding a cached
 * JSON blob inline would run the CPU-bound parse on the main thread. [Dispatchers.Default] keeps it off.
 */
suspend fun <T> Json.decodeOffMain(deserializer: DeserializationStrategy<T>, string: String): T =
    withContext(Dispatchers.Default) { decodeFromString(deserializer, string) }

suspend fun <T> Json.encodeOffMain(serializer: SerializationStrategy<T>, value: T): String =
    withContext(Dispatchers.Default) { encodeToString(serializer, value) }
