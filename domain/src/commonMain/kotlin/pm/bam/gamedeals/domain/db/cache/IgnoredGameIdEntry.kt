package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one game on the user's ITAD ignore list (epic #272, P3 #279). Mirrors
 * [WaitlistGameIdEntry]: persists only the **gameId set** — enough to filter ignored games out of
 * Deals/Search instantly on cold start and offline (#280), without a remote round-trip.
 *
 * Login-scoped, not TTL-cached: refreshed remote-as-truth by `getIgnored()` and cleared on logout.
 */
@Entity(tableName = "IgnoredGameId")
data class IgnoredGameIdEntry(
    @PrimaryKey val gameId: String,
)
