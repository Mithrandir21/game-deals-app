package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A game in an ITAD global ranking — most-waitlisted / most-collected / most-popular (epic #219,
 * Phase 5). The ranking endpoints return game ids; [priceDenominated] is enriched separately from the
 * current best deal (`/games/prices/v3`) and is null until that enrichment lands.
 *
 * Serialized into the `StatsRankingsCache` feed blob (ITAD caching strategy, Phase 5c, #266) — the
 * enriched price is **snapshotted** here at the 12h feed TTL rather than referencing a separate price
 * cache (a ranking-tile price may be up to 12h stale, acceptable off the transact surface).
 */
@Immutable
@Serializable
data class RankedGame(
    val gameId: String,
    val title: String,
    val boxart: String? = null,
    val priceDenominated: String? = null,
)
