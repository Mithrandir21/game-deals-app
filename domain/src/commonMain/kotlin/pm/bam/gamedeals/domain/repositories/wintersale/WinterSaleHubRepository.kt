package pm.bam.gamedeals.domain.repositories.wintersale

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/** A deal at or above this cut gets the sale's big-discount ribbon, and fills the "75% off or more" shelf. */
const val WINTER_SALE_BIG_CUT_PERCENT = 75

/** ITAD's marker on a deal. A deal carries at most one: a new low beats an all-time low beats a store low. */
enum class WinterSaleDealFlag { NewLow, AllTimeLow, StoreLow }

data class WinterSaleDeal(
    val gameId: String,
    val title: String,
    val artwork: GameArtwork,
    val shopId: Int,
    val shopName: String,
    val price: WinterSaleMoney,
    val regular: WinterSaleMoney?,
    val cutPercent: Int,
    val url: String,
    val voucher: String?,
    val flag: WinterSaleDealFlag?,
    val mature: Boolean,
) {
    val isBigCut: Boolean get() = cutPercent >= WINTER_SALE_BIG_CUT_PERCENT
}

/** One list of sale deals: a store's, the all-time lows, or the big cuts. Each is sorted by ITAD popularity. */
sealed interface WinterSaleShelf {
    /** Stable text form, used as a navigation argument and an analytics value. */
    val key: String

    data class Store(val shopId: Int) : WinterSaleShelf {
        override val key: String get() = "store:$shopId"
    }

    data object AllTimeLows : WinterSaleShelf {
        override val key: String get() = "lows"
    }

    data object BigCuts : WinterSaleShelf {
        override val key: String get() = "cuts"
    }

    companion object {
        fun fromKey(key: String): WinterSaleShelf? = when {
            key == AllTimeLows.key -> AllTimeLows
            key == BigCuts.key -> BigCuts
            key.startsWith("store:") -> key.removePrefix("store:").toIntOrNull()?.let(::Store)
            else -> null
        }
    }
}

data class WinterSaleDealPage(val deals: List<WinterSaleDeal>, val nextOffset: Int?)

/** A shop's name and how many deals ITAD lists for it right now (DLC and soundtracks included). */
@Serializable
data class WinterSaleShopCount(val shopId: Int, val name: String, val deals: Int)

/** The sale's headline numbers for the Home and guide cards: deals across the hub's stores, and how many of them have deals. */
data class WinterSaleDealTotals(val totalDeals: Int, val storeCount: Int)

interface WinterSaleHubSource {
    suspend fun shopCounts(country: String): List<WinterSaleShopCount>

    suspend fun deals(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int, limit: Int): WinterSaleDealPage
}

data class WinterSaleHubStore(val shopId: Int, val name: String, val dealCount: Int?, val deals: List<WinterSaleDeal>)

data class WinterSaleHub(
    val country: String,
    val includeAdult: Boolean,
    /** The large lead tile; null when Steam's deals couldn't load. */
    val steam: WinterSaleHubStore?,
    val otherStores: List<WinterSaleHubStore>,
    val allTimeLows: List<WinterSaleDeal>,
    val bigCuts: List<WinterSaleDeal>,
    /**
     * The headline numbers, the same as Home's card: every hub store ITAD says has deals, even one whose tile failed.
     * Null when the counts couldn't load or no store has deals.
     */
    val totals: WinterSaleDealTotals?,
    /** True when at least one shelf or the counts failed, so the hub is showing less than it should. */
    val partial: Boolean,
    val loadedAtEpochMillis: Long,
) {
    val storeCount: Int get() = otherStores.size + if (steam != null) 1 else 0
    val isEmpty: Boolean get() = steam == null && otherStores.isEmpty() && allTimeLows.isEmpty() && bigCuts.isEmpty()
}

interface WinterSaleHubRepository {
    /** The hub's shelves, cached for [WINTER_SALE_HUB_CACHE_MS] per country and adult setting unless [force]d. */
    suspend fun load(country: String, includeAdult: Boolean, force: Boolean = false): WinterSaleHub

    /** One page of a shelf for the sale list screen; throws when ITAD can't be reached. */
    suspend fun page(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int): WinterSaleDealPage

    /** The store's tile from the last hub load, so its list can show the name and count before the first page arrives. */
    fun cachedStore(shopId: Int): WinterSaleHubStore?

    /**
     * Deals across the hub's stores, for the Home card. Uses the store counts kept for [WINTER_SALE_COUNTS_CACHE_MS] (on the
     * phone, so an app restart doesn't ask again); null when they have never loaded for [country].
     */
    suspend fun dealTotals(country: String): WinterSaleDealTotals?
}

/** The stores with a tile, Steam first (it's the Steam sale). The rest keep this order. */
val WINTER_SALE_HUB_STORES: List<Int> = listOf(STEAM_SHOP_ID, 35, 16, 6, 36, 37, 20, 64)

const val STEAM_SHOP_ID = 61
const val WINTER_SALE_HUB_CACHE_MS = 30 * 60 * 1000L

/** Store counts only feed the headline numbers, which are rounded anyway, so a day-old count is fine. */
const val WINTER_SALE_COUNTS_CACHE_MS = 24 * 60 * 60 * 1000L
const val WINTER_SALE_LIST_PAGE_SIZE = 30
internal const val HUB_LEAD_DEALS = 10
internal const val HUB_STORE_DEALS = 3
internal const val HUB_SHELF_DEALS = 12

