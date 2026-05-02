package pm.bam.gamedeals.remote.cheapshark.mappers

import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.DealsSortBy
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsSortBy
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGame
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGameDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteRelease
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore
import kotlin.math.roundToInt

private const val IMAGE_BASE = "https://www.cheapshark.com"

// ---- Trivial mappers (no helpers needed) ----

internal fun RemoteRelease.toDomain(): Release =
    Release(
        title = title,
        date = date,
        image = image,
    )

internal fun RemoteStore.toDomain(): Store =
    Store(
        storeID = storeID,
        storeName = storeName,
        isActive = isActive.toBooleanStrict(),
        images = images.toDomain(),
    )

internal fun RemoteStore.RemoteStoreImages.toDomain(): Store.StoreImages =
    Store.StoreImages(
        banner = IMAGE_BASE + banner,
        logo = IMAGE_BASE + logo,
        icon = IMAGE_BASE + icon,
    )

@OptIn(ExperimentalSerializationApi::class)
internal fun SearchParameters.toDomain(): RemoteDealsQuery = RemoteDealsQuery(
    storeID = storeID,
    pageNumber = pageNumber,
    pageSize = pageSize,
    sortBy = sortBy?.toDomain(),
    desc = desc,
    lowerPrice = lowerPrice,
    upperPrice = upperPrice,
    metacritic = metacritic,
    steamRating = steamMinRating,
    maxAge = maxAge,
    steamAppID = steamAppID,
    title = title,
    exact = exact?.toInt(),
    aaa = aaa?.toInt(),
    steamworks = steamworks?.toInt(),
    onSale = onSale?.toInt(),
)

internal fun DealsSortBy.toDomain(): RemoteDealsSortBy = when (this) {
    DealsSortBy.DEALRATING -> RemoteDealsSortBy.DEALRATING
    DealsSortBy.TITLE -> RemoteDealsSortBy.TITLE
    DealsSortBy.SAVINGS -> RemoteDealsSortBy.SAVINGS
    DealsSortBy.PRICE -> RemoteDealsSortBy.PRICE
    DealsSortBy.METACRITIC -> RemoteDealsSortBy.METACRITIC
    DealsSortBy.REVIEWS -> RemoteDealsSortBy.REVIEWS
    DealsSortBy.RELEASE -> RemoteDealsSortBy.RELEASE
    DealsSortBy.STORE -> RemoteDealsSortBy.STORE
    DealsSortBy.RECENT -> RemoteDealsSortBy.RECENT
}

internal fun RemoteGameDetails.RemoteGameInfo.toDomain(): GameDetails.GameInfo =
    GameDetails.GameInfo(
        title = title,
        steamAppID = steamAppID,
        thumb = thumb,
    )

// ---- Helper-dependent mappers ----

internal fun RemoteDeal.toDomain(ctx: CheapsharkMapperContext): Deal =
    Deal(
        dealID = dealID,
        internalName = internalName,
        title = title,
        metacriticLink = metacriticLink,
        storeID = storeID,
        gameID = gameID,
        salePriceValue = salePrice,
        salePriceDenominated = ctx.currency.valueToDenominated(salePrice),
        normalPriceValue = normalPrice,
        normalPriceDenominated = ctx.currency.valueToDenominated(normalPrice),
        isOnSale = isOnSale.toBooleanStrict(),
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
    )

internal fun RemoteDealDetails.toDomain(ctx: CheapsharkMapperContext): DealDetails =
    DealDetails(
        gameInfo = gameInfo.toDomain(ctx),
        cheaperStores = cheaperStores.map { it.toDomain(ctx) },
        cheapestPrice = cheapestPrice.toDomain(ctx),
    )

internal fun RemoteDealDetails.RemoteGameInfo.toDomain(ctx: CheapsharkMapperContext): DealDetails.GameInfo =
    DealDetails.GameInfo(
        storeID = storeID,
        gameID = gameID,
        name = name,
        steamAppID = steamAppID,
        salePriceValue = salePrice,
        salePriceDenominated = ctx.currency.valueToDenominated(salePrice),
        retailPriceValue = retailPrice,
        retailPriceDenominated = ctx.currency.valueToDenominated(retailPrice),
        steamRatingText = steamRatingText,
        steamRatingPercent = steamRatingPercent.takeIf { it > 0 },
        steamRatingCount = steamRatingCount,
        metacriticScore = metacriticScore.takeIf { it > 0 },
        metacriticLink = metacriticLink,
        releaseDate = ctx.dates.formatToISODateNullable(releaseDate),
        publisher = publisher,
        steamworks = steamworks?.toBooleanStrict(),
        thumb = thumb,
    )

internal fun RemoteDealDetails.RemoteCheaperStore.toDomain(ctx: CheapsharkMapperContext): DealDetails.CheaperStore =
    DealDetails.CheaperStore(
        dealID = dealID,
        storeID = storeID,
        salePriceValue = salePrice,
        salePriceDenominated = ctx.currency.valueToDenominated(salePrice),
        retailPriceValue = retailPrice,
        retailPriceDenominated = ctx.currency.valueToDenominated(retailPrice),
    )

internal fun RemoteDealDetails.RemoteCheapestPrice.toDomain(ctx: CheapsharkMapperContext): DealDetails.CheapestPrice? =
    price?.let {
        DealDetails.CheapestPrice(
            priceValue = it,
            priceDenominated = ctx.currency.valueToDenominated(it),
            date = ctx.dates.formatToISODate(date),
        )
    }

internal fun RemoteGame.toDomain(ctx: CheapsharkMapperContext): Game =
    Game(
        gameID = gameID,
        steamAppID = steamAppID,
        cheapestValue = cheapest,
        cheapestDenominated = ctx.currency.valueToDenominated(cheapest),
        cheapestDealID = cheapestDealID,
        title = external,
        internalName = internalName,
        thumb = thumb,
    )

internal fun RemoteGameDetails.toDomain(ctx: CheapsharkMapperContext): GameDetails =
    GameDetails(
        info = info.toDomain(),
        cheapestPriceEver = cheapestPriceEver.toDomain(ctx),
        deals = deals.map { it.toDomain(ctx) }.toImmutableList(),
    )

internal fun RemoteGameDetails.RemoteGameCheapestPriceEver.toDomain(ctx: CheapsharkMapperContext): GameDetails.GameCheapestPriceEver =
    GameDetails.GameCheapestPriceEver(
        priceValue = price,
        priceDenominated = ctx.currency.valueToDenominated(price),
        date = ctx.dates.formatToISODate(date),
    )

internal fun RemoteGameDetails.RemoteGameDeal.toDomain(ctx: CheapsharkMapperContext): GameDetails.GameDeal =
    GameDetails.GameDeal(
        storeID = storeID,
        dealID = dealID,
        priceValue = price,
        priceDenominated = ctx.currency.valueToDenominated(price),
        retailPriceValue = retailPrice,
        retailPriceDenominated = ctx.currency.valueToDenominated(retailPrice),
        savings = savings.roundToInt(),
    )

// ---- File-private helpers ----

private fun Int.toBooleanStrict(): Boolean = when (this) {
    0 -> false
    1 -> true
    else -> throw Exception("Unknown value for int to boolean conversion: $this")
}

private fun Boolean.toInt(): Int = if (this) 1 else 0
