package pm.bam.gamedeals.domain.repositories.waitlist

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.cache.WaitlistGameIdEntry
import pm.bam.gamedeals.domain.db.dao.WaitlistDao
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource

/**
 * The user's ITAD waitlist (epic #219). Replaces the removed local Favourites (Phase 3); the heart is
 * login-gated and writes here. Mirrors the old `FavouritesRepository` shape so call sites swap cleanly.
 *
 * Backed by [ItadAccountSource] (remote) over a Room-persisted **gameId set** ([WaitlistDao]) for reactive
 * UI (ITAD caching strategy, Phase 7a, #268): the id set survives process death, so the heart state is
 * correct **instantly on cold start and offline** instead of empty-until-refresh. [getWaitlist] is the
 * remote-as-truth reconcile (replaces the cached id set); [toggleWaitlist] is **remote-first** (await the
 * ITAD add/remove, then update Room) so Room never holds an unconfirmed edit and the reconcile is always
 * safe. The observed set is auth-gated — empty whenever logged out — and the rows are cleared on logout.
 * All writes are login-gated.
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
    private val waitlistDao: WaitlistDao,
) : WaitlistRepository {

    // Auth-gated so the persisted id set is never surfaced while logged out (it is also cleared on logout,
    // but this guards the window before a reconcile and any cross-account switch).
    override fun observeWaitlistIds(): Flow<ImmutableSet<String>> =
        combine(authTokenStore.observeAuthState(), waitlistDao.observeAll()) { authState, rows ->
            if (authState is AuthState.LoggedIn) rows.map { it.gameId }.toImmutableSet() else persistentSetOf()
        }

    override fun observeIsWaitlisted(gameId: String): Flow<Boolean> =
        observeWaitlistIds().map { gameId in it }

    override suspend fun getWaitlist(): List<WaitlistEntry> {
        if (!loggedIn()) {
            waitlistDao.clear()
            return emptyList()
        }
        val entries = accountSource.getWaitlist()
        waitlistDao.replaceAll(entries.map { it.gameId })
        return entries
    }

    override suspend fun toggleWaitlist(gameId: String): WaitlistToggleResult {
        if (!loggedIn()) return WaitlistToggleResult.NOT_LOGGED_IN
        // Remote-first: confirm the ITAD write before mutating Room, so the cache can't drift from remote.
        if (waitlistDao.contains(gameId)) {
            accountSource.removeFromWaitlist(gameId)
            waitlistDao.delete(gameId)
        } else {
            accountSource.addToWaitlist(gameId)
            waitlistDao.add(WaitlistGameIdEntry(gameId))
        }
        return WaitlistToggleResult.UPDATED
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
