package pm.bam.gamedeals.testing.fixtures

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails

fun game(
    gameID: String = "1",
    steamAppID: Int? = null,
    cheapestValue: Double = 0.0,
    cheapestDenominated: String = "$0",
    cheapestDealID: String = "deal-1",
    title: String = "Test Game",
    internalName: String = "TEST_GAME",
    thumb: String = "thumb",
) = Game(gameID, steamAppID, cheapestValue, cheapestDenominated, cheapestDealID, title, internalName, thumb)

fun gameDetails(
    info: GameDetails.GameInfo = GameDetails.GameInfo(title = "Test Game", steamAppID = null, thumb = "thumb"),
    cheapestPriceEver: GameDetails.GameCheapestPriceEver =
        GameDetails.GameCheapestPriceEver(priceValue = 0.0, priceDenominated = "$0", date = "2026-01-01"),
    deals: ImmutableList<GameDetails.GameDeal> = persistentListOf(),
) = GameDetails(info, cheapestPriceEver, deals)

fun gameDeal(
    storeID: Int = 1,
    dealID: String = "deal-1",
    priceValue: Double = 9.99,
    priceDenominated: String = "$9.99",
    retailPriceValue: Double = 19.99,
    retailPriceDenominated: String = "$19.99",
    savings: Int = 50,
    url: String = "",
) = GameDetails.GameDeal(
    storeID = storeID,
    dealID = dealID,
    priceValue = priceValue,
    priceDenominated = priceDenominated,
    retailPriceValue = retailPriceValue,
    retailPriceDenominated = retailPriceDenominated,
    savings = savings,
    url = url,
)

fun gameInfo(
    storeID: Int = 1,
    gameID: String = "100",
    name: String = "Test Game",
    salePriceValue: Double = 9.99,
    salePriceDenominated: String = "$9.99",
    retailPriceValue: Double = 19.99,
    retailPriceDenominated: String = "$19.99",
    steamRatingCount: String = "100",
    publisher: String = "ACME",
    thumb: String = "thumb",
) = DealDetails.GameInfo(
    storeID = storeID,
    gameID = gameID,
    name = name,
    salePriceValue = salePriceValue,
    salePriceDenominated = salePriceDenominated,
    retailPriceValue = retailPriceValue,
    retailPriceDenominated = retailPriceDenominated,
    steamRatingCount = steamRatingCount,
    publisher = publisher,
    thumb = thumb,
)

fun cheaperStore(
    dealID: String = "cheaper-deal",
    storeID: Int = 1,
    salePriceValue: Double = 4.99,
    salePriceDenominated: String = "$4.99",
    retailPriceValue: Double = 19.99,
    retailPriceDenominated: String = "$19.99",
    url: String = "",
) = DealDetails.CheaperStore(
    dealID = dealID,
    storeID = storeID,
    salePriceValue = salePriceValue,
    salePriceDenominated = salePriceDenominated,
    retailPriceValue = retailPriceValue,
    retailPriceDenominated = retailPriceDenominated,
    url = url,
)

fun cheapestPrice(
    priceValue: Double = 4.99,
    priceDenominated: String = "$4.99",
    date: String = "2026-01-01",
) = DealDetails.CheapestPrice(priceValue, priceDenominated, date)

fun dealDetails(
    gameInfo: DealDetails.GameInfo = gameInfo(),
    cheaperStores: ImmutableList<DealDetails.CheaperStore> = persistentListOf(),
    cheapestPrice: DealDetails.CheapestPrice? = cheapestPrice(),
) = DealDetails(gameInfo, cheaperStores, cheapestPrice)
