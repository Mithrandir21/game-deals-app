package pm.bam.gamedeals.domain.utils

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Encodes a [LocalDateTime] as a UTC epoch-second long. The previous implementation used
 * `@Serializer(forClass = LocalDateTime::class)` for boilerplate-free codegen, but that
 * annotation requires the target class to live in the same module — `kotlinx.datetime.LocalDateTime`
 * doesn't, so the descriptor is declared explicitly.
 */
internal class LocalDateSerializer : KSerializer<LocalDateTime> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: LocalDateTime) =
        encoder.encodeLong(value.toInstant(TimeZone.UTC).epochSeconds)

    override fun deserialize(decoder: Decoder): LocalDateTime =
        Instant.fromEpochSeconds(decoder.decodeLong()).toLocalDateTime(TimeZone.UTC)
}
