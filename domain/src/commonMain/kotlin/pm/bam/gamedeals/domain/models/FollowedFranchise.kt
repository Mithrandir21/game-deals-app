package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A franchise/series the user follows (#7). Stored locally since following is a client-side affordance
 * layered on IGDB's `franchises`. [franchiseId] is the IGDB franchise id; [name]
 * is kept so the followed list renders without re-fetching.
 */
@Immutable
@Serializable
data class FollowedFranchise(
    val franchiseId: Long,
    val name: String,
    val addedAtMs: Long,
)
