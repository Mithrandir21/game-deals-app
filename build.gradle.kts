// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.compose.stability.analyzer) apply false
    alias(libs.plugins.kotlinx.kover)
}

// Aggregated coverage report. Filters match the per-module set in
// build-logic/convention/.../Kover.kt — keep them in sync.
kover {
    reports {
        filters {
            excludes {
                packages(
                    "*.di",
                    "*.generated.resources",
                )
                classes(
                    "*BuildConfig",
                    "*ComposableSingletons*",
                    "*\$\$serializer",
                )
                annotatedBy("*Generated*")
            }
        }
    }
}

dependencies {
    // Aggregate Kover reports across every module that applies the plugin
    // via a convention (KMP library / Android application). Adding a new
    // module: if it's via :kmp-library or :android-application, list it here
    // and it'll fold into the root koverHtmlReport.
    kover(project(":app"))
    kover(project(":common"))
    kover(project(":common:ui"))
    kover(project(":logging"))
    kover(project(":testing"))
    kover(project(":remote"))
    kover(project(":remote:gamerpower"))
    kover(project(":remote:cheapshark"))
    kover(project(":domain"))
    kover(project(":feature:store"))
    kover(project(":feature:game"))
    kover(project(":feature:search"))
    kover(project(":feature:home"))
    kover(project(":feature:webview"))
    kover(project(":feature:giveaways"))
    kover(project(":feature:favourites"))
}