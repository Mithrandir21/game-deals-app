plugins {
    `kotlin-dsl`
}

group = "pm.bam.gamedeals.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.compose.stability.analyzer.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.compose.multiplatform.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "pm.bam.gamedeals.android.application"
            implementationClass = "pm.bam.gamedeals.AndroidApplicationConventionPlugin"
        }
        register("kmpLibrary") {
            id = "pm.bam.gamedeals.kmp.library"
            implementationClass = "pm.bam.gamedeals.KotlinMultiplatformLibraryConventionPlugin"
        }
        register("kmpLibraryCompose") {
            id = "pm.bam.gamedeals.kmp.library.compose"
            implementationClass = "pm.bam.gamedeals.KotlinMultiplatformLibraryComposeConventionPlugin"
        }
        register("kmpKsp") {
            id = "pm.bam.gamedeals.kmp.ksp"
            implementationClass = "pm.bam.gamedeals.KotlinMultiplatformKspConventionPlugin"
        }
        register("kmpFeature") {
            id = "pm.bam.gamedeals.kmp.feature"
            implementationClass = "pm.bam.gamedeals.KotlinMultiplatformFeatureConventionPlugin"
        }
        register("rootCoverage") {
            id = "pm.bam.gamedeals.coverage.root"
            implementationClass = "pm.bam.gamedeals.RootCoverageConventionPlugin"
        }
    }
}
