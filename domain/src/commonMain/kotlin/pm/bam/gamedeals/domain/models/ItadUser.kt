package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/** The signed-in ITAD user's profile (epic #219, Phase 2 — from `/user/info/v2`). */
@Immutable
data class ItadUser(
    val username: String,
)
