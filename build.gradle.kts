import org.gradle.testing.jacoco.tasks.JacocoReport

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.compose.stability.analyzer) apply false
    alias(libs.plugins.kotlinx.kover)
    jacoco
}

jacoco {
    toolVersion = "0.8.13"
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

// Modules whose connectedAndroidDeviceTest / connectedDebugAndroidTest
// outputs feed the parallel JaCoCo report. Add a module here when it
// gains an `androidDeviceTest` (or `androidTest` for `:app`) source set.
val jacocoCoveredModulePaths = listOf(
    ":app",
    ":common:ui",
    ":feature:home",
    ":feature:store",
    ":feature:game",
    ":feature:search",
    ":feature:giveaways",
    ":feature:webview",
)

// JaCoCo path-based excludes — keep aligned with the Kover filter set in
// Kover.kt + the `kover {}` block above. Kover uses class-FQN wildcards,
// JaCoCo uses Ant-style globs against .class file paths.
val jacocoClassExcludes = listOf(
    "**/di/**",
    "**/generated/resources/**",
    "**/*BuildConfig*",
    "**/*ComposableSingletons*",
    "**/*\$\$serializer*",
    "**/R.class",
    "**/R\$*.class",
    "**/Manifest*.*",
)

tasks.register<JacocoReport>("jacocoAndroidTestReport") {
    group = "verification"
    description = "Aggregate JaCoCo coverage from `connectedAndroidDeviceTest` " +
        "(KMP-library modules) and `connectedDebugAndroidTest` (:app) `.ec` files. " +
        "Independent of Kover (which covers host tests at build/reports/kover/). " +
        "Run after the connected test tasks finish."

    val coveredProjects = jacocoCoveredModulePaths.mapNotNull { rootProject.findProject(it) }

    executionData.setFrom(
        files(coveredProjects.map { p ->
            p.fileTree(p.layout.buildDirectory.dir("outputs/code_coverage")) {
                include("**/*.ec")
            }
        })
    )

    classDirectories.setFrom(
        files(coveredProjects.map { p ->
            p.fileTree(p.layout.buildDirectory) {
                // JaCoCo refuses already-instrumented bytecode. AGP-9 keeps
                // *instrumented* copies at `intermediates/classes/debug/jacocoDebug/dirs/`
                // (for `:app`) and `.transforms/.../transformed/instrumented_classes/`
                // (for KMP-library). The original bytecode the report needs
                // lives at the compiler outputs below.
                include(
                    // :app (com.android.application) — Kotlin compile output, pre-instrumentation.
                    "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/**/*.class",
                    // :app — Java compile output, pre-instrumentation.
                    "intermediates/javac/debug/compileDebugJavaWithJavac/classes/**/*.class",
                    // KMP-library Android target — Kotlin compile output.
                    "classes/kotlin/android/main/**/*.class",
                )
                exclude(jacocoClassExcludes)
            }
        })
    )

    sourceDirectories.setFrom(
        files(coveredProjects.flatMap { p ->
            listOf(
                p.file("src/main/java"),
                p.file("src/main/kotlin"),
                p.file("src/commonMain/kotlin"),
                p.file("src/androidMain/kotlin"),
            )
        })
    )

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/androidTest/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/androidTest/report.xml"))
    }
}