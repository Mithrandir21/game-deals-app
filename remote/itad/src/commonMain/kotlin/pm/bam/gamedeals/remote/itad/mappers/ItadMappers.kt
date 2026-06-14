package pm.bam.gamedeals.remote.itad.mappers

import kotlinx.collections.immutable.toImmutableList
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.remote.itad.models.ItadDeal
import pm.bam.gamedeals.remote.itad.models.ItadGamePrices
import pm.bam.gamedeals.remote.itad.models.ItadGameSearchResult
import pm.bam.gamedeals.remote.itad.models.ItadMoney
import pm.bam.gamedeals.remote.itad.models.ItadPriceHistoryEntry
import pm.bam.gamedeals.remote.itad.models.ItadShop
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadGameInfo
import pm.bam.gamedeals.remote.itad.models.RemoteItadGamePrices
import pm.bam.gamedeals.remote.itad.models.RemoteItadHistoryEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadPrice
import pm.bam.gamedeals.remote.itad.models.RemoteItadSearchGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadShop
import pm.bam.gamedeals.remote.itad.models.RemoteItadShopRef
import pm.bam.gamedeals.remote.itad.models.bestArt

internal fun RemoteItadPrice.toItadMoney(): ItadMoney =
    ItadMoney(amount = amount, currency = currency)

internal fun RemoteItadShopRef.toItadShop(): ItadShop =
    ItadShop(id = id, name = name)

/**
 * Bridge an ITAD shop into the app's domain [Store]. ITAD shop ids are already integers (so they
 * map straight onto `storeID`); ITAD's shops endpoint doesn't carry the banner/logo/icon triple
 * CheapShark provided, so [Store.StoreImages] is left blank for now (logos arrive in a later phase).
 */
internal fun RemoteItadShop.toStore(): Store =
    Store(
        storeID = id,
        storeName = title,
        isActive = true,
        images = Store.StoreImages(banner = "", logo = "", icon = ""),
        iconUrl = "",
    )

internal fun RemoteItadShop.toItadShop(): ItadShop =
    ItadShop(id = id, name = title)

internal fun RemoteItadDealEntry.toItadDeal(gameId: String, gameTitle: String, boxart: String? = null): ItadDeal =
    ItadDeal(
        gameId = gameId,
        gameTitle = gameTitle,
        shop = shop.toItadShop(),
        price = price.toItadMoney(),
        regular = regular?.toItadMoney(),
        cutPercent = cut,
        url = url,
        storeLow = storeLow?.toItadMoney(),
        boxart = boxart,
        isLowestEver = flag.isHistoryLowFlag(),
        isNewHistoricalLow = flag.isNewHistoryLowFlag(),
        isStoreLow = flag.isStoreLowFlag(),
        hasVoucher = voucher.isVoucherPresent(),
    )

/**
 * ITAD's deal `flag` marks a price at its all-time low: `"N"` (just hit a new historical low) or
 * `"H"` (currently at the historical low). Any other value (incl. `"S"` and `null`) is not (#255).
 */
internal fun String?.isHistoryLowFlag(): Boolean = this == "N" || this == "H"

/** ITAD `flag == "N"`: the price *just* hit a new all-time low (drives the new-low "N" badge). */
internal fun String?.isNewHistoryLowFlag(): Boolean = this == "N"

/** ITAD `flag == "S"`: the lowest price this specific store has ever offered (drives the store-low "S" badge). */
internal fun String?.isStoreLowFlag(): Boolean = this == "S"

/** A deal carries a voucher when ITAD returns a non-blank `voucher` code (drives the scissors badge). */
internal fun String?.isVoucherPresent(): Boolean = !this.isNullOrBlank()

/** One game's best current deal from `/deals/v2` (singular `deal` + game-level `assets`). */
internal fun RemoteItadDealsGame.toItadDeal(): ItadDeal =
    deal.toItadDeal(gameId = id, gameTitle = title, boxart = assets.bestArt())

internal fun RemoteItadSearchGame.toItadGameSearchResult(steamAppId: Int? = null): ItadGameSearchResult =
    ItadGameSearchResult(
        id = id,
        title = title,
        slug = slug,
        steamAppId = steamAppId,
        boxart = assets.bestArt(),
    )

/** `/games/info/v2` carries the same header fields as a search game, so reduce it to the lightweight identity used for deal/game headers. */
internal fun RemoteItadGameInfo.toItadGameSearchResult(): ItadGameSearchResult =
    ItadGameSearchResult(
        id = id,
        title = title,
        slug = slug,
        steamAppId = appid,
        boxart = assets.bestArt(),
    )

internal fun RemoteItadGamePrices.toItadGamePrices(): ItadGamePrices =
    ItadGamePrices(
        gameId = id,
        historyLowAll = historyLow?.all?.toItadMoney(),
        deals = deals.map { it.toItadDeal(gameId = id, gameTitle = "") }.toImmutableList(),
    )

/** Returns null for history rows without a deal (ITAD emits gaps when a game left every shop's sale). */
internal fun RemoteItadHistoryEntry.toItadPriceHistoryEntry(): ItadPriceHistoryEntry? =
    deal?.let {
        ItadPriceHistoryEntry(
            timestamp = timestamp,
            shop = shop.toItadShop(),
            price = it.price.toItadMoney(),
            regular = it.regular?.toItadMoney(),
            cutPercent = it.cut,
        )
    }
