package pm.bam.gamedeals.domain.repositories.recentlyviewed

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.RecentlyViewedGameEntry
import pm.bam.gamedeals.domain.db.dao.RecentlyViewedDao
import pm.bam.gamedeals.domain.models.RecentlyViewedGame
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/** How many distinct games the recently-viewed history keeps (#211). Older entries are trimmed. */
internal const val RECENTLY_VIEWED_CAP = 20

/**
 * Local recently-viewed games history (#211). Unlike the ITAD-account lists, this is **device-local** and
 * not auth-gated: it is recorded when a full game page opens, survives logout, and is read directly from
 * Room (no remote). The list is capped at [RECENTLY_VIEWED_CAP] and de-duplicated by gameId — re-viewing a
 * game moves it back to the top. Surfaced as a carousel on Home and Deals.
 */
interface RecentlyViewedRepository {
    /** Most-recent-first, capped to [RECENTLY_VIEWED_CAP]. */
    fun observeRecentlyViewed(): Flow<ImmutableList<RecentlyViewedGame>>

    /** Records a game view (upsert + cap-trim). De-dupes on gameId; a re-view moves it to the top. */
    suspend fun recordView(gameId: String, title: String, boxart: String?)

    /** Removes a single game from the history (per-item swipe/long-press). */
    suspend fun remove(gameId: String)

    /** Clears the whole history. */
    suspend fun clear()
}

internal class RecentlyViewedRepositoryImpl(
    private val recentlyViewedDao: RecentlyViewedDao,
    private val clock: Clock,
    private val analytics: Analytics,
) : RecentlyViewedRepository {

    override fun observeRecentlyViewed(): Flow<ImmutableList<RecentlyViewedGame>> =
        recentlyViewedDao.observeRecent(RECENTLY_VIEWED_CAP)
            .map { rows -> rows.map { it.toRecentlyViewedGame() }.toImmutableList() }

    override suspend fun recordView(gameId: String, title: String, boxart: String?) {
        if (gameId.isBlank()) return
        recentlyViewedDao.upsert(
            RecentlyViewedGameEntry(
                gameId = gameId,
                title = title,
                boxart = boxart,
                viewedAtEpochMs = clock.nowMillis(),
            )
        )
        recentlyViewedDao.trimTo(RECENTLY_VIEWED_CAP)
    }

    override suspend fun remove(gameId: String) {
        recentlyViewedDao.delete(gameId)
        analytics.capture(AnalyticsEvents.RECENTLY_VIEWED_CLEARED, mapOf("scope" to "one"))
    }

    override suspend fun clear() {
        recentlyViewedDao.clear()
        analytics.capture(AnalyticsEvents.RECENTLY_VIEWED_CLEARED, mapOf("scope" to "all"))
    }
}

private fun RecentlyViewedGameEntry.toRecentlyViewedGame() =
    RecentlyViewedGame(gameId = gameId, title = title, boxart = boxart)
