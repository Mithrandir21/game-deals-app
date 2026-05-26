package pm.bam.gamedeals.remote.igdb.mappers

import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.remote.igdb.models.RemoteExternalGameLookup

internal fun List<RemoteExternalGameLookup>.toIgdbGameOrNull(): IgdbGame? =
    firstOrNull()?.game?.toIgdbGame()
