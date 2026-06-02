plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "pm.bam.gamedeals.iosApp")
            // Re-export :domain so Swift sees Koin module accessors directly.
            export(project(":domain"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil3)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
            api(project(":domain"))
            implementation(project(":common"))
            implementation(project(":common:ui"))
            implementation(project(":logging"))
            implementation(project(":remote"))
            implementation(project(":remote:cheapshark"))
            implementation(project(":remote:gamerpower"))
            implementation(project(":remote:igdb"))
            implementation(project(":remote:itad"))
            implementation(libs.androidx.compose.navigation)
            implementation(project(":feature:home"))
            implementation(project(":feature:search"))
            implementation(project(":feature:game"))
            implementation(project(":feature:giveaways"))
            implementation(project(":feature:favourites"))
            implementation(project(":feature:store"))
            implementation(project(":feature:webview"))
        }
    }
}
