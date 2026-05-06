package pm.bam.gamedeals.domain.utils

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Without this, kotlinx-serialization treats `ImmutableList` as a polymorphic interface and
// throws "Serializer for subclass 'SmallPersistentVector' is not found" at runtime.
class ImmutableListSerializer<T>(
    private val dataSerializer: KSerializer<T>,
) : KSerializer<ImmutableList<T>> {

    private val delegate = ListSerializer(dataSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: ImmutableList<T>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): ImmutableList<T> =
        delegate.deserialize(decoder).toImmutableList()
}
