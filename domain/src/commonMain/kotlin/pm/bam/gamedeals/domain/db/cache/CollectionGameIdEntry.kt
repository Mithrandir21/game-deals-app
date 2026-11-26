package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one game in the user's ITAD collection (ITAD caching strategy, Phase 7a, #268).
 *
 * Persists only the **gameId set** (see [WaitlistGameIdEntry] for the rationale) — enough to drive the
 * collected state (`observeIsCollected`) instantly on cold start and offline. Login-scoped, not
 * TTL-cached (D8): refreshed remote-as-truth by `getCollection()` and cleared on logout.
 */
@Entity(tableName = "CollectionGameId")
data class CollectionGameIdEntry(
    @PrimaryKey val gameId: String,
)
