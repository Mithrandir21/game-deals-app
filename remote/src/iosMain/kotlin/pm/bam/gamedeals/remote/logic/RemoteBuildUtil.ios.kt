package pm.bam.gamedeals.remote.logic

// First-run iOS hardcodes DEBUG. Phase-7 polish: thread an Xcode
// configuration name through via xcconfig + Kotlin/Native compile flag.
internal actual fun currentBuildType(): RemoteBuildType = RemoteBuildType.DEBUG
