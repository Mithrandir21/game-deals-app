package pm.bam.gamedeals.remote.cheapshark.mappers

import kotlinx.collections.immutable.toImmutableList
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGame
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGameDetails
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation
import kotlin.math.roundToInt

internal fun RemoteGame.toGame(
    currencyTransformation: CurrencyTransformation
): Game =
    Game(
        gameID = gameID,
        steamAppID = steamAppID,
        cheapestValue = cheapest,
        cheapestDenominated = currencyTransformation.valueToDenominated(cheapest),
        cheapestDealID = cheapestDealID,
        title = external,
        internalName = internalName,
        thumb = thumb,
    )

internal fun RemoteGameDetails.RemoteGameDeal.toGameDeal(
    currencyTransformation: CurrencyTransformation
): GameDetails.GameDeal =
    GameDetails.GameDeal(
        storeID = storeID,
        dealID = dealID,
        priceValue = price,
        priceDenominated = currencyTransformation.valueToDenominated(price),
        retailPriceValue = retailPrice,
        retailPriceDenominated = currencyTransformation.valueToDenominated(retailPrice),
        savings = savings.roundToInt(),
    )

internal fun RemoteGameDetails.RemoteGameCheapestPriceEver.toGameCheapestPriceEver(
    currencyTransformation: CurrencyTransformation,
    datetimeFormatter: DateTimeFormatter
): GameDetails.GameCheapestPriceEver =
    GameDetails.GameCheapestPriceEver(
        priceValue = price,
        priceDenominated = currencyTransformation.valueToDenominated(price),
        date = datetimeFormatter.formatToISODate(date)
    )

internal fun RemoteGameDetails.RemoteGameInfo.toGameInfo(): GameDetails.GameInfo =
    GameDetails.GameInfo(
        title = title,
        steamAppID = steamAppID,
        thumb = thumb,
    )

internal fun RemoteGameDetails.toGameDetails(
    currencyTransformation: CurrencyTransformation,
    datetimeFormatter: DateTimeFormatter
): GameDetails =
    GameDetails(
        info = info.toGameInfo(),
        cheapestPriceEver = cheapestPriceEver.toGameCheapestPriceEver(currencyTransformation, datetimeFormatter),
        deals = deals.map { it.toGameDeal(currencyTransformation) }.toImmutableList(),
    )
