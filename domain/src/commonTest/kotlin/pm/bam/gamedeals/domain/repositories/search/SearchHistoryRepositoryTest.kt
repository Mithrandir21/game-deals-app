package pm.bam.gamedeals.domain.repositories.search

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.DealsFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchHistoryRepositoryTest {

    private fun repo(now: Long = 1_000L) = SearchHistoryRepositoryImpl(FakeStorage(), FakeClock(now))

    @Test
    fun recent_defaults_empty_then_records_most_recent_first() = runTest {
        val repo = repo()
        assertEquals(emptyList(), repo.observeRecentSearches().first())

        repo.recordSearch("halo")
        repo.recordSearch("doom")
        assertEquals(listOf("doom", "halo"), repo.observeRecentSearches().first())
    }

    @Test
    fun recent_dedupes_case_insensitively_and_moves_to_front() = runTest {
        val repo = repo()
        repo.recordSearch("Halo")
        repo.recordSearch("Doom")
        repo.recordSearch("halo") // same as "Halo" ignoring case

        assertEquals(listOf("halo", "Doom"), repo.observeRecentSearches().first())
    }

    @Test
    fun recent_is_capped_and_blanks_ignored() = runTest {
        val repo = repo()
        repeat(MAX_RECENT_SEARCHES + 5) { repo.recordSearch("q$it") }
        repo.recordSearch("   ")

        val recent = repo.observeRecentSearches().first()
        assertEquals(MAX_RECENT_SEARCHES, recent.size)
        assertEquals("q${MAX_RECENT_SEARCHES + 4}", recent.first()) // newest kept
    }

    @Test
    fun clear_recent_empties_the_list() = runTest {
        val repo = repo()
        repo.recordSearch("halo")
        repo.clearRecentSearches()
        assertEquals(emptyList(), repo.observeRecentSearches().first())
    }

    @Test
    fun saving_pins_query_and_filter_replacing_same_name() = runTest {
        val repo = repo(now = 42L)
        val filter = DealsFilter(minCutPercent = 75)
        repo.saveSearch("Deep discounts", "witcher", filter)

        val saved = repo.observeSavedSearches().first()
        assertEquals(1, saved.size)
        assertEquals("Deep discounts", saved.first().name)
        assertEquals("witcher", saved.first().query)
        assertEquals(75, saved.first().filter.minCutPercent)
        assertEquals(42L, saved.first().addedAtMs)

        // Same name replaces rather than duplicates.
        repo.saveSearch("deep discounts", "elden", DealsFilter())
        val after = repo.observeSavedSearches().first()
        assertEquals(1, after.size)
        assertEquals("elden", after.first().query)
    }

    @Test
    fun removing_a_saved_search_drops_it() = runTest {
        val repo = repo()
        repo.saveSearch("A", "a", DealsFilter())
        repo.saveSearch("B", "b", DealsFilter())
        repo.removeSavedSearch("a")

        assertEquals(listOf("B"), repo.observeSavedSearches().first().map { it.name })
    }
}

private class FakeClock(private val now: Long) : Clock {
    override fun nowMillis(): Long = now
}

private class FakeStorage : Storage {
    private val saved = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        (saved[storageKey] as T?) ?: defaultValue

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
