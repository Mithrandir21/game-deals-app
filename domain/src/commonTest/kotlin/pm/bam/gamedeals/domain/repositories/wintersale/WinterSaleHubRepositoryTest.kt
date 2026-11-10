package pm.bam.gamedeals.domain.repositories.wintersale

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.testing.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinterSaleHubRepositoryTest {

    private var now = 1_000_000L
    private val logger = RecordingLogger()
    private val source = FakeHubSource()
    private val storage = CountsStorage()
    private val repository = WinterSaleHubRepositoryImpl(source, storage, { now }, logger)

    @Test
    fun steam_leads_and_the_other_stores_keep_their_fixed_order() = runTest {
        val hub = repository.load("GB", includeAdult = false)

        assertEquals(STEAM_SHOP_ID, hub.steam?.shopId)
        assertEquals(HUB_LEAD_DEALS, hub.steam?.deals?.size)
        assertEquals(WINTER_SALE_HUB_STORES.drop(1), hub.otherStores.map { it.shopId })
        assertTrue(hub.otherStores.all { it.deals.size == HUB_STORE_DEALS })
        assertEquals(8, hub.storeCount)
        assertFalse(hub.partial)
    }

    @Test
    fun the_header_counts_the_same_stores_as_home_even_when_a_tile_fails() = runTest {
        source.counts += WinterSaleShopCount(999, "Not on the hub", 50_000)
        val epic = source.counts.indexOfFirst { it.shopId == 16 }
        source.counts[epic] = source.counts[epic].copy(deals = 0)
        source.failing += WinterSaleShelf.Store(35)

        val hub = repository.load("GB", includeAdult = false)

        assertFalse(hub.otherStores.any { it.shopId == 16 }, "a store with no deals stays hidden")
        assertFalse(hub.otherStores.any { it.shopId == 35 }, "a failed tile is hidden")
        assertEquals(6, hub.storeCount, "tiles on screen")
        val expected = source.counts.filter { it.shopId in WINTER_SALE_HUB_STORES && it.deals > 0 }
        assertEquals(WinterSaleDealTotals(expected.sumOf { it.deals }, 7), hub.totals, "the failed store still counts")
        assertEquals(repository.dealTotals("GB"), hub.totals, "Home's card shows the same numbers")
    }

    @Test
    fun a_failing_shelf_hides_only_that_shelf_and_marks_the_hub_partial() = runTest {
        source.failing += WinterSaleShelf.AllTimeLows
        source.failing += WinterSaleShelf.Store(35)

        val hub = repository.load("GB", includeAdult = false)

        assertTrue(hub.allTimeLows.isEmpty())
        assertFalse(hub.otherStores.any { it.shopId == 35 })
        assertEquals(HUB_SHELF_DEALS, hub.bigCuts.size)
        assertTrue(hub.partial)
        assertEquals(2, logger.at(LogLevel.ERROR).size)
    }

    @Test
    fun failed_counts_keep_the_tiles_without_numbers() = runTest {
        source.countsFail = true

        val hub = repository.load("GB", includeAdult = false)

        assertNull(hub.totals)
        assertNull(hub.steam?.dealCount)
        assertEquals("Shop 61", hub.steam?.name, "falls back to the shop name on the deals")
        assertTrue(hub.partial)
    }

    @Test
    fun adult_games_are_dropped_unless_the_setting_allows_them() = runTest {
        source.matureIds += "lows-0"

        val withoutAdult = repository.load("GB", includeAdult = false)
        val withAdult = repository.load("GB", includeAdult = true)

        assertFalse(withoutAdult.allTimeLows.any { it.gameId == "lows-0" })
        assertTrue(withAdult.allTimeLows.any { it.gameId == "lows-0" })
        assertEquals(listOf(false, true), source.requests.filter { it.shelf == WinterSaleShelf.AllTimeLows }.map { it.includeAdult })
    }

    @Test
    fun a_second_open_within_half_an_hour_reuses_the_last_load() = runTest {
        val first = repository.load("GB", includeAdult = false)
        now += WINTER_SALE_HUB_CACHE_MS - 1

        assertSame(first, repository.load("GB", includeAdult = false))
        assertEquals(11, source.calls, "1 count call + 8 stores + 2 shelves")

        now += 1
        repository.load("GB", includeAdult = false)
        assertEquals(21, source.calls, "the shelves again, but the counts are kept for a day")
    }

    @Test
    fun pull_to_refresh_a_new_country_or_a_partial_load_skip_the_cache() = runTest {
        repository.load("GB", includeAdult = false)
        repository.load("GB", includeAdult = false, force = true)
        assertEquals(21, source.calls)

        repository.load("US", includeAdult = false)
        assertEquals(32, source.calls, "a new country asks for its counts too")

        source.failing += WinterSaleShelf.BigCuts
        repository.load("DE", includeAdult = false)
        repository.load("DE", includeAdult = false)
        assertEquals(53, source.calls, "a partial hub is fetched again on the next open")
    }

    @Test
    fun the_cached_store_names_the_list_before_its_first_page() = runTest {
        assertNull(repository.cachedStore(35))
        repository.load("GB", includeAdult = false)

        assertEquals("Shop 35", repository.cachedStore(35)?.name)
        assertEquals(1_035, repository.cachedStore(35)?.dealCount)
        assertEquals("Shop 61", repository.cachedStore(STEAM_SHOP_ID)?.name)
    }

    @Test
    fun a_list_page_asks_for_thirty_games_from_the_offset_and_drops_adult_games() = runTest {
        source.matureIds += "cuts-1"

        val page = repository.page(WinterSaleShelf.BigCuts, "GB", includeAdult = false, offset = 30)

        assertEquals(30, source.requests.single().offset)
        assertEquals(WINTER_SALE_LIST_PAGE_SIZE, source.requests.single().limit)
        assertFalse(page.deals.any { it.gameId == "cuts-1" })
        assertEquals(60, page.nextOffset)
    }

    @Test
    fun home_totals_add_up_the_hub_stores_that_have_deals() = runTest {
        source.counts += WinterSaleShopCount(999, "Not on the hub", 50_000)
        val epic = source.counts.indexOfFirst { it.shopId == 16 }
        source.counts[epic] = source.counts[epic].copy(deals = 0)

        val totals = repository.dealTotals("GB")

        val expected = source.counts.filter { it.shopId in WINTER_SALE_HUB_STORES && it.deals > 0 }
        assertEquals(WinterSaleDealTotals(expected.sumOf { it.deals }, 7), totals)
        assertEquals(1, source.countCalls)
    }

    @Test
    fun counts_are_kept_for_a_day_even_after_a_restart() = runTest {
        repository.dealTotals("GB")
        val restarted = WinterSaleHubRepositoryImpl(source, storage, { now }, logger)

        now += WINTER_SALE_COUNTS_CACHE_MS - 1
        restarted.dealTotals("GB")
        restarted.load("GB", includeAdult = false)
        assertEquals(1, source.countCalls, "the saved counts serve Home and the hub")

        now += 1
        restarted.dealTotals("GB")
        assertEquals(2, source.countCalls)
    }

    @Test
    fun a_failed_count_falls_back_to_the_last_one_for_that_country() = runTest {
        source.countsFail = true
        assertNull(repository.dealTotals("GB"), "never loaded: no totals")

        source.countsFail = false
        val first = repository.dealTotals("GB")
        now += WINTER_SALE_COUNTS_CACHE_MS
        source.countsFail = true

        assertEquals(first, repository.dealTotals("GB"))
        assertNull(repository.dealTotals("US"), "another country's counts are never used")
    }

    @Test
    fun an_unreadable_saved_count_is_logged_and_fetched_again() = runTest {
        storage.broken = true

        val totals = repository.dealTotals("GB")

        assertEquals(8, totals?.storeCount)
        assertEquals(1, source.countCalls)
        assertTrue(logger.at(LogLevel.ERROR).isNotEmpty())
    }

    @Test
    fun a_clock_that_moved_back_loads_again_instead_of_trusting_the_cache() = runTest {
        val first = repository.load("GB", includeAdult = false)
        now -= 1

        val second = repository.load("GB", includeAdult = false)

        assertNotSame(first, second)
        assertEquals(2, source.countCalls, "the saved counts are not trusted either")
    }

    @Test
    fun a_hub_with_no_deals_anywhere_is_not_kept() = runTest {
        source.emptyShelves = true

        val hub = repository.load("GB", includeAdult = false)
        repository.load("GB", includeAdult = false)

        assertTrue(hub.isEmpty)
        assertFalse(hub.partial)
        assertEquals(21, source.calls, "the shelves are asked again on the next open; the counts are kept")
        assertNull(repository.cachedStore(STEAM_SHOP_ID))
    }

    @Test
    fun without_steam_the_other_stores_still_show_and_steam_still_counts() = runTest {
        source.failing += WinterSaleShelf.Store(STEAM_SHOP_ID)

        val hub = repository.load("GB", includeAdult = false)

        assertNull(hub.steam)
        assertEquals(7, hub.otherStores.size)
        assertTrue(hub.partial)
        assertEquals(8, hub.totals?.storeCount)
    }

    @Test
    fun a_store_with_no_full_games_on_sale_hides_its_tile_without_marking_the_hub_partial() = runTest {
        source.emptyStores += 37

        val hub = repository.load("GB", includeAdult = false)

        assertFalse(hub.otherStores.any { it.shopId == 37 })
        assertFalse(hub.partial)
    }

    @Test
    fun no_hub_store_with_deals_gives_no_total() = runTest {
        for (i in source.counts.indices) source.counts[i] = source.counts[i].copy(deals = 0)

        assertNull(repository.dealTotals("GB"))
        assertNull(repository.load("GB", includeAdult = false).totals)
    }

    @Test
    fun a_saved_count_from_a_clock_that_moved_back_is_fetched_again() = runTest {
        repository.dealTotals("GB")
        now -= 1_000

        repository.dealTotals("GB")

        assertEquals(2, source.countCalls)
    }

    @Test
    fun shelves_round_trip_through_their_keys() {
        listOf(WinterSaleShelf.Store(61), WinterSaleShelf.AllTimeLows, WinterSaleShelf.BigCuts).forEach {
            assertEquals(it, WinterSaleShelf.fromKey(it.key))
        }
        assertNull(WinterSaleShelf.fromKey("store:steam"))
        assertNull(WinterSaleShelf.fromKey("for_you"))
    }

    @Test
    fun only_cuts_of_seventy_five_percent_or_more_count_as_big() {
        assertFalse(deal("a", cut = 74).isBigCut)
        assertTrue(deal("b", cut = 75).isBigCut)
    }
}

