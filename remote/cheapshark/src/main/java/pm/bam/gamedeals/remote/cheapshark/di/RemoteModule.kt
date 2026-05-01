package pm.bam.gamedeals.remote.cheapshark.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSource
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSourceImpl
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import javax.inject.Singleton

@Module(includes = [RemoteNetworkModule::class, InternalRemoteModule::class])
@InstallIn(SingletonComponent::class)
class RemoteModule


@Module
@InstallIn(SingletonComponent::class)
internal class InternalRemoteModule {

    @Provides
    @Singleton
    fun provideCheapsharkSource(
        logger: Logger,
        dealsApi: DealsApi,
        gamesApi: GamesApi,
        releaseApi: ReleaseApi,
        storesApi: StoresApi,
        remoteExceptionTransformer: RemoteExceptionTransformer
    ): CheapsharkSource =
        CheapsharkSourceImpl(logger, dealsApi, gamesApi, releaseApi, storesApi, remoteExceptionTransformer)
}
