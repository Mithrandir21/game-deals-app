package pm.bam.gamedeals.remote.logic

import pm.bam.gamedeals.remote.BuildConfig

internal actual fun currentBuildType(): RemoteBuildType =
    when (val type = BuildConfig.BUILD_TYPE) {
        RemoteBuildType.DEBUG.buildTypeName -> RemoteBuildType.DEBUG
        RemoteBuildType.RELEASE.buildTypeName -> RemoteBuildType.RELEASE
        else -> throw RuntimeException("Unexpected build type: $type")
    }
