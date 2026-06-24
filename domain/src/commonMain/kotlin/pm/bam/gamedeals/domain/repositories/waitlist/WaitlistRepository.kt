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
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

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
interface WaitlistRepository {
    fun observeWaitlistIds(): Flow<ImmutableSet<String>>
    fun observeIsWaitlisted(gameId: String): Flow<Boolean>
    suspend fun getWaitlist(): List<WaitlistEntry>

    /** Wipes the locally-cached id set (no remote call) — used to clear the row on logout. */
    suspend fun clearLocal()

    /**
     * Adds/removes [gameId] on the user's ITAD waitlist, returning [RepoUpdateResult]. When logged
     * out this is a no-op and returns [RepoUpdateResult.NOT_LOGGED_IN] so the UI can route the user
     * to sign in (the heart is login-gated — there is no local waitlist).
     */
    suspend fun toggleWaitlist(gameId: String): RepoUpdateResult
}

internal class WaitlistRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val waitlistDao: WaitlistDao,
    private val analytics: Analytics,
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

    override suspend fun clearLocal() = waitlistDao.clear()

    override suspend fun toggleWaitlist(gameId: String): RepoUpdateResult {
        if (!loggedIn()) return RepoUpdateResult.NOT_LOGGED_IN
        // Remote-first: confirm the ITAD write before mutating Room, so the cache can't drift from remote.
        if (waitlistDao.contains(gameId)) {
            accountSource.removeFromWaitlist(gameId)
            waitlistDao.delete(gameId)
            // Recorded only after the remote+local write succeeds, so failed toggles aren't counted. The base
            // props (environment/app_version) are merged by the Analytics impl.
            analytics.capture(AnalyticsEvents.WAITLIST_REMOVED, mapOf("game_id" to gameId))
        } else {
            accountSource.addToWaitlist(gameId)
            waitlistDao.add(WaitlistGameIdEntry(gameId))
            analytics.capture(AnalyticsEvents.WAITLIST_ADDED, mapOf("game_id" to gameId))
        }
        return RepoUpdateResult.UPDATED
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
