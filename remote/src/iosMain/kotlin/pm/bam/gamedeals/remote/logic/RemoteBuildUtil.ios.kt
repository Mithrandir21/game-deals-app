package pm.bam.gamedeals.remote.logic

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentBuildType(): RemoteBuildType =
    if (Platform.isDebugBinary) RemoteBuildType.DEBUG else RemoteBuildType.RELEASE
