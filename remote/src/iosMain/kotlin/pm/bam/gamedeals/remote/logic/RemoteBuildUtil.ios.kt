package pm.bam.gamedeals.remote.logic

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Maps Xcode's Debug vs Release configuration to our [RemoteBuildType] using
 * `Platform.isDebugBinary`, which reflects the `-g` debug-info flag — Xcode
 * passes `-g` for Debug builds and strips it for Release. This avoids
 * threading Xcode's `CONFIGURATION` environment variable through Gradle and
 * the Kotlin/Native compile step.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun currentBuildType(): RemoteBuildType =
    if (Platform.isDebugBinary) RemoteBuildType.DEBUG else RemoteBuildType.RELEASE