internal class WinterSaleHubRepositoryImpl(
    private val source: WinterSaleHubSource,
    private val storage: Storage,
    private val clock: Clock,
    private val logger: Logger,
) : WinterSaleHubRepository {

    private val mutex = Mutex()
    private val countsMutex = Mutex()
    private var counts: StoredShopCounts? = null
    private var countsRead = false
    @kotlin.concurrent.Volatile
    private var cached: WinterSaleHub? = null

    override suspend fun load(country: String, includeAdult: Boolean, force: Boolean): WinterSaleHub = mutex.withLock {
        val now = clock.nowMillis()
        cached?.takeIf {
            !force && it.country == country && it.includeAdult == includeAdult && !it.partial &&
                now >= it.loadedAtEpochMillis && now - it.loadedAtEpochMillis < WINTER_SALE_HUB_CACHE_MS
        }?.let { return it }

        val hub = fetch(country, includeAdult, now)
        if (!hub.isEmpty) cached = hub
        hub
    }

    override suspend fun dealTotals(country: String): WinterSaleDealTotals? = shopCounts(country)?.let(::totalsOf)

    /** Counts every hub store with deals, not just the tiles that loaded, so Home and the hub always show the same numbers. */
    private fun totalsOf(byShop: Map<Int, WinterSaleShopCount>): WinterSaleDealTotals? {
        val withDeals = WINTER_SALE_HUB_STORES.mapNotNull { byShop[it]?.takeIf { count -> count.deals > 0 } }
        return if (withDeals.isEmpty()) null else WinterSaleDealTotals(withDeals.sumOf { it.deals }, withDeals.size)
    }

    /**
     * The store counts for [country], fetched at most once a day and kept on the phone. If a new fetch fails, an older
     * count for the same country still serves: the headline is rounded and only says the sale is big.
     */
    private suspend fun shopCounts(country: String): Map<Int, WinterSaleShopCount>? = countsMutex.withLock {
        if (!countsRead) {
            countsRead = true
            counts = try {
                storage.getNullable(COUNTS_KEY, StoredShopCounts.serializer())
            } catch (error: CancellationException) {
                throw error
            } catch (t: Throwable) {
                error(logger, t)
                null
            }
        }
        val now = clock.nowMillis()
        val known = counts?.takeIf { it.country == country }
        if (known != null && now >= known.fetchedAtEpochMillis && now - known.fetchedAtEpochMillis < WINTER_SALE_COUNTS_CACHE_MS) {
            return known.counts.associateBy { it.shopId }
        }
        val fetched = attempt { source.shopCounts(country) } ?: return known?.counts?.associateBy { it.shopId }
        val stored = StoredShopCounts(country, now, fetched)
        counts = stored
        attempt { storage.save(COUNTS_KEY, stored, StoredShopCounts.serializer()) }
        fetched.associateBy { it.shopId }
    }

    override fun cachedStore(shopId: Int): WinterSaleHubStore? =
        cached?.let { hub -> listOfNotNull(hub.steam).plus(hub.otherStores).firstOrNull { it.shopId == shopId } }

    override suspend fun page(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int): WinterSaleDealPage {
        val page = source.deals(shelf, country, includeAdult, offset, WINTER_SALE_LIST_PAGE_SIZE)
        return page.copy(deals = page.deals.forAdultSetting(includeAdult))
    }

    private suspend fun fetch(country: String, includeAdult: Boolean, now: Long): WinterSaleHub = coroutineScope {
        val counts = async { shopCounts(country) }
        val stores = WINTER_SALE_HUB_STORES.map { shopId ->
            val limit = if (shopId == STEAM_SHOP_ID) HUB_LEAD_DEALS else HUB_STORE_DEALS
            shopId to async { attempt { shelf(WinterSaleShelf.Store(shopId), country, includeAdult, limit) } }
        }
        val lows = async { attempt { shelf(WinterSaleShelf.AllTimeLows, country, includeAdult, HUB_SHELF_DEALS) } }
        val cuts = async { attempt { shelf(WinterSaleShelf.BigCuts, country, includeAdult, HUB_SHELF_DEALS) } }

        val countsById = counts.await()
        val tiles = stores.mapNotNull { (shopId, deals) ->
            val shown = deals.await()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val count = countsById?.get(shopId)
            // A store that ITAD says has no deals stays hidden even if a stray row came back.
            if (count != null && count.deals <= 0) return@mapNotNull null
            WinterSaleHubStore(shopId, count?.name ?: shown.first().shopName, count?.deals, shown)
        }
        val lowsShown = lows.await()
        val cutsShown = cuts.await()
        val steam = tiles.firstOrNull { it.shopId == STEAM_SHOP_ID }
        val failed = countsById == null || lowsShown == null || cutsShown == null || stores.any { it.second.await() == null }
        WinterSaleHub(
            country = country,
            includeAdult = includeAdult,
            steam = steam,
            otherStores = tiles.filter { it.shopId != STEAM_SHOP_ID },
            allTimeLows = lowsShown.orEmpty(),
            bigCuts = cutsShown.orEmpty(),
            totals = countsById?.let(::totalsOf),
            partial = failed,
            loadedAtEpochMillis = now,
        )
    }

    private suspend fun shelf(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, limit: Int): List<WinterSaleDeal> =
        source.deals(shelf, country, includeAdult, offset = 0, limit = limit).deals.forAdultSetting(includeAdult).distinctBy { it.gameId }.take(limit)

    /** One shelf failing hides that shelf only; the rest of the hub still shows. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (t: Throwable) {
        error(logger, t)
        null
    }
}

@Serializable
private data class StoredShopCounts(val country: String, val fetchedAtEpochMillis: Long, val counts: List<WinterSaleShopCount>)

private const val COUNTS_KEY = "winter_sale_shop_counts_v1"

private fun List<WinterSaleDeal>.forAdultSetting(includeAdult: Boolean) = if (includeAdult) this else filterNot { it.mature }
