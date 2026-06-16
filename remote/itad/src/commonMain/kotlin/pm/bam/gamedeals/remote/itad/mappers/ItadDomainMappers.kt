package pm.bam.gamedeals.remote.itad.mappers

import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.remote.itad.models.ItadDeal
import pm.bam.gamedeals.remote.itad.models.ItadGamePrices
import pm.bam.gamedeals.remote.itad.models.ItadGameSearchResult
import pm.bam.gamedeals.remote.itad.models.ItadMoney
import pm.bam.gamedeals.remote.itad.models.ItadPriceHistoryEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadBundle
import pm.bam.gamedeals.remote.itad.models.RemoteItadGameInfo
import pm.bam.gamedeals.remote.itad.models.bestArt

/**
 * ITAD-shaped models → the app's (CheapShark-shaped) domain models (epic #205, Phase 2b).
 *
 * ITAD is game-centric and lacks several fields CheapShark had (per-deal id, Steam rating, Metacritic,
 * deal rating, release date, publisher). Those `Deal`/`DealDetails.GameInfo` fields are nullable and left
 * `null` here. ITAD has no per-deal id, and the same game can be a deal in multiple stores, so the
 * synthesized `Deal.dealID` encodes the shop: `"<gameUUID>:<shopId>"`. [gameIdFromDealId] recovers the UUID.
 */

internal fun dealId(gameId: String, shopId: Int): String = "$gameId:$shopId"

internal fun gameIdFromDealId(dealId: String): String = dealId.substringBeforeLast(':')

/**
 * KMP-safe money → denominated string. Prefixes a symbol for the common prefix-style currencies the
 * regional picker exposes (USD → "$9.99", EUR → "€9.99", GBP → "£9.99", …); other currencies fall back
 * to a trailing code ("9.99 PLN") so we never render a wrong/misplaced symbol (#212, regional pricing).
 */
internal fun ItadMoney.denominated(): String = formatMoney(amount, currency)

internal fun ItadDeal.toDeal(): Deal {
    val normal = regular ?: price
    return Deal(
        dealID = dealId(gameId, shop.id),
        title = gameTitle,
        storeID = shop.id,
        gameID = gameId,
        salePriceValue = price.amount,
        salePriceDenominated = price.denominated(),
        normalPriceValue = normal.amount,
        normalPriceDenominated = normal.denominated(),
        isOnSale = cutPercent > 0,
        savings = cutPercent.toDouble(),
        thumb = boxart.orEmpty(),
        url = url,
        isLowestEver = isLowestEver,
        isNewHistoricalLow = isNewHistoricalLow,
        isStoreLow = isStoreLow,
        hasVoucher = hasVoucher,
        // CheapShark-only fields (internalName, metacritic*, steamRating*, releaseDate, lastChange,
        // dealRating) are nullable and default to null — ITAD does not provide them.
    )
}

/**
 * ITAD price-history log → domain [PriceHistory] (#208). Each ITAD entry is a price-change event; we plot
 * the price at each event over time. Rows with an unparseable timestamp are dropped; points are sorted
 * oldest → newest so the chart's x-axis runs left-to-right in time order.
 */
internal fun List<ItadPriceHistoryEntry>.toPriceHistory(gameId: String): PriceHistory =
    PriceHistory(
        gameID = gameId,
        points = mapNotNull { it.toPricePoint() }
            .sortedBy { it.timestampEpochMs }
            .toImmutableList(),
    )

private fun ItadPriceHistoryEntry.toPricePoint(): PriceHistory.PricePoint? {
    val epochMs = runCatching { Instant.parse(timestamp).toEpochMilliseconds() }.getOrNull() ?: return null
    return PriceHistory.PricePoint(
        timestampEpochMs = epochMs,
        priceValue = price.amount,
        priceDenominated = price.denominated(),
    )
}

/**
 * ITAD `/bundles/v1` bundle → domain [Bundle] (#205 Phase 3c; expanded in the Bundles redesign). The tier
 * structure is preserved one-to-one (each [Bundle.Tier] keeps its denominated + raw price and its games);
 * [Bundle.games] is the deduped union across all tiers, kept for the compact list row's cover-art strip.
 * The headline [Bundle.priceDenominated]/[Bundle.priceValue] is the cheapest tier. Expiry/publish are
 * parsed from ISO-8601 (with offset) via [Instant]; an unparseable value becomes null.
 */
internal fun RemoteItadBundle.toBundle(): Bundle {
    val mappedTiers = tiers.map { tier ->
        val money = tier.price?.toItadMoney()
        Bundle.Tier(
            priceDenominated = money?.denominated(),
            priceValue = tier.price?.amount,
            games = tier.games
                .map { Bundle.BundleGame(id = it.id, title = it.title, boxart = it.assets.bestArt().orEmpty()) }
                .toImmutableList(),
        )
    }.toImmutableList()
    val games = mappedTiers.flatMap { it.games }
        .distinctBy { it.id }
        .toImmutableList()
    val cheapest = tiers.mapNotNull { it.price }.minByOrNull { it.amount }
    return Bundle(
        id = id,
        title = title,
        storeName = page?.name.orEmpty(),
        url = url,
        expiryEpochMs = expiry?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() },
        gameCount = counts?.games ?: games.size,
        priceDenominated = cheapest?.toItadMoney()?.denominated(),
        games = games,
        publishEpochMs = publish?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() },
        isMature = isMature,
        details = details?.takeIf { it.isNotBlank() },
        priceValue = cheapest?.amount,
        tiers = mappedTiers,
    )
}

