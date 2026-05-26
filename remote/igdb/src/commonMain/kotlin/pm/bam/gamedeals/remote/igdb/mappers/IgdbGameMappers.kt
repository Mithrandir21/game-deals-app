package pm.bam.gamedeals.remote.igdb.mappers

import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame

internal fun RemoteIgdbGame.toIgdbGame(): IgdbGame = IgdbGame(
    id = id,
    name = name,
    summary = summary,
)
