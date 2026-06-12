package pm.bam.gamedeals.domain.repositories.ignored

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.db.cache.IgnoredGameIdEntry
import pm.bam.gamedeals.domain.db.dao.IgnoredDao
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.repositories.waitlist.FakeAccountSource
import pm.bam.gamedeals.domain.repositories.waitlist.FakeAuthTokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors `WaitlistRepositoryTest` — the ignore list reuses the waitlist id-set pattern (#279). */
class IgnoredRepositoryTest {

    @Test
    fun logged_out_getIgnored_clears_and_returns_empty() = runTest {
        val dao = FakeIgnoredDao(initial = listOf("a"))
        val repo = IgnoredRepositoryImpl(
            FakeAccountSource(ignored = listOf(IgnoredEntry("a", "A"))),
            FakeAuthTokenStore(access = null),
            dao,
        )
        assertEquals(emptyList(), repo.getIgnored())
        assertEquals(persistentSetOf(), repo.observeIgnoredIds().first())
    }

    @Test
    fun logged_out_toggle_is_a_no_op() = runTest {
        val source = FakeAccountSource()
        val repo = IgnoredRepositoryImpl(source, FakeAuthTokenStore(access = null), FakeIgnoredDao())
        assertEquals(IgnoredToggleResult.NOT_LOGGED_IN, repo.toggleIgnored("a"))
        assertTrue(source.added.isEmpty())
        assertTrue(source.removed.isEmpty())
    }

    @Test
    fun logged_in_getIgnored_populates_the_id_cache() = runTest {
        val repo = IgnoredRepositoryImpl(
            FakeAccountSource(ignored = listOf(IgnoredEntry("a", "A"), IgnoredEntry("b", "B"))),
            FakeAuthTokenStore(access = "token"),
            FakeIgnoredDao(),
        )
        assertEquals(2, repo.getIgnored().size)
        assertEquals(setOf("a", "b"), repo.observeIgnoredIds().first().toSet())
    }

    @Test
    fun cold_start_observe_reads_the_persisted_id_set_before_any_refresh() = runTest {
        // The id set survives process death, so the Deals/Search filter is correct without calling getIgnored.
        val repo = IgnoredRepositoryImpl(
            FakeAccountSource(),
            FakeAuthTokenStore(access = "token"),
            FakeIgnoredDao(initial = listOf("a", "b")),
        )
        assertEquals(setOf("a", "b"), repo.observeIgnoredIds().first().toSet())
    }

    @Test
    fun logged_in_toggle_adds_when_absent() = runTest {
        val source = FakeAccountSource()
        val repo = IgnoredRepositoryImpl(source, FakeAuthTokenStore(access = "token"), FakeIgnoredDao())

        assertEquals(IgnoredToggleResult.UPDATED, repo.toggleIgnored("a"))

        assertEquals(listOf("a"), source.added)
        assertTrue(repo.observeIsIgnored("a").first())
    }

    @Test
    fun logged_in_toggle_removes_when_present() = runTest {
        val source = FakeAccountSource(ignored = listOf(IgnoredEntry("a", "A")))
        val repo = IgnoredRepositoryImpl(source, FakeAuthTokenStore(access = "token"), FakeIgnoredDao())
        repo.getIgnored() // seed cache with "a"

        assertEquals(IgnoredToggleResult.UPDATED, repo.toggleIgnored("a"))

        assertEquals(listOf("a"), source.removed)
        assertFalse(repo.observeIsIgnored("a").first())
    }
}

/** In-memory [IgnoredDao] backed by a reactive [MutableStateFlow] so `observeAll()` emits on every change. */
internal class FakeIgnoredDao(initial: List<String> = emptyList()) : IgnoredDao {
    private val rows = MutableStateFlow(initial.map { IgnoredGameIdEntry(it) })
    override fun observeAll(): Flow<List<IgnoredGameIdEntry>> = rows
    override suspend fun contains(gameId: String): Boolean = rows.value.any { it.gameId == gameId }
    override suspend fun add(vararg entries: IgnoredGameIdEntry) {
        rows.value = (rows.value + entries).distinctBy { it.gameId }
    }
    override suspend fun delete(gameId: String) { rows.value = rows.value.filterNot { it.gameId == gameId } }
    override suspend fun clear() { rows.value = emptyList() }
}