/**
 * ITAD `/games/prices/v3` prices → domain [BundleGamePrice] (Bundles redesign). The "best" deal is the
 * cheapest current deal (by raw amount); its shop name comes directly off the deal entry (no
 * StoresRepository round-trip). The all-time low is `historyLowAll`. A game with no current deal yields
 * null `best*` fields (the detail row shows "No current deal").
 */
internal fun ItadGamePrices.toBundleGamePrice(): BundleGamePrice {
    val best = deals.minByOrNull { it.price.amount }
    return BundleGamePrice(
        gameId = gameId,
        bestShopName = best?.shop?.name,
        bestPriceValue = best?.price?.amount,
        bestPriceDenominated = best?.price?.denominated(),
        bestCutPercent = best?.cutPercent,
        bestRegularDenominated = best?.regular?.denominated(),
        bestHasVoucher = best?.hasVoucher ?: false,
        bestIsNewHistoricalLow = best?.isNewHistoricalLow ?: false,
        bestIsStoreLow = best?.isStoreLow ?: false,
        historicalLowValue = historyLowAll?.amount,
        historicalLowDenominated = historyLowAll?.denominated(),
        currency = best?.price?.currency ?: historyLowAll?.currency,
    )
}

/**
 * ITAD `/games/info/v2` → domain [GameMeta] (epic #291, Phase 1). Companies are flattened to their names
 * (the UI shows names, not ITAD company ids); reviews/stats/players carry through one-to-one. Empty
 * collections and null objects are preserved so the consumer can hide each section when ITAD omits it.
 */
internal fun RemoteItadGameInfo.toGameMeta(): GameMeta = GameMeta(
    gameId = id,
    steamAppId = appid,
    releaseDate = releaseDate,
    developers = developers.map { it.name }.toImmutableList(),
    publishers = publishers.map { it.name }.toImmutableList(),
    tags = tags.toImmutableList(),
    reviews = reviews.map { GameMeta.Review(source = it.source, score = it.score, count = it.count, url = it.url) }.toImmutableList(),
    players = players?.let { GameMeta.Players(recent = it.recent, day = it.day, week = it.week, peak = it.peak) },
    stats = stats?.let { GameMeta.Stats(rank = it.rank, waitlisted = it.waitlisted, collected = it.collected) },
    earlyAccess = earlyAccess,
    achievements = achievements,
    tradingCards = tradingCards,
)

internal fun ItadGameSearchResult.toGame(): Game = Game(
    gameID = id,
    steamAppID = steamAppId,
    cheapestValue = 0.0,
    cheapestDenominated = "",
    cheapestDealID = "",
    title = title,
    internalName = "",
    thumb = boxart.orEmpty(),
)

internal fun ItadGamePrices.toGameDetails(title: String, boxart: String?): GameDetails = GameDetails(
    info = GameDetails.GameInfo(title = title, steamAppID = null, thumb = boxart.orEmpty()),
    cheapestPriceEver = GameDetails.GameCheapestPriceEver(
        priceValue = historyLowAll?.amount ?: 0.0,
        priceDenominated = historyLowAll?.denominated().orEmpty(),
        date = "",
    ),
    deals = deals.map { it.toGameDeal() }.toImmutableList(),
)

/**
 * Build a [DealDetails] for the deal at [focusShopId] (parsed from the synthesized dealID). The focused
 * shop becomes the headline `gameInfo`; the remaining shops are the cheaper-store alternatives.
 */
internal fun ItadGamePrices.toDealDetails(title: String, boxart: String?, focusShopId: Int): DealDetails {
    val focus = deals.firstOrNull { it.shop.id == focusShopId } ?: deals.firstOrNull()
    val focusNormal = focus?.let { it.regular ?: it.price }
    val others = deals.filter { it.shop.id != focus?.shop?.id }
    return DealDetails(
        gameInfo = DealDetails.GameInfo(
            storeID = focus?.shop?.id ?: focusShopId,
            gameID = gameId,
            name = title,
            salePriceValue = focus?.price?.amount ?: 0.0,
            salePriceDenominated = focus?.price?.denominated().orEmpty(),
            retailPriceValue = focusNormal?.amount ?: 0.0,
            retailPriceDenominated = focusNormal?.denominated().orEmpty(),
            thumb = boxart.orEmpty(),
        ),
        cheaperStores = others.map { it.toCheaperStore() }.toImmutableList(),
        cheapestPrice = historyLowAll?.let {
            DealDetails.CheapestPrice(priceValue = it.amount, priceDenominated = it.denominated(), date = "")
        },
    )
}

private fun ItadDeal.toGameDeal(): GameDetails.GameDeal {
    val retail = regular ?: price
    return GameDetails.GameDeal(
        storeID = shop.id,
        dealID = dealId(gameId, shop.id),
        priceValue = price.amount,
        priceDenominated = price.denominated(),
        retailPriceValue = retail.amount,
        retailPriceDenominated = retail.denominated(),
        savings = cutPercent,
        url = url,
    )
}

private fun ItadDeal.toCheaperStore(): DealDetails.CheaperStore {
    val retail = regular ?: price
    return DealDetails.CheaperStore(
        dealID = dealId(gameId, shop.id),
        storeID = shop.id,
        salePriceValue = price.amount,
        salePriceDenominated = price.denominated(),
        retailPriceValue = retail.amount,
        retailPriceDenominated = retail.denominated(),
        url = url,
    )
}
