package pm.bam.gamedeals.domain.repositories.collection

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.db.cache.CollectionGameIdEntry
import pm.bam.gamedeals.domain.db.dao.CollectionDao
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.RecordingAnalytics
import pm.bam.gamedeals.domain.repositories.waitlist.FakeAccountSource
import pm.bam.gamedeals.domain.repositories.waitlist.FakeAuthTokenStore
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionRepositoryTest {

    @Test
    fun logged_out_getCollection_returns_empty() = runTest {
        val repo = CollectionRepositoryImpl(
            FakeAccountSource(collection = listOf(CollectionEntry("a", "A"))),
            FakeAuthTokenStore(access = null),
            FakeCollectionDao(initial = listOf("a")),
            RecordingAnalytics(),
        )
        assertEquals(emptyList(), repo.getCollection())
        assertEquals(persistentSetOf(), repo.observeCollectionIds().first())
    }

    @Test
    fun logged_in_getCollection_populates_the_id_cache() = runTest {
        val repo = CollectionRepositoryImpl(
            FakeAccountSource(collection = listOf(CollectionEntry("a", "A"), CollectionEntry("b", "B"))),
            FakeAuthTokenStore(access = "token"),
            FakeCollectionDao(),
            RecordingAnalytics(),
        )
        assertEquals(2, repo.getCollection().size)
        assertEquals(setOf("a", "b"), repo.observeCollectionIds().first().toSet())
    }

    @Test
    fun cold_start_observe_reads_the_persisted_id_set_before_any_refresh() = runTest {
        val repo = CollectionRepositoryImpl(
            FakeAccountSource(),
            FakeAuthTokenStore(access = "token"),
            FakeCollectionDao(initial = listOf("a", "b")),
            RecordingAnalytics(),
        )
        assertEquals(setOf("a", "b"), repo.observeCollectionIds().first().toSet())
    }

    @Test
    fun logged_in_toggle_adds_when_absent() = runTest {
        val source = FakeAccountSource()
        val analytics = RecordingAnalytics()
        val repo = CollectionRepositoryImpl(source, FakeAuthTokenStore(access = "token"), FakeCollectionDao(), analytics)

        val result = repo.toggleCollection("a")

        assertEquals(RepoUpdateResult.UPDATED, result)
        assertEquals(listOf("a"), source.added)
        assertTrue(repo.observeIsCollected("a").first())
        assertEquals(mapOf("game_id" to "a"), analytics.propsOf(AnalyticsEvents.COLLECTION_ADDED))
    }

    @Test
    fun logged_out_toggle_is_a_no_op() = runTest {
        val source = FakeAccountSource()
        val analytics = RecordingAnalytics()
        val repo = CollectionRepositoryImpl(source, FakeAuthTokenStore(access = null), FakeCollectionDao(), analytics)

        val result = repo.toggleCollection("a")

        assertEquals(RepoUpdateResult.NOT_LOGGED_IN, result)
        assertTrue(source.added.isEmpty())
        assertTrue(analytics.captured.isEmpty()) // no event when the write is a no-op
    }
}

/** In-memory [CollectionDao] backed by a reactive [MutableStateFlow] so `observeAll()` emits on every change. */
internal class FakeCollectionDao(initial: List<String> = emptyList()) : CollectionDao {
    private val rows = MutableStateFlow(initial.map { CollectionGameIdEntry(it) })
    override fun observeAll(): Flow<List<CollectionGameIdEntry>> = rows
    override suspend fun contains(gameId: String): Boolean = rows.value.any { it.gameId == gameId }
    override suspend fun add(vararg entries: CollectionGameIdEntry) {
        rows.value = (rows.value + entries).distinctBy { it.gameId }
    }
    override suspend fun delete(gameId: String) { rows.value = rows.value.filterNot { it.gameId == gameId } }
    override suspend fun clear() { rows.value = emptyList() }
}
