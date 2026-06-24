package pm.bam.gamedeals.domain.repositories.ignored

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.cache.IgnoredGameIdEntry
import pm.bam.gamedeals.domain.db.dao.IgnoredDao
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * The user's ITAD ignore list (epic #272, P3 #279). A 1:1 mirror of
 * [WaitlistRepository][pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository]: backed by
 * [ItadAccountSource] (remote) over a Room-persisted **gameId set** ([IgnoredDao]) so [observeIgnoredIds]
 * is correct instantly on cold start and offline — which the Deals/Search filter (#280) reads. [getIgnored]
 * is the remote-as-truth reconcile; [toggleIgnored] is **remote-first** (await the ITAD add/remove, then
 * update Room). The observed set is auth-gated — empty whenever logged out — and cleared on logout. All
 * writes are login-gated.
 */
interface IgnoredRepository {
    fun observeIgnoredIds(): Flow<ImmutableSet<String>>
    fun observeIsIgnored(gameId: String): Flow<Boolean>
    suspend fun getIgnored(): List<IgnoredEntry>

    /** Wipes the locally-cached id set (no remote call) — used to clear the row on logout. */
    suspend fun clearLocal()

    /**
     * Adds/removes [gameId] on the user's ITAD ignore list, returning [RepoUpdateResult]. When logged
     * out this is a no-op and returns [RepoUpdateResult.NOT_LOGGED_IN] so the UI can route to sign in.
     */
    suspend fun toggleIgnored(gameId: String): RepoUpdateResult
}

internal class IgnoredRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val ignoredDao: IgnoredDao,
    private val analytics: Analytics,
) : IgnoredRepository {

    override fun observeIgnoredIds(): Flow<ImmutableSet<String>> =
        combine(authTokenStore.observeAuthState(), ignoredDao.observeAll()) { authState, rows ->
            if (authState is AuthState.LoggedIn) rows.map { it.gameId }.toImmutableSet() else persistentSetOf()
        }

    override fun observeIsIgnored(gameId: String): Flow<Boolean> =
        observeIgnoredIds().map { gameId in it }

    override suspend fun getIgnored(): List<IgnoredEntry> {
        if (!loggedIn()) {
            ignoredDao.clear()
            return emptyList()
        }
        val entries = accountSource.getIgnored()
        ignoredDao.replaceAll(entries.map { it.gameId })
        return entries
    }

    override suspend fun clearLocal() = ignoredDao.clear()

    override suspend fun toggleIgnored(gameId: String): RepoUpdateResult {
        if (!loggedIn()) return RepoUpdateResult.NOT_LOGGED_IN
        // Remote-first: confirm the ITAD write before mutating Room, so the cache can't drift from remote.
        if (ignoredDao.contains(gameId)) {
            accountSource.removeFromIgnored(gameId)
            ignoredDao.delete(gameId)
            analytics.capture(AnalyticsEvents.IGNORED_REMOVED, mapOf("game_id" to gameId))
        } else {
            accountSource.addToIgnored(gameId)
            ignoredDao.add(IgnoredGameIdEntry(gameId))
            analytics.capture(AnalyticsEvents.IGNORED_ADDED, mapOf("game_id" to gameId))
        }
        return RepoUpdateResult.UPDATED
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
