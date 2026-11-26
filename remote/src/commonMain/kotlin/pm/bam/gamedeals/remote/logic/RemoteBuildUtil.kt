package pm.bam.gamedeals.remote.logic

fun interface RemoteBuildUtil {
    /** Get an enum representing the [RemoteBuildType] of this class. */
    fun buildType(): RemoteBuildType
}

internal class RemoteBuildUtilImpl(private val type: RemoteBuildType) : RemoteBuildUtil {
    override fun buildType(): RemoteBuildType = type
}

enum class RemoteBuildType(val buildTypeName: String) {
    DEBUG("debug"),
    RELEASE("release")
}
