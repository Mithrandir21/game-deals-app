import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx)
            implementation(libs.coroutines)

            // :remote api-exposes ktor-client-core + content-negotiation + logging +
            // ktor-serialization-kotlinx-json + sandwich-ktor for downstream use.
            api(project(":remote"))

            implementation(libs.koin.core)

            implementation(project(":logging"))
            implementation(project(":common"))
            implementation(project(":domain"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing"))
            implementation(libs.coroutines.testing)
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.remote.gamerpower"
}
