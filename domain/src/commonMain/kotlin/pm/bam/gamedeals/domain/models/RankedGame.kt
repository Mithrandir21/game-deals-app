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
 *
 * [cutPercent], [regularPriceDenominated], [storeName] and the [hasVoucher] / [isNewHistoricalLow] /
 * [isStoreLow] flags are all enriched off the best current deal (`/games/prices/v3`) alongside
 * [priceDenominated], so the Home ranked rows render with the exact same anatomy as the Trending deal
 * rows — store label, struck regular price, discount + flag badges (one row look everywhere). Every
 * enriched field carries a default so old cached blobs (written before the field existed) still decode.
 */
@Immutable
@Serializable
data class RankedGame(
    val gameId: String,
    val title: String,
    val boxart: String? = null,
    val priceDenominated: String? = null,
    val cutPercent: Int? = null,
    val regularPriceDenominated: String? = null,
    val storeName: String? = null,
    val hasVoucher: Boolean = false,
    val isNewHistoricalLow: Boolean = false,
    val isStoreLow: Boolean = false,
)
