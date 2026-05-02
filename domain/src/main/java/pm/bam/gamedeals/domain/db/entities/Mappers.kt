package pm.bam.gamedeals.domain.db.entities

import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store

internal fun Deal.toEntity(expiresAt: Long = 0L): DealEntity = DealEntity(
    dealID = dealID,
    internalName = internalName,
    title = title,
    metacriticLink = metacriticLink,
    storeID = storeID,
    gameID = gameID,
    salePriceValue = salePriceValue,
    salePriceDenominated = salePriceDenominated,
    normalPriceValue = normalPriceValue,
    normalPriceDenominated = normalPriceDenominated,
    isOnSale = isOnSale,
    savings = savings,
    metacriticScore = metacriticScore,
    steamRatingText = steamRatingText,
    steamRatingPercent = steamRatingPercent,
    steamRatingCount = steamRatingCount,
    steamAppID = steamAppID,
    releaseDate = releaseDate,
    lastChange = lastChange,
    dealRating = dealRating,
    thumb = thumb,
    expires = expiresAt,
)

internal fun Store.toEntity(expiresAt: Long = 0L): StoreEntity = StoreEntity(
    storeID = storeID,
    storeName = storeName,
    isActive = isActive,
    images = images,
    expires = expiresAt,
)

internal fun Game.toEntity(): GameEntity = GameEntity(
    gameID = gameID,
    steamAppID = steamAppID,
    cheapestValue = cheapestValue,
    cheapestDenominated = cheapestDenominated,
    cheapestDealID = cheapestDealID,
    title = title,
    internalName = internalName,
    thumb = thumb,
)

internal fun Release.toEntity(): ReleaseEntity = ReleaseEntity(
    title = title,
    date = date,
    image = image,
)

internal fun Giveaway.toEntity(): GiveawayEntity = GiveawayEntity(
    id = id,
    title = title,
    worthDenominated = worthDenominated,
    worth = worth,
    thumbnail = thumbnail,
    image = image,
    description = description,
    instructions = instructions,
    openGiveawayUrl = openGiveawayUrl,
    publishedDate = publishedDate,
    type = type,
    platforms = platforms,
    endDate = endDate,
    users = users,
    status = status,
    gamerpowerUrl = gamerpowerUrl,
    openGiveaway = openGiveaway,
)
