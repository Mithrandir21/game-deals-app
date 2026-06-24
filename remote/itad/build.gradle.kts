plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx)
            implementation(libs.coroutines)
            implementation(libs.kotlinx.collections.immutable)

            // :remote api-exposes ktor-client-core + content-negotiation + logging +
            // ktor-serialization-kotlinx-json + sandwich-ktor for downstream use.
            api(project(":remote"))

            // Ktor Auth (bearer) for the ITAD OAuth user-token client (epic #219, Phase 2).
            implementation(libs.ktor.client.auth)

            implementation(libs.koin.core)

            implementation(project(":logging"))
            implementation(project(":common"))
            implementation(project(":domain"))
        }

        androidMain.dependencies {
            // androidContext() for the OAuth browser launcher binding (epic #219, Phase 2.2).
            implementation(libs.koin.android)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing"))
            implementation(libs.coroutines.testing)
        }
    }
}
