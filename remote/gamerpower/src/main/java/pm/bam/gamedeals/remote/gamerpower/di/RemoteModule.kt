package pm.bam.gamedeals.remote.gamerpower.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.gamerpower.GamerPowerSource
import pm.bam.gamedeals.remote.gamerpower.GamerPowerSourceImpl
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import javax.inject.Singleton

@Module(includes = [RemoteNetworkModule::class, InternalRemoteModule::class])
@InstallIn(SingletonComponent::class)
class RemoteModule


@Module
@InstallIn(SingletonComponent::class)
internal class InternalRemoteModule {

    @Provides
    @Singleton
    fun provideGamerPowerSource(
        logger: Logger,
        gamesApi: GamesApi,
        remoteExceptionTransformer: RemoteExceptionTransformer
    ): GamerPowerSource =
        GamerPowerSourceImpl(logger, gamesApi, remoteExceptionTransformer)
}
