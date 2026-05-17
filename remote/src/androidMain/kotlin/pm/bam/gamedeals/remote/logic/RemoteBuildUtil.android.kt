package pm.bam.gamedeals.remote.logic

// AGP 9's KMP-library plugin doesn't generate BuildConfig per library
// module, so the previous `BuildConfig.BUILD_TYPE` read is gone. Until the
// build type is injected from :app via Koin (where BuildConfig is still
// available because :app is an application module), default to DEBUG so
// local dev keeps HTTP logging on. Trade-off: Android RELEASE builds now
// also log HTTP traffic until the inject-from-:app follow-up lands.
internal actual fun currentBuildType(): RemoteBuildType = RemoteBuildType.DEBUG
