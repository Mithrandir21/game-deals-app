package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "Release")
@Immutable
@Serializable
data class Release(
    @PrimaryKey
    @SerialName("title")
    val title: String,
    @SerialName("date")
    val date: Int,
    @SerialName("image")
    val image: String,

    /**
     * Epoch-millisecond expiry stamp written when the entity is persisted by the repository.
     *
     * Stamped via the injected `Clock` plus the resource's TTL on refresh (ITAD caching strategy,
     * Phase 1 — TTL-gate). Persisted with SQL `DEFAULT 0` (already-expired), which backs the
     * v10→v11 `ADD COLUMN` migration: older cached rows are treated as stale and refetch once.
     */
    @SerialName("expires")
    @ColumnInfo(defaultValue = "0")
    val expires: Long = 0L,
)
