package pm.bam.gamedeals.domain.repositories.waitlist

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource

/**
 * The user's ITAD waitlist (epic #219). Replaces the removed local Favourites (Phase 3); the heart is
 * login-gated and writes here. Mirrors the old `FavouritesRepository` shape so call sites swap cleanly.
 *
 * Backed by [ItadAccountSource] (remote) with a local [MutableStateFlow] id cache for reactive UI:
 * [getWaitlist] refreshes the cache from the source, and [toggleWaitlist] applies an optimistic local
 * update around the remote add/remove. All writes are login-gated — a no-op when logged out (the UI
 * gates the heart, and [getWaitlist] returns empty so logged-out users simply see nothing).
 */
/** Outcome of a [WaitlistRepository.toggleWaitlist] call, so callers can react to the login gate. */
enum class WaitlistToggleResult {
    /** The waitlist was updated (added or removed). */
    UPDATED,

    /** No-op: the user is logged out. Callers should prompt the user to sign in. */
    NOT_LOGGED_IN,
}

interface WaitlistRepository {
    fun observeWaitlistIds(): Flow<ImmutableSet<String>>
    fun observeIsWaitlisted(gameId: String): Flow<Boolean>
    suspend fun getWaitlist(): List<WaitlistEntry>

    /**
     * Adds/removes [gameId] on the user's ITAD waitlist, returning [WaitlistToggleResult]. When logged
     * out this is a no-op and returns [WaitlistToggleResult.NOT_LOGGED_IN] so the UI can route the user
     * to sign in (the heart is login-gated — there is no local waitlist).
     */
    suspend fun toggleWaitlist(gameId: String): WaitlistToggleResult
}

internal class WaitlistRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
) : WaitlistRepository {

    private val ids = MutableStateFlow<ImmutableSet<String>>(persistentSetOf())

    override fun observeWaitlistIds(): Flow<ImmutableSet<String>> = ids
    override fun observeIsWaitlisted(gameId: String): Flow<Boolean> = ids.map { gameId in it }

    override suspend fun getWaitlist(): List<WaitlistEntry> {
        if (!loggedIn()) {
            ids.value = persistentSetOf()
            return emptyList()
        }
        val entries = accountSource.getWaitlist()
        ids.value = entries.map { it.gameId }.toImmutableSet()
        return entries
    }

    override suspend fun toggleWaitlist(gameId: String): WaitlistToggleResult {
        if (!loggedIn()) return WaitlistToggleResult.NOT_LOGGED_IN
        if (gameId in ids.value) {
            accountSource.removeFromWaitlist(gameId)
            ids.value = (ids.value - gameId).toImmutableSet()
        } else {
            accountSource.addToWaitlist(gameId)
            ids.value = (ids.value + gameId).toImmutableSet()
        }
        return WaitlistToggleResult.UPDATED
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
