package pm.bam.gamedeals.remote.gamerpower

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveaway
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure
import javax.inject.Inject

internal class GamerPowerSourceImpl @Inject constructor(
    private val logger: Logger,
    private val gamesApi: GamesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer
) : GamerPowerSource {

    override suspend fun fetchGiveaways(): List<RemoteGiveaway> =
        gamesApi.getAllGames()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    private companion object {
        private val TAG: String = GamerPowerSourceImpl::class.simpleName.orEmpty()
    }
}
