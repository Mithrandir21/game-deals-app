package pm.bam.gamedeals.remote.igdb.mappers

import kotlin.jvm.JvmName
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.remote.igdb.models.RemoteExternalGameLookup
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame

internal fun List<RemoteExternalGameLookup>.toIgdbGameOrNull(): IgdbGame? =
    firstOrNull()?.game?.toIgdbGame()

@JvmName("toIgdbGameOrNullFromGames")
internal fun List<RemoteIgdbGame>.toIgdbGameOrNull(): IgdbGame? =
    firstOrNull()?.toIgdbGame()
