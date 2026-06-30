package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A game on the user's ITAD waitlist (epic #219, Phase 2 — from `/waitlist/games/v1`).
 *
 * [type]/[mature]/[addedEpochMs] are carried off the list endpoint (the GET response includes an `added`
 * ISO timestamp). New fields are defaulted so existing constructors (recommendations, lifecycle, tests)
 * keep compiling. Price is NOT on this endpoint — the display layer merges it from a batched price call.
 */
@Immutable
data class WaitlistEntry(
    val gameId: String,
    val title: String,
    val artwork: GameArtwork = GameArtwork(),
    val type: String? = null,
    val mature: Boolean = false,
    val addedEpochMs: Long? = null,
)
