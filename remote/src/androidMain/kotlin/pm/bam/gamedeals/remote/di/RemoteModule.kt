package pm.bam.gamedeals.remote.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformerImpl
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.RemoteBuildUtilImpl
import javax.inject.Singleton

@Module(includes = [InternalRemoteModule::class])
@InstallIn(SingletonComponent::class)
class RemoteModule {

    @Provides
    @Singleton
    internal fun provideBuildUtil(): RemoteBuildUtil = RemoteBuildUtilImpl()
}


@Module
@InstallIn(SingletonComponent::class)
internal class InternalRemoteModule {

    @Provides
    @Singleton
    fun provideRemoteExceptionTransformer(): RemoteExceptionTransformer = RemoteExceptionTransformerImpl()
}
