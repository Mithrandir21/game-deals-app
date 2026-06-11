package pm.bam.gamedeals.domain.repositories.waitlist

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.cache.WaitlistGameIdEntry
import pm.bam.gamedeals.domain.db.dao.WaitlistDao
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaitlistRepositoryTest {

    @Test
    fun logged_out_getWaitlist_clears_and_returns_empty() = runTest {
        val dao = FakeWaitlistDao(initial = listOf("a"))
        val repo = WaitlistRepositoryImpl(
            FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A"))),
            FakeAuthTokenStore(access = null),
            dao,
        )
        assertEquals(emptyList(), repo.getWaitlist())
        // Logged out: the cached id set is cleared and the observed set is empty (auth-gated).
        assertEquals(persistentSetOf(), repo.observeWaitlistIds().first())
    }

    @Test
    fun logged_out_toggle_is_a_no_op() = runTest {
        val source = FakeAccountSource()
        val repo = WaitlistRepositoryImpl(source, FakeAuthTokenStore(access = null), FakeWaitlistDao())
        assertEquals(WaitlistToggleResult.NOT_LOGGED_IN, repo.toggleWaitlist("a"))
        assertTrue(source.added.isEmpty())
        assertTrue(source.removed.isEmpty())
    }

    @Test
    fun logged_in_getWaitlist_populates_the_id_cache() = runTest {
        val repo = WaitlistRepositoryImpl(
            FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A"), WaitlistEntry("b", "B"))),
            FakeAuthTokenStore(access = "token"),
            FakeWaitlistDao(),
        )
        assertEquals(2, repo.getWaitlist().size)
        assertEquals(setOf("a", "b"), repo.observeWaitlistIds().first().toSet())
    }

    @Test
    fun cold_start_observe_reads_the_persisted_id_set_before_any_refresh() = runTest {
        // The id set survives process death, so the heart state is correct without calling getWaitlist.
        val repo = WaitlistRepositoryImpl(
            FakeAccountSource(),
            FakeAuthTokenStore(access = "token"),
            FakeWaitlistDao(initial = listOf("a", "b")),
        )
        assertEquals(setOf("a", "b"), repo.observeWaitlistIds().first().toSet())
    }

    @Test
    fun logged_in_toggle_adds_when_absent() = runTest {
        val source = FakeAccountSource()
        val repo = WaitlistRepositoryImpl(source, FakeAuthTokenStore(access = "token"), FakeWaitlistDao())

        assertEquals(WaitlistToggleResult.UPDATED, repo.toggleWaitlist("a"))

        assertEquals(listOf("a"), source.added)
        assertTrue(repo.observeIsWaitlisted("a").first())
    }

    @Test
    fun logged_in_toggle_removes_when_present() = runTest {
        val source = FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A")))
        val repo = WaitlistRepositoryImpl(source, FakeAuthTokenStore(access = "token"), FakeWaitlistDao())
        repo.getWaitlist() // seed cache with "a"

        assertEquals(WaitlistToggleResult.UPDATED, repo.toggleWaitlist("a"))

        assertEquals(listOf("a"), source.removed)
        assertFalse(repo.observeIsWaitlisted("a").first())
    }
}

internal class FakeAccountSource(
    private val waitlist: List<WaitlistEntry> = emptyList(),
    private val collection: List<CollectionEntry> = emptyList(),
) : ItadAccountSource {
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()

    override suspend fun getUserInfo(): ItadUser = ItadUser("user")
    override suspend fun getWaitlist(): List<WaitlistEntry> = waitlist
    override suspend fun addToWaitlist(gameId: String) { added += gameId }
    override suspend fun removeFromWaitlist(gameId: String) { removed += gameId }
    override suspend fun getCollection(): List<CollectionEntry> = collection
    override suspend fun addToCollection(gameId: String) { added += gameId }
    override suspend fun removeFromCollection(gameId: String) { removed += gameId }
}

internal class FakeAuthTokenStore(private val access: String?) : AuthTokenStore {
    override fun observeAuthState(): Flow<AuthState> =
        flowOf(if (access != null) AuthState.LoggedIn("user") else AuthState.LoggedOut)
    override suspend fun getAccessToken(): String? = access
    override suspend fun getRefreshToken(): String? = null
    override suspend fun getUsername(): String? = null
    override suspend fun getExpiresAtEpochMs(): Long = 0L
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, username: String) = Unit
    override suspend fun clear() = Unit
}

/** In-memory [WaitlistDao] backed by a reactive [MutableStateFlow] so `observeAll()` emits on every change. */
internal class FakeWaitlistDao(initial: List<String> = emptyList()) : WaitlistDao {
    private val rows = MutableStateFlow(initial.map { WaitlistGameIdEntry(it) })
    override fun observeAll(): Flow<List<WaitlistGameIdEntry>> = rows
    override suspend fun contains(gameId: String): Boolean = rows.value.any { it.gameId == gameId }
    override suspend fun add(vararg entries: WaitlistGameIdEntry) {
        rows.value = (rows.value + entries).distinctBy { it.gameId }
    }
    override suspend fun delete(gameId: String) { rows.value = rows.value.filterNot { it.gameId == gameId } }
    override suspend fun clear() { rows.value = emptyList() }
}
