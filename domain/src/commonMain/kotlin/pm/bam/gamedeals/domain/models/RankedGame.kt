package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A game in an ITAD global ranking — most-waitlisted / most-collected / most-popular (epic #219,
 * Phase 5). The ranking endpoints return game ids; [priceDenominated] is enriched separately from the
 * current best deal (`/games/prices/v3`) and is null until that enrichment lands.
 */
@Immutable
data class RankedGame(
    val gameId: String,
    val title: String,
    val boxart: String? = null,
    val priceDenominated: String? = null,
)
