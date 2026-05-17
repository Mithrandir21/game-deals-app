package pm.bam.gamedeals.testing.fixtures

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDateTime
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType

val MIN_DATETIME = LocalDateTime(1970, 1, 1, 0, 0)
val MAX_DATETIME = LocalDateTime(9999, 12, 31, 23, 59, 59)

fun giveaway(
    id: Int = 1,
    title: String = "Test Giveaway",
    worthDenominated: String? = "$0",
    worth: Double? = 0.0,
    thumbnail: String = "thumb.png",
    image: String = "image.png",
    description: String = "desc",
    instructions: String = "instructions",
    openGiveawayUrl: String = "https://example.com/open",
    publishedDate: LocalDateTime = MIN_DATETIME,
    type: GiveawayType = GiveawayType.GAME,
    platforms: ImmutableList<GiveawayPlatform> = persistentListOf(GiveawayPlatform.PC),
    endDate: String? = null,
    users: Int = 0,
    status: String = "Active",
    gamerpowerUrl: String = "https://example.com",
    openGiveaway: String = "https://example.com/giveaway",
) = Giveaway(
    id, title, worthDenominated, worth, thumbnail, image, description, instructions,
    openGiveawayUrl, publishedDate, type, platforms, endDate, users, status, gamerpowerUrl, openGiveaway,
)
