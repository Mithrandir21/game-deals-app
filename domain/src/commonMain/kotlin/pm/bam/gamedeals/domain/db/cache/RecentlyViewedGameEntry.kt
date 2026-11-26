package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a recently-viewed game (#211). Unlike the account-bound id-set tables (waitlist/ignored),
 * this is **local device history**, not ITAD data: it survives logout and needs no remote round-trip.
 * Stores just enough to render a cover tile — [gameId] (ITAD id, the app-wide key), [title], and the
 * [boxart] url — plus [viewedAtEpochMs] for ordering most-recent-first. Capped + de-duplicated by
 * [pm.bam.gamedeals.domain.repositories.recentlyviewed.RecentlyViewedRepository].
 */
@Entity(tableName = "RecentlyViewedGame")
data class RecentlyViewedGameEntry(
    @PrimaryKey val gameId: String,
    val title: String,
    val boxart: String?,
    val viewedAtEpochMs: Long,
)
