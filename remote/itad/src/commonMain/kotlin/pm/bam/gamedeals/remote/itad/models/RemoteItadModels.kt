package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ITAD (IsThereAnyDeal) API v2 transport DTOs. Only the fields the app currently consumes are
 * modelled; the shared `Json` is configured with `ignoreUnknownKeys = true`, so ITAD's many extra
 * fields are tolerated. Money is `{amount, amountInt, currency}`, game ids are UUID strings, and
 * shop ids are integers.
 */

@Serializable
data class RemoteItadPrice(
    @SerialName("amount") val amount: Double,
    @SerialName("amountInt") val amountInt: Int? = null,
    @SerialName("currency") val currency: String,
)

@Serializable
data class RemoteItadShopRef(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
)

// --- /service/shops/v1 ---
@Serializable
data class RemoteItadShop(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
)

// --- /games/search/v1 and /games/lookup/v1 ---
@Serializable
data class RemoteItadGameAssets(
    @SerialName("boxart") val boxart: String? = null,
    @SerialName("banner300") val banner300: String? = null,
    @SerialName("banner600") val banner600: String? = null,
)

@Serializable
data class RemoteItadSearchGame(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String? = null,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String? = null,
    @SerialName("mature") val mature: Boolean? = null,
    @SerialName("assets") val assets: RemoteItadGameAssets? = null,
)

@Serializable
data class RemoteItadLookupResponse(
    @SerialName("found") val found: Boolean,
    @SerialName("game") val game: RemoteItadSearchGame? = null,
)

// --- /deals/v2 and the deal entries reused by /games/prices/v3 ---
@Serializable
data class RemoteItadDealEntry(
    @SerialName("shop") val shop: RemoteItadShopRef,
    @SerialName("price") val price: RemoteItadPrice,
    @SerialName("regular") val regular: RemoteItadPrice? = null,
    @SerialName("cut") val cut: Int = 0,
    @SerialName("url") val url: String,
    @SerialName("storeLow") val storeLow: RemoteItadPrice? = null,
)

@Serializable
data class RemoteItadDealsGame(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String? = null,
    @SerialName("title") val title: String,
    @SerialName("assets") val assets: RemoteItadGameAssets? = null,
    @SerialName("deal") val deal: RemoteItadDealEntry,
)

/**
 * `/deals/v2` envelope. The live API wraps the games list in `{ nextOffset, hasMore, list }` (each
 * list item is a game with a single best `deal`), not the bare array the Phase-1 stub assumed.
 */
@Serializable
data class RemoteItadDealsResponse(
    @SerialName("nextOffset") val nextOffset: Int? = null,
    @SerialName("hasMore") val hasMore: Boolean = false,
    @SerialName("list") val list: List<RemoteItadDealsGame> = emptyList(),
)

// --- /games/prices/v3 ---
@Serializable
data class RemoteItadHistoryLow(
    @SerialName("all") val all: RemoteItadPrice? = null,
    @SerialName("y1") val y1: RemoteItadPrice? = null,
    @SerialName("m3") val m3: RemoteItadPrice? = null,
)

@Serializable
data class RemoteItadGamePrices(
    @SerialName("id") val id: String,
    @SerialName("historyLow") val historyLow: RemoteItadHistoryLow? = null,
    @SerialName("deals") val deals: List<RemoteItadDealEntry> = emptyList(),
)

// --- /games/history/v2 ---
@Serializable
data class RemoteItadHistoryDeal(
    @SerialName("price") val price: RemoteItadPrice,
    @SerialName("regular") val regular: RemoteItadPrice? = null,
    @SerialName("cut") val cut: Int = 0,
)

@Serializable
data class RemoteItadHistoryEntry(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("shop") val shop: RemoteItadShopRef,
    @SerialName("deal") val deal: RemoteItadHistoryDeal? = null,
)
