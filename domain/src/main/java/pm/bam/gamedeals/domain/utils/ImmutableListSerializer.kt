package pm.bam.gamedeals.domain.utils

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for [ImmutableList] that delegates to kotlinx-serialization's built-in
 * [ListSerializer]. Without this, kotlinx-serialization treats `ImmutableList` as a
 * polymorphic interface and fails at runtime with
 * `Serializer for subclass 'SmallPersistentVector' is not found in the polymorphic scope
 * of 'ImmutableList'` whenever a `persistentListOf(...)` / `toImmutableList()` value
 * needs to be serialized — e.g. when `GiveawaySearchParameters` is round-tripped through
 * `Properties.encodeToMap` for `rememberSaveable` state restoration.
 *
 * Apply via `@file:UseSerializers(ImmutableListSerializer::class)` at the file level, or
 * `@Serializable(with = ImmutableListSerializer::class)` on individual fields.
 */
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
