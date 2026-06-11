package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one game on the user's ITAD waitlist (ITAD caching strategy, Phase 7a, #268).
 *
 * Persists only the **gameId set** — enough to drive the heart state (`observeIsWaitlisted`) instantly on
 * cold start and offline. The display fields (title/boxart) live on the [WaitlistEntry][pm.bam.gamedeals.domain.models.WaitlistEntry]
 * returned by `getWaitlist()` straight from remote; persisting them is deferred until a surface reads the
 * list *from Room* (an offline waitlist screen) rather than from `getWaitlist()`.
 *
 * Login-scoped, not TTL-cached (D8): refreshed remote-as-truth by `getWaitlist()` and cleared on logout.
 */
@Entity(tableName = "WaitlistGameId")
data class WaitlistGameIdEntry(
    @PrimaryKey val gameId: String,
)
