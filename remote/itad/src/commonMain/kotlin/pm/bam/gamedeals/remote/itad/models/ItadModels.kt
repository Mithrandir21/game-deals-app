package pm.bam.gamedeals.remote.itad.models

import kotlinx.collections.immutable.ImmutableList
import pm.bam.gamedeals.domain.models.GameArtwork

/**
 * Clean, ITAD-shaped domain models that the module's mappers produce from the transport DTOs.
 *
 * Phase 1 (#205) keeps these separate from the app's CheapShark-shaped domain models (`Deal`,
 * `Game`, …) on purpose: ITAD identifies games by UUID and exposes a different shape, and the
 * app's domain models migrate to UUIDs in Phase 2. Until then these prove the fetch + map path
 * and are consumed by the module's tests; Phase 2/3 bridge them into the (migrated) domain.
 */

data class ItadMoney(
    val amount: Double,
    val currency: String,
)

data class ItadShop(
    val id: Int,
    val name: String,
)

data class ItadGameSearchResult(
    val id: String, // ITAD game UUID
    val title: String,
    val slug: String? = null,
    /** Populated only when the game was resolved via a Steam appid lookup (the IGDB bridge). */
    val steamAppId: Int? = null,
    val artwork: GameArtwork = GameArtwork(),
)

data class ItadDeal(
    val gameId: String, // ITAD game UUID
    val gameTitle: String,
    val shop: ItadShop,
    val price: ItadMoney,
    val regular: ItadMoney?,
    val cutPercent: Int,
    val url: String,
    val storeLow: ItadMoney? = null,
    /** Game art from `/deals/v2` `assets`; empty on the `/games/prices/v3` deal entries. */
    val artwork: GameArtwork = GameArtwork(),
    /** `true` when ITAD's deal `flag` marks this price as at/at-a-new historical low (#255). */
    val isLowestEver: Boolean = false,
    /** `true` when ITAD's deal `flag == "N"` — the price *just* hit a new all-time low (new-low badge). */
    val isNewHistoricalLow: Boolean = false,
    /** `true` when ITAD's deal `flag == "S"` — the lowest price this specific store has offered (store-low badge). */
    val isStoreLow: Boolean = false,
    /** `true` when the deal carries a voucher/coupon code (drives the "with voucher" scissors badge). */
    val hasVoucher: Boolean = false,
)

data class ItadGamePrices(
    val gameId: String,
    val historyLowAll: ItadMoney?,
    val deals: ImmutableList<ItadDeal>,
)

data class ItadPriceHistoryEntry(
    val timestamp: String, // ISO-8601; parsing/formatting deferred to the consumer
    val shop: ItadShop,
    val price: ItadMoney,
    val regular: ItadMoney?,
    val cutPercent: Int,
)
