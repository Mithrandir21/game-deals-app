package pm.bam.gamedeals.remote.itad.mappers

import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadRankedGame

/**
 * ITAD ranking entry → domain [RankedGame] (epic #219, Phase 5). The stats endpoints carry no boxart or
 * price, so [RankedGame.boxart] stays null (placeholder) and [RankedGame.priceDenominated] is filled
 * separately by the source via `/games/prices/v3` enrichment.
 */
internal fun RemoteItadRankedGame.toRankedGame(): RankedGame = RankedGame(
    gameId = id,
    title = title,
)
