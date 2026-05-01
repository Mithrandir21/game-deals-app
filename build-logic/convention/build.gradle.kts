plugins {
    `kotlin-dsl`
}

group = "pm.bam.gamedeals.buildlogic"

// Match the JDK target used by the rest of the project so the precompiled
// plugin classes link against JDK 21 bytecode and avoid JavaCompile mismatches
// on CI (see lesson L-2026-04-30-02).
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
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "pm.bam.gamedeals.android.library"
            implementationClass = "pm.bam.gamedeals.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "pm.bam.gamedeals.android.library.compose"
            implementationClass = "pm.bam.gamedeals.AndroidLibraryComposeConventionPlugin"
        }
        register("androidKsp") {
            id = "pm.bam.gamedeals.android.ksp"
            implementationClass = "pm.bam.gamedeals.AndroidKspConventionPlugin"
        }
        register("androidFeature") {
            id = "pm.bam.gamedeals.android.feature"
            implementationClass = "pm.bam.gamedeals.AndroidFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = "pm.bam.gamedeals.android.application"
            implementationClass = "pm.bam.gamedeals.AndroidApplicationConventionPlugin"
        }
    }
}
