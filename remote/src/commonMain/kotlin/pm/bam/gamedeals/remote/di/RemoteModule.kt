package pm.bam.gamedeals.remote.di

import org.koin.dsl.module
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformerImpl
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.RemoteBuildUtilImpl

fun remoteModule(buildType: RemoteBuildType) = module {
    single<RemoteBuildUtil> { RemoteBuildUtilImpl(buildType) }
    single<RemoteExceptionTransformer> { RemoteExceptionTransformerImpl() }
}
