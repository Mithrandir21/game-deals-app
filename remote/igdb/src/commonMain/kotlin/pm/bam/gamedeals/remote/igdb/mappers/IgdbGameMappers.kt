package pm.bam.gamedeals.remote.igdb.mappers

import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbInvolvedCompany
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbSimilarGame
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbTimeToBeat
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbWebsite

internal fun RemoteIgdbGame.toIgdbGame(): IgdbGame = IgdbGame(
    id = id,
    name = name,
    summary = summary,
    storyline = storyline,
    coverImageId = cover?.imageId,
    screenshotImageIds = screenshots.mapNotNull { it.imageId }.toImmutableList(),
    firstReleaseDate = firstReleaseDate?.let { Instant.fromEpochSeconds(it) },
    rating = rating,
    ratingCount = ratingCount,
    aggregatedRating = aggregatedRating,
    aggregatedRatingCount = aggregatedRatingCount,
    genres = genres.mapNotNull { it.name }.toImmutableList(),
    themes = themes.mapNotNull { it.name }.toImmutableList(),
    involvedCompanies = involvedCompanies.flatMap { it.toRoles() }.toImmutableList(),
    websites = websites.mapNotNull { it.toIgdbWebsiteOrNull() }.toImmutableList(),
    similarGames = similarGames.mapNotNull { it.toIgdbSimilarGameOrNull() }.toImmutableList(),
    dlcs = dlcs.mapNotNull { it.toIgdbSimilarGameOrNull() }.toImmutableList(),
    expansions = expansions.mapNotNull { it.toIgdbSimilarGameOrNull() }.toImmutableList(),
    // timeToBeat is fetched separately (/v4/game_time_to_beats) and merged by the consumer — left null here.
    steamAppId = externalGames
        .firstOrNull { it.externalGameSource == STEAM_EXTERNAL_GAME_SOURCE_ID }
        ?.uid
        ?.toIntOrNull(),
)

/**
 * Maps a lean IGDB game (from the new-releases query) into a domain [Release] (epic #205, Phase 2c).
 * Returns null when the cover image id is missing so the Home strip never shows a blank tile —
 * the query already filters `cover != null`, but the field is modelled nullable. `Release.date` is
 * the IGDB `first_release_date` (Unix seconds); it is stored but not displayed.
 */
internal fun RemoteIgdbGame.toReleaseOrNull(): Release? {
    val imageId = cover?.imageId ?: return null
    return Release(
        title = name,
        date = firstReleaseDate?.toInt() ?: 0,
        image = igdbImageUrl(imageId, IgdbImageSize.CoverBig),
    )
}

private const val STEAM_EXTERNAL_GAME_SOURCE_ID = 1L

private fun RemoteIgdbInvolvedCompany.toRoles(): List<IgdbGame.IgdbCompanyRole> {
    val companyName = company?.name ?: return emptyList()
    return buildList {
        if (developer) add(IgdbGame.IgdbCompanyRole(companyName, IgdbGame.IgdbCompanyRole.Role.Developer))
        if (publisher) add(IgdbGame.IgdbCompanyRole(companyName, IgdbGame.IgdbCompanyRole.Role.Publisher))
        if (porting) add(IgdbGame.IgdbCompanyRole(companyName, IgdbGame.IgdbCompanyRole.Role.Porting))
        if (supporting) add(IgdbGame.IgdbCompanyRole(companyName, IgdbGame.IgdbCompanyRole.Role.Supporting))
    }
}

private fun RemoteIgdbWebsite.toIgdbWebsiteOrNull(): IgdbGame.IgdbWebsite? {
    val u = url ?: return null
    return IgdbGame.IgdbWebsite(u, mapWebsiteType(type?.id, type?.type))
}

// Stable int IDs come first (these never rename). The `typeName` string fallback catches
// categories whose IGDB int IDs we haven't enumerated yet (e.g. PlayStation Store, Nintendo) —
// adding a new Category enum value + a string match here is enough to label them correctly
// without first needing to discover the integer ID.
private fun mapWebsiteType(typeId: Long?, typeName: String?): IgdbGame.IgdbWebsite.Category {
    when (typeId) {
        1L -> return IgdbGame.IgdbWebsite.Category.Official
        2L -> return IgdbGame.IgdbWebsite.Category.Wikia
        3L -> return IgdbGame.IgdbWebsite.Category.Wikipedia
        4L -> return IgdbGame.IgdbWebsite.Category.Facebook
        5L -> return IgdbGame.IgdbWebsite.Category.Twitter
        6L -> return IgdbGame.IgdbWebsite.Category.Twitch
        8L -> return IgdbGame.IgdbWebsite.Category.Instagram
        9L -> return IgdbGame.IgdbWebsite.Category.YouTube
        10L -> return IgdbGame.IgdbWebsite.Category.IPhone
        11L -> return IgdbGame.IgdbWebsite.Category.IPad
        12L -> return IgdbGame.IgdbWebsite.Category.Android
        13L -> return IgdbGame.IgdbWebsite.Category.Steam
        14L -> return IgdbGame.IgdbWebsite.Category.Reddit
        15L -> return IgdbGame.IgdbWebsite.Category.Itch
        16L -> return IgdbGame.IgdbWebsite.Category.EpicStore
        17L -> return IgdbGame.IgdbWebsite.Category.GogStore
        18L -> return IgdbGame.IgdbWebsite.Category.Discord
        19L -> return IgdbGame.IgdbWebsite.Category.Bluesky
        22L -> return IgdbGame.IgdbWebsite.Category.Xbox
    }
    return when (typeName?.lowercase()) {
        "playstation", "playstation store" -> IgdbGame.IgdbWebsite.Category.PlayStation
        "nintendo", "nintendo eshop" -> IgdbGame.IgdbWebsite.Category.Nintendo
        else -> IgdbGame.IgdbWebsite.Category.Other
    }
}

private fun RemoteIgdbSimilarGame.toIgdbSimilarGameOrNull(): IgdbGame.IgdbSimilarGame? {
    val n = name ?: return null
    return IgdbGame.IgdbSimilarGame(id = id, name = n, coverImageId = cover?.imageId)
}

internal fun RemoteIgdbTimeToBeat.toIgdbTimeToBeat(): IgdbGame.IgdbTimeToBeat =
    IgdbGame.IgdbTimeToBeat(hastily = hastily, normally = normally, completely = completely, count = count)

/** First row of `/v4/game_time_to_beats`, or null when IGDB has no completion data for the game. */
internal fun List<RemoteIgdbTimeToBeat>.toIgdbTimeToBeatOrNull(): IgdbGame.IgdbTimeToBeat? =
    firstOrNull()?.toIgdbTimeToBeat()
