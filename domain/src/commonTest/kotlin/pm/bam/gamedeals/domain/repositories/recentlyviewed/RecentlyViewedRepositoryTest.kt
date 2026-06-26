package pm.bam.gamedeals.domain.repositories.recentlyviewed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.RecordingAnalytics
import pm.bam.gamedeals.domain.db.cache.RecentlyViewedGameEntry
import pm.bam.gamedeals.domain.db.dao.RecentlyViewedDao
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentlyViewedRepositoryTest {

    // Advancing clock so each recorded view gets a strictly-later timestamp (deterministic ordering).
    private var clockMillis = 1_000L
    private val clock = Clock { clockMillis }
    private val dao = FakeRecentlyViewedDao()
    private val analytics = RecordingAnalytics()
    private val repository = RecentlyViewedRepositoryImpl(dao, clock, analytics)

    private suspend fun record(id: String) {
        clockMillis += 1_000
        repository.recordView(gameId = id, title = "Title $id", boxart = "box-$id")
    }

    @Test
    fun most_recent_first() = runTest {
        record("a"); record("b"); record("c")

        assertEquals(listOf("c", "b", "a"), repository.observeRecentlyViewed().first().map { it.gameId })
    }

    @Test
    fun re_viewing_a_game_dedupes_and_moves_it_to_the_top() = runTest {
        record("a"); record("b"); record("a")

        val ids = repository.observeRecentlyViewed().first().map { it.gameId }
        assertEquals(listOf("a", "b"), ids) // single 'a', now newest
    }

    @Test
    fun list_is_capped_at_twenty() = runTest {
        repeat(RECENTLY_VIEWED_CAP + 5) { record("g$it") }

        val result = repository.observeRecentlyViewed().first()
        assertEquals(RECENTLY_VIEWED_CAP, result.size)
        // The five oldest were trimmed; the newest (g24) is first.
        assertEquals("g${RECENTLY_VIEWED_CAP + 4}", result.first().gameId)
        assertTrue(result.none { it.gameId == "g0" })
    }

    @Test
    fun blank_game_id_is_ignored() = runTest {
        repository.recordView(gameId = "", title = "x", boxart = null)

        assertTrue(repository.observeRecentlyViewed().first().isEmpty())
    }

    @Test
    fun remove_drops_one_and_reports_analytics() = runTest {
        record("a"); record("b")

        repository.remove("a")

        assertEquals(listOf("b"), repository.observeRecentlyViewed().first().map { it.gameId })
        assertEquals(mapOf("scope" to "one"), analytics.propsOf(AnalyticsEvents.RECENTLY_VIEWED_CLEARED))
    }

    @Test
    fun clear_empties_and_reports_analytics() = runTest {
        record("a"); record("b")

        repository.clear()

        assertTrue(repository.observeRecentlyViewed().first().isEmpty())
        assertEquals(mapOf("scope" to "all"), analytics.propsOf(AnalyticsEvents.RECENTLY_VIEWED_CLEARED))
    }
}

/** In-memory [RecentlyViewedDao] mirroring the real query semantics (upsert-by-id, ordered trim). */
private class FakeRecentlyViewedDao : RecentlyViewedDao {
    private val rows = MutableStateFlow<List<RecentlyViewedGameEntry>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<RecentlyViewedGameEntry>> =
        rows.map { list -> list.sortedByDescending { it.viewedAtEpochMs }.take(limit) }

    override suspend fun upsert(entry: RecentlyViewedGameEntry) {
        rows.value = rows.value.filterNot { it.gameId == entry.gameId } + entry
    }

    override suspend fun trimTo(keep: Int) {
        val survivors = rows.value.sortedByDescending { it.viewedAtEpochMs }.take(keep).map { it.gameId }.toSet()
        rows.value = rows.value.filter { it.gameId in survivors }
    }

    override suspend fun delete(gameId: String) {
        rows.value = rows.value.filterNot { it.gameId == gameId }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}
