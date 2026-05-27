package pm.bam.gamedeals.remote.igdb.mappers

import kotlin.jvm.JvmName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.remote.igdb.models.RemoteExternalGameLookup
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame

internal fun List<RemoteExternalGameLookup>.toIgdbGameOrNull(): IgdbGame? =
    firstOrNull()?.game?.toIgdbGame()

@JvmName("toIgdbGameOrNullFromGames")
internal fun List<RemoteIgdbGame>.toIgdbGameOrNull(): IgdbGame? =
    firstOrNull()?.toIgdbGame()

internal fun List<RemoteIgdbGame>.toIgdbCandidateList(): ImmutableList<IgdbGame.IgdbSimilarGame> =
    map { IgdbGame.IgdbSimilarGame(id = it.id, name = it.name, coverImageId = it.cover?.imageId) }
        .toImmutableList()
