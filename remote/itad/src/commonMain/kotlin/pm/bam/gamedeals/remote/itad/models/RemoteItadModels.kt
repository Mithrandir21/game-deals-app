package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.domain.models.GameArtwork

/**
 * ITAD (IsThereAnyDeal) API v2 transport DTOs. Only the fields the app currently consumes are
 * modelled; the shared `Json` is configured with `ignoreUnknownKeys = true`, so ITAD's many extra
 * fields are tolerated. Money is `{amount, amountInt, currency}`, game ids are UUID strings, and
 * shop ids are integers.
 */

@Serializable
data class RemoteItadPrice(
    // Defaulted so an omitted/partial price block degrades to a zero price rather than failing the row.
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("amountInt") val amountInt: Int? = null,
    @SerialName("currency") val currency: String = "",
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
    @SerialName("banner145") val banner145: String? = null,
    @SerialName("banner300") val banner300: String? = null,
    @SerialName("banner400") val banner400: String? = null,
    @SerialName("banner600") val banner600: String? = null,
)

/**
 * Transport assets → the domain [GameArtwork] carried whole through the app (replaced the old
 * `bestArt()` single-URL selector). A null asset block (ITAD omitted `assets`) maps to an empty
 * [GameArtwork] so consumers always get a non-null holder and select via its accessors.
 */
fun RemoteItadGameAssets?.toGameArtwork(): GameArtwork = GameArtwork(
    banner145 = this?.banner145,
    banner300 = this?.banner300,
    banner400 = this?.banner400,
    banner600 = this?.banner600,
    boxart = this?.boxart,
)

@Serializable
data class RemoteItadSearchGame(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String? = null,
    // Defaulted (display field); the row's identity is `id`, which stays required.
    @SerialName("title") val title: String = "",
    @SerialName("type") val type: String? = null,
    @SerialName("mature") val mature: Boolean? = null,
    @SerialName("assets") val assets: RemoteItadGameAssets? = null,
)

@Serializable
data class RemoteItadLookupResponse(
    @SerialName("found") val found: Boolean,
    @SerialName("game") val game: RemoteItadSearchGame? = null,
)

// --- /games/info/v2 (the rich game-info shape; superset of the search game) ---
@Serializable
data class RemoteItadCompanyRef(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String,
)

@Serializable
data class RemoteItadReview(
    @SerialName("score") val score: Int? = null,
    @SerialName("source") val source: String,
    @SerialName("count") val count: Int? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class RemoteItadStats(
    @SerialName("rank") val rank: Int? = null,
    @SerialName("waitlisted") val waitlisted: Int? = null,
    @SerialName("collected") val collected: Int? = null,
)

@Serializable
data class RemoteItadPlayers(
    @SerialName("recent") val recent: Int? = null,
    @SerialName("day") val day: Int? = null,
    @SerialName("week") val week: Int? = null,
    @SerialName("peak") val peak: Int? = null,
)

/**
 * `/games/info/v2` — the full game-info object. A superset of [RemoteItadSearchGame]: the same
 * id/slug/title/assets header plus catalogue metadata (developers, publishers, tags, release date) and
 * the live signals the redesigned Game Page surfaces (storefront/critic [reviews], waitlist/collection
 * [stats], current [players] counts). `ignoreUnknownKeys = true` tolerates ITAD's further extras.
 */
@Serializable
data class RemoteItadGameInfo(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String? = null,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String? = null,
    @SerialName("mature") val mature: Boolean? = null,
    @SerialName("assets") val assets: RemoteItadGameAssets? = null,
    @SerialName("appid") val appid: Int? = null,
    @SerialName("earlyAccess") val earlyAccess: Boolean = false,
    @SerialName("achievements") val achievements: Boolean = false,
    @SerialName("tradingCards") val tradingCards: Boolean = false,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("developers") val developers: List<RemoteItadCompanyRef> = emptyList(),
    @SerialName("publishers") val publishers: List<RemoteItadCompanyRef> = emptyList(),
    @SerialName("reviews") val reviews: List<RemoteItadReview> = emptyList(),
    @SerialName("stats") val stats: RemoteItadStats? = null,
    @SerialName("players") val players: RemoteItadPlayers? = null,
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
    /**
     * ITAD's server-computed deal marker, present on both `/deals/v2` and `/games/prices/v3` deal
     * entries: `"N"` = price just hit a new historical low, `"H"` = currently at the historical low,
     * `"S"` = lowest price this specific store has ever offered, `null` = none. The deal-badge work
     * surfaces `"N"` (new-low badge) and `"S"` (store-low badge) on the deal tiles/rows.
     */
    @SerialName("flag") val flag: String? = null,
    /**
     * The shop's voucher/coupon code applied to reach [price], when one is required (nullable; ITAD
     * leaves it absent otherwise). Presence drives the "with voucher" scissors badge.
     */
    @SerialName("voucher") val voucher: String? = null,
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

// --- /stats/most-waitlisted|most-collected|most-popular/v1 (bare array, ordered by position) ---
@Serializable
data class RemoteItadRankedGame(
    @SerialName("position") val position: Int = 0,
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String? = null,
    @SerialName("title") val title: String,
    @SerialName("count") val count: Int? = null,
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

// --- /bundles/v1 (a bare array of bundles) ---
@Serializable
data class RemoteItadBundlePage(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("shopId") val shopId: Int? = null,
)

@Serializable
data class RemoteItadBundleCounts(
    @SerialName("games") val games: Int = 0,
    @SerialName("media") val media: Int = 0,
)

@Serializable
data class RemoteItadBundleTier(
    @SerialName("price") val price: RemoteItadPrice? = null,
    @SerialName("addon") val addon: Boolean = false,
    // Tier games share the search-game shape (id, slug, title, assets.boxart).
    @SerialName("games") val games: List<RemoteItadSearchGame> = emptyList(),
)

@Serializable
data class RemoteItadBundle(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("page") val page: RemoteItadBundlePage? = null,
    @SerialName("url") val url: String,
    @SerialName("details") val details: String? = null,
    @SerialName("isMature") val isMature: Boolean = false,
    @SerialName("publish") val publish: String? = null,
    @SerialName("expiry") val expiry: String? = null,
    @SerialName("counts") val counts: RemoteItadBundleCounts? = null,
    @SerialName("tiers") val tiers: List<RemoteItadBundleTier> = emptyList(),
)
