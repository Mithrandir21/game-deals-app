package pm.bam.gamedeals.remote.logic

fun interface RemoteBuildUtil {
    /** Get an enum representing the [RemoteBuildType] of this class. */
    fun buildType(): RemoteBuildType
}

internal class RemoteBuildUtilImpl : RemoteBuildUtil {
    override fun buildType(): RemoteBuildType = currentBuildType()
}

enum class RemoteBuildType(val buildTypeName: String) {
    DEBUG("debug"),
    RELEASE("release")
}

/**
 * Platform-specific build-type lookup. Android reads `BuildConfig.BUILD_TYPE`;
 * iOS hardcodes DEBUG until a build-time flag wires through Xcode's
 * configuration name.
 */
internal expect fun currentBuildType(): RemoteBuildType