private data class Request(val shelf: WinterSaleShelf, val includeAdult: Boolean, val offset: Int, val limit: Int)

private class FakeHubSource : WinterSaleHubSource {
    val counts = WINTER_SALE_HUB_STORES.map { WinterSaleShopCount(it, "Shop $it", 1_000 + it) }.toMutableList()
    var countsFail = false
    var countCalls = 0
    val failing = mutableSetOf<WinterSaleShelf>()
    val matureIds = mutableSetOf<String>()
    val requests = mutableListOf<Request>()
    var calls = 0
    /** Every shelf comes back with no deals, as if nothing were on sale. */
    var emptyShelves = false
    /** Stores whose shelf comes back with no full games (their count may still include DLC). */
    val emptyStores = mutableSetOf<Int>()

    override suspend fun shopCounts(country: String): List<WinterSaleShopCount> {
        calls++
        countCalls++
        if (countsFail) error("counts down")
        return counts.toList()
    }

    override suspend fun deals(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int, limit: Int): WinterSaleDealPage {
        calls++
        requests += Request(shelf, includeAdult, offset, limit)
        if (shelf in failing) error("${shelf.key} down")
        if (emptyShelves || (shelf is WinterSaleShelf.Store && shelf.shopId in emptyStores)) return WinterSaleDealPage(emptyList(), null)
        val prefix = when (shelf) {
            is WinterSaleShelf.Store -> "s${shelf.shopId}"
            WinterSaleShelf.AllTimeLows -> "lows"
            WinterSaleShelf.BigCuts -> "cuts"
        }
        val shopId = (shelf as? WinterSaleShelf.Store)?.shopId ?: 6
        // One extra row so the adult filter can drop one and still fill the shelf.
        val deals = (0..limit).map { deal("$prefix-$it", shopId = shopId, mature = "$prefix-$it" in matureIds) }
        return WinterSaleDealPage(deals.take(limit + if (matureIds.isEmpty()) 0 else 1), nextOffset = offset + limit)
    }
}

private fun deal(id: String, shopId: Int = 61, cut: Int = 80, mature: Boolean = false) = WinterSaleDeal(
    gameId = id, title = "Game $id", artwork = GameArtwork(), shopId = shopId, shopName = "Shop $shopId",
    price = WinterSaleMoney(1.99, "GBP"), regular = WinterSaleMoney(9.99, "GBP"), cutPercent = cut, url = "https://itad.link/$id",
    voucher = null, flag = null, mature = mature,
)

/** Keeps saved values in memory as typed objects; [broken] makes every read and write fail like a corrupt store. */
private class CountsStorage : Storage {
    val values = mutableMapOf<String, Any>()
    var broken = false

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("no value for $storageKey")

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? {
        if (broken) error("corrupt store")
        return values[storageKey] as T? ?: defaultValue
    }

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        if (broken) error("corrupt store")
        values[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = storageKey in values
    override suspend fun remove(storageKey: String): Boolean = values.remove(storageKey) != null
}
