package pm.bam.gamedeals.remote.cheapshark.mappers

import kotlinx.collections.immutable.toImmutableList
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation

internal fun RemoteDeal.toDeal(currencyTransformation: CurrencyTransformation): Deal =
    Deal(
        dealID = dealID,
        internalName = internalName,
        title = title,
        metacriticLink = metacriticLink,
        storeID = storeID,
        gameID = gameID,
        salePriceValue = salePrice,
        salePriceDenominated = currencyTransformation.valueToDenominated(salePrice),
        normalPriceValue = normalPrice,
        normalPriceDenominated = currencyTransformation.valueToDenominated(normalPrice),
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

internal fun RemoteDealDetails.RemoteCheapestPrice.toCheapestPrice(
    currencyTransformation: CurrencyTransformation,
    datetimeFormatter: DateTimeFormatter
): DealDetails.CheapestPrice? =
    price?.let {
        DealDetails.CheapestPrice(
            priceValue = it,
            priceDenominated = currencyTransformation.valueToDenominated(it),
            date = datetimeFormatter.formatToISODate(date)
        )
    }

internal fun RemoteDealDetails.RemoteCheaperStore.toCheaperStore(
    currencyTransformation: CurrencyTransformation
): DealDetails.CheaperStore =
    DealDetails.CheaperStore(
        dealID = dealID,
        storeID = storeID,
        salePriceValue = salePrice,
        salePriceDenominated = currencyTransformation.valueToDenominated(salePrice),
        retailPriceValue = retailPrice,
        retailPriceDenominated = currencyTransformation.valueToDenominated(retailPrice),
    )

internal fun RemoteDealDetails.RemoteGameInfo.toGameInfo(
    currencyTransformation: CurrencyTransformation,
    datetimeFormatter: DateTimeFormatter
): DealDetails.GameInfo =
    DealDetails.GameInfo(
        storeID = storeID,
        gameID = gameID,
        name = name,
        steamAppID = steamAppID,
        salePriceValue = salePrice,
        salePriceDenominated = currencyTransformation.valueToDenominated(salePrice),
        retailPriceValue = retailPrice,
        retailPriceDenominated = currencyTransformation.valueToDenominated(retailPrice),
        steamRatingText = steamRatingText,
        steamRatingPercent = steamRatingPercent.takeIf { it > 0 },
        steamRatingCount = steamRatingCount,
        metacriticScore = metacriticScore.takeIf { it > 0 },
        metacriticLink = metacriticLink,
        releaseDate = datetimeFormatter.formatToISODateNullable(releaseDate),
        publisher = publisher,
        steamworks = steamworks?.toBooleanStrict(),
        thumb = thumb
    )

internal fun RemoteDealDetails.toDealDetails(
    currencyTransformation: CurrencyTransformation,
    datetimeFormatter: DateTimeFormatter
): DealDetails =
    DealDetails(
        gameInfo = gameInfo.toGameInfo(currencyTransformation, datetimeFormatter),
        cheaperStores = cheaperStores.map { it.toCheaperStore(currencyTransformation) }.toImmutableList(),
        cheapestPrice = cheapestPrice.toCheapestPrice(currencyTransformation, datetimeFormatter)
    )
