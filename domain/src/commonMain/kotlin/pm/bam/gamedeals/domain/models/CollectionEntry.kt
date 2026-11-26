package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A game in the user's ITAD collection (epic #219, Phase 2 — from `/collection/games/v1`).
 *
 * [type]/[mature]/[group]/[addedEpochMs] are carried off the list endpoint. [Serializable] so the enriched
 * list can be persisted wholesale as a JSON-blob display cache (no price — collection is games you own).
 * New fields are defaulted so existing constructors keep compiling.
 */
@Immutable
@Serializable
data class CollectionEntry(
    val gameId: String,
    val title: String,
    val artwork: GameArtwork = GameArtwork(),
    val type: String? = null,
    val mature: Boolean = false,
    val group: Int? = null,
    val addedEpochMs: Long? = null,
)
