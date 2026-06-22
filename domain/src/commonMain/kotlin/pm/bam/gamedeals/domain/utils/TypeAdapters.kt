package pm.bam.gamedeals.domain.utils

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import pm.bam.gamedeals.common.serializer.Serializer
import pm.bam.gamedeals.common.serializer.deserialize
import pm.bam.gamedeals.common.serializer.serialize
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.Store

@ProvidedTypeConverter
class StoreImagesConverter(
    private val serializer: Serializer
) {
    @TypeConverter
    fun convertToJsonString(priceDetails: Store.StoreImages): String = serializer.serialize(priceDetails)

    @TypeConverter
    fun convertToObject(json: String): Store.StoreImages = serializer.deserialize(json)
}

@OptIn(ExperimentalTime::class)
@ProvidedTypeConverter
class LocalDatetimeConverter {
    @TypeConverter
    fun convertToJsonString(localDateTime: LocalDateTime): Long =
        localDateTime.toInstant(TimeZone.UTC).epochSeconds

    @TypeConverter
    fun convertToObject(json: Long): LocalDateTime =
        Instant.fromEpochSeconds(json).toLocalDateTime(TimeZone.UTC)
}

@ProvidedTypeConverter
class GiveawayPlatformsConverter {
    @TypeConverter
    fun convertToJsonString(platforms: ImmutableList<GiveawayPlatform>): String = platforms.joinToString(separator = ", ")

    @TypeConverter
    fun convertToObject(json: String): ImmutableList<GiveawayPlatform> =
        json.split(", ")
            // Skip any token that isn't a current GiveawayPlatform constant. A bare valueOf() throws
            // IllegalArgumentException on read for an unknown/renamed platform name, which would crash
            // giveaway hydration; Giveaway is a TTL cache, so dropping the stray token is safe.
            .mapNotNull { name -> GiveawayPlatform.entries.firstOrNull { it.name == name } }
            .toImmutableList()
}
