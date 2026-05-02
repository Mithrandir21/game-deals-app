package pm.bam.gamedeals.remote.gamerpower

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.source.GamerPowerSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.gamerpower.mappers.GamerPowerMapperContext
import pm.bam.gamedeals.remote.gamerpower.mappers.toDomain
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure
import javax.inject.Inject

internal class GamerPowerSourceImpl @Inject constructor(
    private val logger: Logger,
    private val gamesApi: GamesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
    private val ctx: GamerPowerMapperContext,
) : GamerPowerSource {

    override suspend fun fetchGiveaways(): List<Giveaway> =
        gamesApi.getAllGames()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toDomain(ctx) }

    private companion object {
        private val TAG: String = GamerPowerSourceImpl::class.simpleName.orEmpty()
    }
}
