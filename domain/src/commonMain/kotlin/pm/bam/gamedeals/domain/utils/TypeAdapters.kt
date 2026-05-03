package pm.bam.gamedeals.domain.utils

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlinx.datetime.Instant
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
    fun convertToJsonString(platforms: List<GiveawayPlatform>): String = platforms.joinToString(separator = ", ")

    @TypeConverter
    fun convertToObject(json: String): List<GiveawayPlatform> = json.split(", ").map { GiveawayPlatform.valueOf(it) }
}
