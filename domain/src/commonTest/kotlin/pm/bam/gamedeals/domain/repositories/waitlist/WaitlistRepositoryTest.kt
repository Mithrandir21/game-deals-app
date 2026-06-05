package pm.bam.gamedeals.domain.repositories.waitlist

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.auth.AuthTokenStore
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
    fun logged_out_getWaitlist_returns_empty() = runTest {
        val repo = WaitlistRepositoryImpl(
            FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A"))),
            FakeAuthTokenStore(access = null),
        )
        assertEquals(emptyList(), repo.getWaitlist())
        assertEquals(persistentSetOf(), repo.observeWaitlistIds().first())
    }

    @Test
    fun logged_out_toggle_is_a_no_op() = runTest {
        val source = FakeAccountSource()
        val repo = WaitlistRepositoryImpl(source, FakeAuthTokenStore(access = null))
        assertEquals(WaitlistToggleResult.NOT_LOGGED_IN, repo.toggleWaitlist("a"))
        assertTrue(source.added.isEmpty())
        assertTrue(source.removed.isEmpty())
    }

    @Test
    fun logged_in_getWaitlist_populates_the_id_cache() = runTest {
        val repo = WaitlistRepositoryImpl(
            FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A"), WaitlistEntry("b", "B"))),
            FakeAuthTokenStore(access = "token"),
        )
        assertEquals(2, repo.getWaitlist().size)
        assertEquals(setOf("a", "b"), repo.observeWaitlistIds().first().toSet())
    }

    @Test
    fun logged_in_toggle_adds_when_absent() = runTest {
        val source = FakeAccountSource()
        val repo = WaitlistRepositoryImpl(source, FakeAuthTokenStore(access = "token"))

        assertEquals(WaitlistToggleResult.UPDATED, repo.toggleWaitlist("a"))

        assertEquals(listOf("a"), source.added)
        assertTrue(repo.observeIsWaitlisted("a").first())
    }

    @Test
    fun logged_in_toggle_removes_when_present() = runTest {
        val source = FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A")))
        val repo = WaitlistRepositoryImpl(source, FakeAuthTokenStore(access = "token"))
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
    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.LoggedOut)
    override suspend fun getAccessToken(): String? = access
    override suspend fun getRefreshToken(): String? = null
    override suspend fun getUsername(): String? = null
    override suspend fun getExpiresAtEpochMs(): Long = 0L
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, username: String) = Unit
    override suspend fun clear() = Unit
}
