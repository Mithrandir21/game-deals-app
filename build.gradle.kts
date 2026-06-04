import org.gradle.testing.jacoco.tasks.JacocoReport
import pm.bam.gamedeals.CoverageFilters

// `apply false` entries establish each plugin's version on the build classpath without applying them at root — module and convention scripts then apply by
// id without repeating the version. Don't add `org.jetbrains.kotlin.android` back; AGP 9's built-in Kotlin support replaces it (applying both would clash).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.compose.stability.analyzer) apply false
    alias(libs.plugins.gamedeals.coverage.root)
}

// Kover aggregator: every module that applies the kover convention contributes here. When adding a new module via :kmp-library / :android-application,
// list it here and it folds into the root `koverHtmlReport`.
dependencies {
    kover(project(":app"))
    kover(project(":common"))
    kover(project(":common:ui"))
    kover(project(":logging"))
    kover(project(":testing"))
    kover(project(":remote"))
    kover(project(":remote:gamerpower"))
    kover(project(":remote:itad"))
    kover(project(":domain"))
    kover(project(":feature:store"))
    kover(project(":feature:game"))
    kover(project(":feature:search"))
    kover(project(":feature:home"))
    kover(project(":feature:webview"))
    kover(project(":feature:giveaways"))
    kover(project(":feature:favourites"))
    kover(project(":feature:settings"))
    kover(project(":feature:bundles"))
    kover(project(":feature:account"))
}

// Modules whose connectedAndroidDeviceTest / connectedDebugAndroidTest outputs feed the parallel JaCoCo report. Add a module here when it gains an
// `androidDeviceTest` (or `androidTest` for `:app`) source set.
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
                // JaCoCo refuses already-instrumented bytecode. AGP-9 keeps *instrumented* copies at `intermediates/classes/debug/jacocoDebug/dirs/`
                // (for `:app`) and `.transforms/.../transformed/instrumented_classes/` (for KMP-library). The original bytecode the report needs lives
                // at the compiler outputs below.
                include(
                    // :app (com.android.application) — Kotlin compile output, pre-instrumentation.
                    "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/**/*.class",
                    // :app — Java compile output, pre-instrumentation.
                    "intermediates/javac/debug/compileDebugJavaWithJavac/classes/**/*.class",
                    // KMP-library Android target — Kotlin compile output.
                    "classes/kotlin/android/main/**/*.class",
                )
                exclude(CoverageFilters.jacocoPathGlobs)
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
