package pm.bam.gamedeals.remote.igdb

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.source.IgdbSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.mappers.toIgdbGameOrNull
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

internal class IgdbSourceImpl(
    private val logger: Logger,
    private val igdbGamesApi: IgdbGamesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
) : IgdbSource {

    override suspend fun fetchGameBySteamId(steamId: Int): IgdbGame? =
        igdbGamesApi.fetchGameBySteamId(steamId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    override suspend fun fetchGameDetailsBySteamId(steamId: Int): IgdbGame? =
        igdbGamesApi.fetchGameDetailsBySteamId(steamId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    override suspend fun fetchGameDetailsByIgdbId(igdbGameId: Long): IgdbGame? =
        igdbGamesApi.fetchGameDetailsByIgdbId(igdbGameId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    override suspend fun fetchGameDetailsByTitle(title: String): IgdbGame? {
        if (title.isBlank()) return null

        val exact = igdbGamesApi.fetchGameDetailsByExactName(title)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()
        if (exact != null) return exact

        return igdbGamesApi.fetchGameDetailsBySearch(title)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()
    }

    private companion object {
        private val TAG: String = IgdbSourceImpl::class.simpleName.orEmpty()
    }
}
