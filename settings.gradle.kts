

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Game Deals"

include(":app")
include(":iosApp")
include(":common")
include(":common:ui")
include(":logging")
include(":testing")
include(":remote")
include(":remote:gamerpower")
include(":remote:igdb")
include(":remote:itad")
include(":domain")

include(":feature:store")
include(":feature:game")
include(":feature:search")
include(":feature:home")
include(":feature:webview")
include(":feature:giveaways")
include(":feature:settings")
include(":feature:bundles")
include(":feature:account")
include(":feature:deals")

// Macrobenchmark + Baseline Profile producer (com.android.test). Targets :app, generates the app's
// baseline profile, and holds the Home-scroll FrameTimingMetric benchmark. Not shipped in the APK.
include(":baselineprofile")
