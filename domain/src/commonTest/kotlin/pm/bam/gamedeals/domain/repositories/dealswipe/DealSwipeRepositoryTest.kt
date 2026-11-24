package pm.bam.gamedeals.domain.repositories.dealswipe

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSortField
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.utils.millisInDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DealSwipeRepositoryTest {

    private var now = 100 * millisInDay
    private val queries = mutableListOf<DealsQuery>()
    private var pages: (DealsQuery) -> List<Deal> = { emptyList() }

    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit) {
        everySuspend { getDeals(any()) } calls { (query: DealsQuery) -> queries += query; pages(query) }
    }
    private val waitlistRepository: WaitlistRepository = mock {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf("wanted"))
    }
    private val collectionRepository: CollectionRepository = mock {
        every { observeCollectionIds() } returns flowOf(persistentSetOf("owned"))
    }
    private val ignoredRepository: IgnoredRepository = mock {
        every { observeIgnoredIds() } returns flowOf(persistentSetOf("ignored"))
    }
    private val settingsRepository: SettingsRepository = mock {
        everySuspend { getMatureOptIn() } returns true
    }
    private val storage = FakeStorage()

    private val repository = DealSwipeRepositoryImpl(
        dealsRepository, waitlistRepository, collectionRepository, ignoredRepository, settingsRepository, storage, Clock { now },
    )

    private fun deal(gameId: String, dealId: String = "d-$gameId") = Deal(
        dealID = dealId, title = gameId, storeID = 61, gameID = gameId,
        salePriceValue = 5.0, salePriceDenominated = "$5", normalPriceValue = 20.0, normalPriceDenominated = "$20",
        isOnSale = true, savings = 75.0,
    )

    private fun fullPage(prefix: String) = List(SWIPE_PAGE_SIZE) { deal("$prefix$it") }

    @Test
    fun the_deck_asks_for_the_hottest_deals_with_the_users_mature_choice() = runTest {
        pages = { fullPage("g") }

        repository.nextPage(100)

        val query = queries.single()
        assertEquals(DealsSortField.Hottest, query.sortField)
        assertEquals(DealsSortField.Hottest.defaultDirection, query.sortDirection)
        assertEquals(100, query.offset)
        assertEquals(SWIPE_PAGE_SIZE, query.limit)
        assertTrue(query.mature)
    }

    @Test
    fun games_the_user_wants_owns_or_ignores_are_left_out() = runTest {
        pages = { listOf(deal("wanted"), deal("a"), deal("owned"), deal("ignored"), deal("b")) }

        val page = repository.nextPage(0)

        assertEquals(listOf("a", "b"), page.deals.map { it.gameID })
    }

    @Test
    fun a_game_appears_once_and_deals_without_a_game_are_dropped() = runTest {
        pages = { listOf(deal("a", "d1"), deal("a", "d2"), deal("", "d3")) }

        assertEquals(listOf("d1"), repository.nextPage(0).deals.map { it.dealID })
    }

    @Test
    fun a_full_page_points_at_the_next_and_a_short_page_ends_the_deck() = runTest {
        pages = { fullPage("g") }
        assertEquals(SWIPE_PAGE_SIZE, repository.nextPage(0).nextOffset)

        pages = { listOf(deal("a")) }
        assertNull(repository.nextPage(SWIPE_PAGE_SIZE).nextOffset)
    }

    @Test
    fun the_deck_stops_at_the_depth_limit() = runTest {
        pages = { fullPage("g") }

        assertNull(repository.nextPage(SWIPE_MAX_OFFSET - SWIPE_PAGE_SIZE).nextOffset, "the last page")
        queries.clear()
        val past = repository.nextPage(SWIPE_MAX_OFFSET)

        assertTrue(past.deals.isEmpty())
        assertNull(past.nextOffset)
        assertTrue(queries.isEmpty(), "no call past the limit")
    }

    @Test
    fun a_skipped_game_stays_out_for_thirty_days() = runTest {
        pages = { listOf(deal("a"), deal("b")) }
        repository.skip("a")

        assertEquals(listOf("b"), repository.nextPage(0).deals.map { it.gameID })

        now += SWIPE_SKIP_MS - 1
        assertEquals(listOf("b"), repository.nextPage(0).deals.map { it.gameID }, "a moment short of 30 days")

        now += 1
        assertEquals(listOf("a", "b"), repository.nextPage(0).deals.map { it.gameID }, "back after 30 days")
    }

    @Test
    fun an_undone_skip_brings_the_game_back() = runTest {
        pages = { listOf(deal("a"), deal("b")) }
        repository.skip("a")
        repository.skip("b")

        repository.unskip("a")
        repository.unskip("never-skipped")

        assertEquals(listOf("a"), repository.nextPage(0).deals.map { it.gameID })
    }

    @Test
    fun skips_written_together_all_stick() = runTest {
        pages = { listOf(deal("a"), deal("b"), deal("c")) }

        listOf("a", "b", "c").map { async { repository.skip(it) } }.awaitAll()

        assertTrue(repository.nextPage(0).deals.isEmpty())
    }

    @Test
    fun old_skips_are_pruned_and_the_newest_kept_when_over_the_cap() = runTest {
        repository.skip("old")
        now += SWIPE_SKIP_MS
        repeat(SWIPE_SKIP_CAP + 1) { i ->
            now += 1
            repository.skip("g$i")
        }

        val saved = storage.skips()
        assertEquals(SWIPE_SKIP_CAP, saved.size)
        assertTrue("old" !in saved, "expired")
        assertTrue("g0" !in saved, "the oldest goes over the cap")
        assertTrue("g$SWIPE_SKIP_CAP" in saved)
    }

    @Test
    fun an_unreadable_skip_store_counts_as_no_skips() = runTest {
        storage.failReads = true
        pages = { listOf(deal("a")) }

        assertEquals(listOf("a"), repository.nextPage(0).deals.map { it.gameID })
    }
}

private class FakeStorage : Storage {
    private val saved = mutableMapOf<String, Any>()
    var failReads = false

    @Suppress("UNCHECKED_CAST")
    fun skips(): Map<String, Long> = saved[DEAL_SWIPE_SKIPS_KEY] as Map<String, Long>

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? {
        if (failReads) error("unreadable")
        return (saved[storageKey] as T?) ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        (saved[storageKey] as T?) ?: defaultValue ?: error("no value for $storageKey")

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        saved[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = saved.containsKey(storageKey)
    override suspend fun remove(storageKey: String): Boolean = saved.remove(storageKey) != null
}
