package pm.bam.gamedeals.remote.igdb

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.source.IgdbSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.mappers.toIgdbGame
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

internal class IgdbSourceImpl(
    private val logger: Logger,
    private val igdbGamesApi: IgdbGamesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
) : IgdbSource {

    override suspend fun fetchSampleGames(): List<IgdbGame> =
        igdbGamesApi.sampleGames()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toIgdbGame() }

    private companion object {
        private val TAG: String = IgdbSourceImpl::class.simpleName.orEmpty()
    }
}
