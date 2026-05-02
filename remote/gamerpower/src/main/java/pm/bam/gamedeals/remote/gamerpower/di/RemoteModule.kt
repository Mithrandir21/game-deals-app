package pm.bam.gamedeals.remote.gamerpower.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.domain.source.GamerPowerSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.gamerpower.GamerPowerSourceImpl
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.gamerpower.mappers.GamerPowerMapperContext
import javax.inject.Singleton

@Module(includes = [RemoteNetworkModule::class, InternalRemoteModule::class])
@InstallIn(SingletonComponent::class)
class RemoteModule


@Module
@InstallIn(SingletonComponent::class)
internal class InternalRemoteModule {

    @Provides
    @Singleton
    fun provideGamerPowerMapperContext(
        dates: DatetimeParsing,
    ): GamerPowerMapperContext = GamerPowerMapperContext(dates)

    @Provides
    @Singleton
    fun provideGamerPowerSource(
        logger: Logger,
        gamesApi: GamesApi,
        remoteExceptionTransformer: RemoteExceptionTransformer,
        ctx: GamerPowerMapperContext,
    ): GamerPowerSource =
        GamerPowerSourceImpl(logger, gamesApi, remoteExceptionTransformer, ctx)
}
