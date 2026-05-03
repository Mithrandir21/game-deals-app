package pm.bam.gamedeals.common.serializer

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import javax.inject.Inject


internal class SerializerImpl @Inject constructor(private val json: Json) : Serializer {

    override fun <T : Any> serialize(deserialized: T, serializationStrategy: SerializationStrategy<T>): String =
        json.encodeToString(serializationStrategy, deserialized)

    override fun <T : Any> deserialize(serialized: String, deserializationStrategy: DeserializationStrategy<T>): T =
        json.decodeFromString(deserializationStrategy, serialized)
}