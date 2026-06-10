@file:UseSerializers(ImmutableListSerializer::class)

package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.properties.Properties
import kotlinx.datetime.LocalDateTime
import pm.bam.gamedeals.domain.utils.ImmutableListSerializer
import pm.bam.gamedeals.domain.utils.LocalDateSerializer

@Entity(tableName = "Giveaway")
@Immutable
@Serializable
data class Giveaway(
    @PrimaryKey
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String,
    @SerialName("worthDenominated")
    val worthDenominated: String?,
    @SerialName("worth")
    val worth: Double?,
    @SerialName("thumbnail")
    val thumbnail: String,
    @SerialName("image")
    val image: String,
    @SerialName("description")
    val description: String,
    @SerialName("instructions")
    val instructions: String,
    @SerialName("open_giveaway_url")
    val openGiveawayUrl: String,
    @Serializable(with = LocalDateSerializer::class)
    @SerialName("publishedDate")
    val publishedDate: LocalDateTime,
    @SerialName("type")
    val type: GiveawayType,
    @SerialName("platforms")
    val platforms: ImmutableList<GiveawayPlatform>,
    @SerialName("end_date")
    val endDate: String?,
    @SerialName("users")
    val users: Int,
    @SerialName("status")
    val status: String,
    @SerialName("gamerpower_url")
    val gamerpowerUrl: String,
    @SerialName("open_giveaway")
    val openGiveaway: String,

    /**
     * Epoch-millisecond expiry stamp written when the entity is persisted by the repository.
     *
     * Stamped via the injected `Clock` plus the resource's TTL on refresh (ITAD caching strategy,
     * Phase 1 — TTL-gate). Persisted with SQL `DEFAULT 0` (already-expired), which backs the
     * v10→v11 `ADD COLUMN` migration: older cached rows are treated as stale and refetch once.
     */
    @SerialName("expires")
    @ColumnInfo(defaultValue = "0")
    val expires: Long = 0L,
)

enum class GiveawayType {
    @SerialName("Game")
    GAME,

    @SerialName("DLC")
    DLC,

    @SerialName("Early_Access")
    BETA,

    @SerialName("Other")
    OTHER,
}

enum class GiveawayPlatform(val platformValue: String) {
    @SerialName("PC")
    PC("PC"),

    @SerialName("PS4")
    PS4("Playstation 4"),

    @SerialName("PS5")
    PS5("Playstation 5"),

    @SerialName("XBOX_360")
    XBOX_360("Xbox 360"),

    @SerialName("XBOX_ONE")
    XBOX_ONE("Xbox One"),

    @SerialName("XBOX_SERIES_X")
    XBOX_X("Xbox Series X|S"),

    @SerialName("NINTENDO_SWITCH")
    NINTENDO_SWITCH("Nintendo Switch"),

    @SerialName("ANDROID")
    ANDROID("Android"),

    @SerialName("IOS")
    IOS("iOS"),

    @SerialName("Steam")
    STEAM("Steam"),

    @SerialName("Itch.io")
    ITCH_IO("Itch.io"),

    @SerialName("Epic")
    EPIC("Epic Games Store"),

    @SerialName("GOG")
    GOG("GOG"),

    @SerialName("DRM_Free")
    DRM_FREE("DRM-Free"),

    @SerialName("OTHER")
    OTHER("Other"),
}

enum class GiveawaySortBy {
    @SerialName("date")
    DATE,

    @SerialName("value")
    VALUE,

    @SerialName("popularity")
    POPULARITY
}


@Immutable
@Serializable
data class GiveawayPlatformSelection(
    val platform: GiveawayPlatform,
    val selected: Boolean,
)

@Immutable
@Serializable
data class GiveawayTypeSelection(
    val type: GiveawayType,
    val selected: Boolean,
)


@OptIn(ExperimentalSerializationApi::class)
@Immutable
@Serializable
data class GiveawaySearchParameters(
    val platforms: ImmutableList<GiveawayPlatformSelection> =
        GiveawayPlatform.entries.map { GiveawayPlatformSelection(it, false) }.toImmutableList(),
    val types: ImmutableList<GiveawayTypeSelection> =
        GiveawayType.entries.map { GiveawayTypeSelection(it, false) }.toImmutableList(),
    val sortBy: GiveawaySortBy = GiveawaySortBy.DATE,
) {
    /**
     * Encodes properties from the this [GiveawaySearchParameters] to a map.
     * `null` values are omitted from the output.
     *
     * @see GiveawaySearchParameters.from
     */
    fun asMap() = Properties.encodeToMap(serializer(), this)

    companion object {
        /**
         * Decodes properties from the given [map] to a value of type [GiveawaySearchParameters].
         * [GiveawaySearchParameters] may contain properties of nullable types; they will be filled by non-null values from the [map], if present.
         */
        fun from(map: Map<String, Any?>): GiveawaySearchParameters = Properties.decodeFromMap(
            serializer(),
            // Removes any map Key/Value pairs where the Value is NULL.
            map.mapNotNull { (key, value) -> value?.let { key to it } }
                .toMap())
    }
}
