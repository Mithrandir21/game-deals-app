package pm.bam.gamedeals.remote.gamerpower.mappers

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveaway
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveawayType

private const val WORTH_NOT_AVAILABLE = "N/A"

internal fun RemoteGiveaway.toGiveaway(
    datetimeParsing: DatetimeParsing
): Giveaway =
    Giveaway(
        id = id,
        title = title,
        worthDenominated = worth.takeUnless { it == WORTH_NOT_AVAILABLE },
        worth = worth.takeUnless { it == WORTH_NOT_AVAILABLE }?.replace("$", "")?.toDoubleOrNull(),
        thumbnail = thumbnail,
        image = image,
        description = description,
        instructions = instructions,
        openGiveawayUrl = openGiveawayUrl,
        publishedDate = datetimeParsing.parseDatetime(publishedDate),
        type = type.toGiveawayType(),
        platforms = platforms.toGiveawayPlatform(),
        endDate = endDate,
        users = users,
        status = status,
        gamerpowerUrl = gamerpowerUrl,
        openGiveaway = openGiveaway,
    )

internal fun RemoteGiveawayType.toGiveawayType(): GiveawayType =
    when (this) {
        RemoteGiveawayType.GAME -> GiveawayType.GAME
        RemoteGiveawayType.DLC -> GiveawayType.DLC
        RemoteGiveawayType.BETA -> GiveawayType.BETA
        RemoteGiveawayType.OTHER -> GiveawayType.OTHER
    }

private fun String.toGiveawayPlatform(): ImmutableList<GiveawayPlatform> =
    this.split(", ")
        .map {
            when (it) {
                GiveawayPlatform.PC.platformValue -> GiveawayPlatform.PC
                GiveawayPlatform.PS4.platformValue -> GiveawayPlatform.PS4
                GiveawayPlatform.PS5.platformValue -> GiveawayPlatform.PS5
                GiveawayPlatform.XBOX_360.platformValue -> GiveawayPlatform.XBOX_360
                GiveawayPlatform.XBOX_ONE.platformValue -> GiveawayPlatform.XBOX_ONE
                GiveawayPlatform.XBOX_X.platformValue -> GiveawayPlatform.XBOX_X
                GiveawayPlatform.NINTENDO_SWITCH.platformValue -> GiveawayPlatform.NINTENDO_SWITCH
                GiveawayPlatform.ANDROID.platformValue -> GiveawayPlatform.ANDROID
                GiveawayPlatform.IOS.platformValue -> GiveawayPlatform.IOS
                GiveawayPlatform.STEAM.platformValue -> GiveawayPlatform.STEAM
                GiveawayPlatform.ITCH_IO.platformValue -> GiveawayPlatform.ITCH_IO
                GiveawayPlatform.EPIC.platformValue -> GiveawayPlatform.EPIC
                GiveawayPlatform.GOG.platformValue -> GiveawayPlatform.GOG
                GiveawayPlatform.DRM_FREE.platformValue -> GiveawayPlatform.DRM_FREE
                else -> GiveawayPlatform.OTHER
            }
        }
        .toImmutableList()
