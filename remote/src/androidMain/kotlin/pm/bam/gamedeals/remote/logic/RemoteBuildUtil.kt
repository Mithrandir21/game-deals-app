package pm.bam.gamedeals.remote.logic

import pm.bam.gamedeals.remote.BuildConfig
import javax.inject.Inject

fun interface RemoteBuildUtil {
    /** Get an enum representing the [RemoteBuildType] of this class. */
    fun buildType(): RemoteBuildType
}

internal class RemoteBuildUtilImpl @Inject constructor() : RemoteBuildUtil {

    /** Get an enum representing the [RemoteBuildType] of this class. */
    override fun buildType(): RemoteBuildType =
        when (val type = BuildConfig.BUILD_TYPE) {
            RemoteBuildType.DEBUG.buildTypeName -> RemoteBuildType.DEBUG
            RemoteBuildType.RELEASE.buildTypeName -> RemoteBuildType.RELEASE
            else -> throw RuntimeException("Unexpected build type: $type")
        }
}

enum class RemoteBuildType(val buildTypeName: String) {
    DEBUG("debug"),
    RELEASE("release")
}